package runner

import (
	"context"
	"errors"
	"log/slog"
	"syscall"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

// stallAfter is how long the sender may run with nothing getting out before the watch calls the
// path broken. A working path sends within a second at the lowest rate.
const stallAfter = 5 * time.Second

// The send paths, as the status line names them.
const (
	pathRaw  = "raw ICMPv6 sockets"
	pathEcho = "the ICMP helper API"
)

// opened is a set of send sockets and what the runner needs to know about them.
type opened struct {
	socks    []socket
	datagram bool
	// notice is what the member should act on, or empty.
	notice string
	path   string
	// track wraps the sockets so the watch sees each send error as it happens.
	track bool
	// fallback opens a second path for when this one gets nothing out, or is nil.
	fallback func() (opened, error)
}

// stallWatch tells a sender that runs but gets nothing out from one that is fine: sends that fail
// move only the error count, and sends that block move nothing at all. A pass whose pixels all sit
// off the canvas sends nothing either, but its progress still moves.
type stallWatch struct {
	after  time.Duration
	since  time.Time
	sent   uint64
	errs   uint64
	passes uint64
	done   int
}

// observe takes one snapshot and reports whether the sender has gone after without a send.
func (w *stallWatch) observe(now time.Time, st paint.Stats) bool {
	if st.State != paint.Running {
		w.reset()
		return false
	}
	moved := st.Passes != w.passes || st.PassDone != w.done
	healthy := st.Sent != w.sent || (st.Errors == w.errs && moved)
	first := w.since.IsZero()
	w.sent, w.errs, w.passes, w.done = st.Sent, st.Errors, st.Passes, st.PassDone
	if first || healthy {
		w.since = now
		return false
	}
	return now.Sub(w.since) >= w.after
}

func (w *stallWatch) reset() { w.since = time.Time{} }

// sendWatch follows the sender once a second. It puts why nothing goes out in the status line, and
// moves the sockets to the fallback path when the first path has sent nothing at all.
type sendWatch struct {
	r        *Runner
	snapshot func() paint.Stats
	settings func() paint.Settings
	readErr  func() string
	socks    []*swapSocket
	path     string
	fallback func() (opened, error)
	stall    stallWatch
	// v6 is the route check, or nil to skip it.
	v6      *ipv6Watch
	stalled bool
	err     error
	// openedSent and openedAt are the sent count and time when the current path took over.
	openedSent uint64
	openedAt   time.Time
}

func (w *sendWatch) run(ctx context.Context) {
	tick := time.NewTicker(time.Second)
	defer tick.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case now := <-tick.C:
			w.step(now)
		}
	}
}

func (w *sendWatch) step(now time.Time) {
	snap := w.snapshot()
	var fresh error
	for _, s := range w.socks {
		if e := s.takeErr(); e != nil {
			fresh = e
		}
	}
	switch snap.State {
	case paint.Running:
	case paint.Idle:
		w.stall.reset()
		w.stalled = false
		if w.v6.missing(now, false) {
			w.r.setProblem(noIPv6Message)
		} else {
			w.r.setProblem(idleMessage(w.settings(), snap.PassTotal, w.readErr()))
		}
		return
	default:
		w.stall.reset()
		w.stalled = false
		if w.v6.missing(now, false) {
			w.r.setProblem(noIPv6Message)
		} else {
			w.r.setProblem("")
		}
		return
	}
	landed := snap.Sent != w.stall.sent
	stalled := w.stall.observe(now, snap)
	missing := w.v6.missing(now, stalled && !w.stalled)
	w.stalled = stalled
	switch {
	case missing && !landed:
		// No fallback: the slower path would cost rate once the member finds IPv6.
		w.r.setProblem(noIPv6Message)
		return
	case !stalled:
		w.err = nil
		w.r.setProblem("")
		return
	}
	if fresh != nil {
		w.err = fresh
	}
	err := w.err
	if err == nil && snap.LastError != "" && snap.LastErrorAt.After(w.openedAt) {
		err = errors.New(snap.LastError)
	}
	if w.fallback != nil && snap.Sent == w.openedSent && w.fallBack(now, snap.Sent, err) {
		return
	}
	w.r.setProblem(stallMessage(w.path, err))
}

// fallBack opens the fallback path and swaps every socket over to it. It is tried once.
func (w *sendWatch) fallBack(now time.Time, sent uint64, cause error) bool {
	open := w.fallback
	w.fallback = nil
	next, err := open()
	if err != nil {
		slog.Warn("open fallback send path", "path", next.path, "err", err)
		return false
	}
	if len(next.socks) != len(w.socks) {
		paint.CloseSockets(next.socks)
		return false
	}
	for i, s := range w.socks {
		old := s.swap(next.socks[i])
		// A raw socket's Close interrupts a send still blocked on it; the helper API waits for it.
		go func() { _ = old.Close() }()
	}
	slog.Warn("send path got nothing out", "path", w.path, "fallback", next.path, "err", cause)
	w.r.setMessage("Nothing got out over " + w.path + ": " + describe(cause) + ". The app now sends over " + next.path + ".")
	w.r.setProblem("")
	w.path, w.err, w.openedSent, w.openedAt = next.path, nil, sent, now
	w.stall.reset()
	return true
}

// stallMessage is the status line for a path that gets nothing out.
func stallMessage(path string, err error) string {
	if noRoute(err) {
		return noIPv6Message + " (" + err.Error() + ")"
	}
	msg := "No pings are getting out"
	if path != "" {
		msg += " over " + path
	}
	msg += ": " + describe(err) + "."
	if path == pathEcho {
		msg += " Running the app as administrator sends over raw sockets instead."
	}
	return msg
}

func describe(err error) string {
	switch h := sendHint(err); {
	case err == nil:
		return "sends are not completing"
	case h != "":
		return h + " (" + err.Error() + ")"
	default:
		return err.Error()
	}
}

// Windows error codes the senders see. Every one is above the highest Unix errno, so none is
// mistaken for a different error on another OS.
const (
	wsaEAcces             syscall.Errno = 10013
	wsaEAddrNotAvail      syscall.Errno = 10049
	wsaENetUnreach        syscall.Errno = 10051
	wsaENoBufs            syscall.Errno = 10055
	wsaETimedOut          syscall.Errno = 10060
	wsaEHostUnreach       syscall.Errno = 10065
	errNetworkUnreachable syscall.Errno = 1231
	errHostUnreachable    syscall.Errno = 1232
	errTimeout            syscall.Errno = 1460
	// The ICMP helper API's IP_STATUS codes, which Windows has no message text for.
	ipDestNoRoute         syscall.Errno = 11002
	ipDestAddrUnreachable syscall.Errno = 11003
	ipDestProhibited      syscall.Errno = 11004
	ipNoResources         syscall.Errno = 11006
	ipReqTimedOut         syscall.Errno = 11010
	ipBadRoute            syscall.Errno = 11012
)

// sendHint names what a send error means for the member, or empty when it is none of the known
// kinds. It goes by error code, since Windows words its messages in the system's language.
func sendHint(err error) string {
	if err == nil {
		return ""
	}
	if errors.Is(err, paint.ErrNoEchoSlot) {
		return "Windows is not finishing the ICMP requests it took"
	}
	if noRoute(err) {
		return "this network has no IPv6"
	}
	var code syscall.Errno
	if !errors.As(err, &code) {
		return ""
	}
	switch code {
	case wsaEAcces, ipDestProhibited, syscall.EACCES, syscall.EPERM:
		return "Windows or security software blocked the send"
	case wsaETimedOut, errTimeout, ipReqTimedOut, syscall.ETIMEDOUT:
		return "sends time out"
	case wsaENoBufs, ipNoResources, syscall.ENOBUFS:
		return "the network stack has no buffer space left"
	}
	return ""
}

// noRoute reports whether err is one of the codes for a machine with no IPv6 route out.
func noRoute(err error) bool {
	var code syscall.Errno
	if !errors.As(err, &code) {
		return false
	}
	switch code {
	case wsaENetUnreach, wsaEHostUnreach, wsaEAddrNotAvail, errNetworkUnreachable, errHostUnreachable,
		ipDestNoRoute, ipDestAddrUnreachable, ipBadRoute, syscall.ENETUNREACH, syscall.EHOSTUNREACH,
		syscall.EADDRNOTAVAIL:
		return true
	}
	return false
}

// idleMessage names what an idle sender waits for, or empty when it waits on nothing the member
// can see.
func idleMessage(cur paint.Settings, pixels int, readErr string) string {
	switch {
	case readErr != "":
		return "Cannot read the paint job from the server: " + readErr
	case cur.Prefix.IsZero():
		return "Waiting for the paint job from the server."
	case !cur.Enabled:
		return "Painting is paused."
	case pixels == 0:
		return "Waiting for the image to paint."
	}
	return ""
}
