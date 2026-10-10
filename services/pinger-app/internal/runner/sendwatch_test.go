package runner

import (
	"errors"
	"net"
	"os"
	"strings"
	"sync/atomic"
	"syscall"
	"testing"
	"time"

	"golang.org/x/net/ipv6"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/paint"

	"github.com/ESA-Blueshell/website/services/pinger-app/internal/headroom"
)

type fakeSock struct {
	err    error
	sent   atomic.Int64
	closed atomic.Bool
}

func (f *fakeSock) WriteTo(b []byte, _ net.Addr) (int, error) {
	if f.err != nil {
		return 0, f.err
	}
	f.sent.Add(1)
	return len(b), nil
}

func (f *fakeSock) Close() error { f.closed.Store(true); return nil }

var dst = &net.IPAddr{IP: net.ParseIP("2001:db8::1")}

// The errors Windows hands back, wrapped the way the paint senders wrap them.
func winErr(call string, code syscall.Errno) error { return os.NewSyscallError(call, code) }

func running(sent, errs uint64) paint.Stats {
	return paint.Stats{State: paint.Running, Sent: sent, Errors: errs}
}

func TestStallWatchFlagsARunningSenderThatSendsNothing(t *testing.T) {
	w := stallWatch{after: 5 * time.Second}
	t0 := time.Unix(0, 0)
	for s := range 5 {
		if w.observe(t0.Add(time.Duration(s)*time.Second), running(0, uint64(s*100))) {
			t.Fatalf("stalled after %ds", s)
		}
	}
	if !w.observe(t0.Add(5*time.Second), running(0, 500)) {
		t.Fatal("not stalled after 5s of failed sends")
	}
}

// A send that blocks moves no counter at all, and is as broken as one that fails.
func TestStallWatchFlagsASenderWhoseCountersStandStill(t *testing.T) {
	w := stallWatch{after: 5 * time.Second}
	t0 := time.Unix(0, 0)
	w.observe(t0, running(0, 0))
	if !w.observe(t0.Add(6*time.Second), running(0, 0)) {
		t.Fatal("not stalled with every counter standing still")
	}
}

func TestStallWatchClearsOnceASendLands(t *testing.T) {
	w := stallWatch{after: 5 * time.Second}
	t0 := time.Unix(0, 0)
	w.observe(t0, running(0, 0))
	w.observe(t0.Add(6*time.Second), running(0, 10))
	if w.observe(t0.Add(7*time.Second), running(1, 10)) {
		t.Fatal("still stalled after a send landed")
	}
}

// Pixels moved off the canvas send nothing and fail nothing, but the pass still moves on.
func TestStallWatchLeavesAPassWithNothingOnTheCanvasAlone(t *testing.T) {
	w := stallWatch{after: 5 * time.Second}
	t0 := time.Unix(0, 0)
	for s := range 10 {
		st := running(7, 0)
		st.PassDone = s * 10
		if w.observe(t0.Add(time.Duration(s)*time.Second), st) {
			t.Fatalf("stalled at %ds while the pass moves on", s)
		}
	}
}

func TestStallWatchStartsOverWhenTheSenderIdles(t *testing.T) {
	w := stallWatch{after: 5 * time.Second}
	t0 := time.Unix(0, 0)
	w.observe(t0, running(0, 0))
	w.observe(t0.Add(4*time.Second), paint.Stats{State: paint.Idle})
	if w.observe(t0.Add(6*time.Second), running(0, 0)) {
		t.Fatal("stalled counting time spent idle")
	}
}

func TestStallMessageNamesTheCause(t *testing.T) {
	cases := []struct {
		name string
		err  error
		want string
	}{
		{"raw socket without a route", winErr("wsasendto", 10051), "has no IPv6"},
		{"raw host unreachable", winErr("wsasendto", 10065), "has no IPv6"},
		{"helper API without a route", winErr("Icmp6SendEcho2", 11002), "has no IPv6"},
		{"helper API network unreachable", winErr("Icmp6SendEcho2", 1231), "has no IPv6"},
		{"blocked by Windows", winErr("wsasendto", 10013), "blocked"},
		{"timed out", winErr("wsasendto", 10060), "time out"},
		{"stuck helper API", paint.ErrNoEchoSlot, "not finishing"},
	}
	for _, c := range cases {
		got := stallMessage(pathRaw, c.err)
		if !strings.Contains(got, c.want) {
			t.Errorf("%s: %q does not say %q", c.name, got, c.want)
		}
		if !strings.Contains(got, c.err.Error()) {
			t.Errorf("%s: %q leaves out the error %q", c.name, got, c.err)
		}
	}
}

func TestStallMessageWithoutAnErrorSaysTheSendsHang(t *testing.T) {
	if got := stallMessage(pathRaw, nil); !strings.Contains(got, "not completing") {
		t.Fatalf("message %q", got)
	}
}

// A member without admin rights has one path; the other needs the app run as administrator.
func TestStallMessageOnTheHelperAPISuggestsAdministrator(t *testing.T) {
	if got := stallMessage(pathEcho, winErr("Icmp6SendEcho2", 11050)); !strings.Contains(got, "administrator") {
		t.Fatalf("message %q", got)
	}
	if got := stallMessage(pathRaw, winErr("wsasendto", 10051)); strings.Contains(got, "administrator") {
		t.Fatalf("raw path message %q suggests administrator", got)
	}
}

func TestIdleMessageNamesWhatTheSenderWaitsFor(t *testing.T) {
	prefix, err := canvas.ParsePrefix("2001:db8::/64")
	if err != nil {
		t.Fatal(err)
	}
	cases := []struct {
		name    string
		cur     paint.Settings
		pixels  int
		readErr string
		want    string
	}{
		{"api unreachable", paint.Settings{}, 0, "dial tcp: i/o timeout", "dial tcp: i/o timeout"},
		{"no paint job", paint.Settings{}, 0, "", "Waiting for the paint job"},
		{"paused", paint.Settings{Prefix: prefix}, 10, "", "paused"},
		{"no image", paint.Settings{Prefix: prefix, Enabled: true}, 0, "", "Waiting for the image"},
	}
	for _, c := range cases {
		if got := idleMessage(c.cur, c.pixels, c.readErr); !strings.Contains(got, c.want) {
			t.Errorf("%s: %q does not say %q", c.name, got, c.want)
		}
	}
}

func TestSwapSocketSendsOnWhicheverSocketItHolds(t *testing.T) {
	first, second := &fakeSock{}, &fakeSock{}
	s := newSwapSocket(first)
	if _, err := s.WriteTo([]byte{1}, dst); err != nil {
		t.Fatal(err)
	}
	if old := s.swap(second); old != first {
		t.Fatalf("swap returned %v, want the first socket", old)
	}
	if _, err := s.WriteTo([]byte{1}, dst); err != nil {
		t.Fatal(err)
	}
	if first.sent.Load() != 1 || second.sent.Load() != 1 {
		t.Fatalf("sent %d then %d, want one each", first.sent.Load(), second.sent.Load())
	}
}

func TestSwapSocketBatchStopsAtTheFirstFailure(t *testing.T) {
	boom := winErr("wsasendto", 10051)
	s := newSwapSocket(&fakeSock{err: boom})
	msgs := []ipv6.Message{{Buffers: [][]byte{{1}}, Addr: dst}, {Buffers: [][]byte{{1}}, Addr: dst}}
	if n, err := s.WriteBatch(msgs, 0); n != 0 || !errors.Is(err, boom) {
		t.Fatalf("WriteBatch = %d, %v", n, err)
	}
	if err := s.takeErr(); !errors.Is(err, boom) {
		t.Fatalf("takeErr() = %v, want the send error", err)
	}
	if err := s.takeErr(); err != nil {
		t.Fatalf("takeErr() = %v twice", err)
	}
}

// watchFor builds a watch over a scripted snapshot, on swap sockets around firsts.
func watchFor(r *Runner, snap *paint.Stats, firsts []*fakeSock, fallback func() (opened, error)) *sendWatch {
	socks := make([]*swapSocket, len(firsts))
	for i, f := range firsts {
		socks[i] = newSwapSocket(f)
	}
	return &sendWatch{
		r:        r,
		snapshot: func() paint.Stats { return *snap },
		settings: func() paint.Settings { return paint.Settings{} },
		readErr:  func() string { return "" },
		socks:    socks,
		path:     pathRaw,
		fallback: fallback,
		stall:    stallWatch{after: stallAfter},
	}
}

func TestWatchFallsBackWhenTheFirstPathSendsNothing(t *testing.T) {
	r := &Runner{headroom: headroom.New()}
	firsts := []*fakeSock{{err: winErr("wsasendto", 10013)}, {err: winErr("wsasendto", 10013)}}
	seconds := []*fakeSock{{}, {}}
	opens := 0
	w := watchFor(r, &paint.Stats{}, firsts, func() (opened, error) {
		opens++
		return opened{socks: []socket{seconds[0], seconds[1]}, path: pathEcho}, nil
	})
	snap := running(0, 0)
	w.snapshot = func() paint.Stats { return snap }

	t0 := time.Unix(0, 0)
	for s := 0; s <= int(stallAfter/time.Second); s++ {
		for _, sk := range w.socks {
			_, _ = sk.WriteTo([]byte{1}, dst)
		}
		snap.Errors += 2
		w.step(t0.Add(time.Duration(s) * time.Second))
	}

	if opens != 1 {
		t.Fatalf("fallback opened %d times, want once", opens)
	}
	for i, sk := range w.socks {
		if _, err := sk.WriteTo([]byte{1}, dst); err != nil {
			t.Fatalf("socket %d still fails after the fallback: %v", i, err)
		}
		if seconds[i].sent.Load() != 1 {
			t.Fatalf("socket %d did not send on the fallback", i)
		}
	}
	waitClosed(t, firsts)
	msg := r.Status().Message
	for _, want := range []string{pathEcho, "blocked"} {
		if !strings.Contains(msg, want) {
			t.Fatalf("status %q does not say %q", msg, want)
		}
	}
}

func waitClosed(t *testing.T, socks []*fakeSock) {
	t.Helper()
	deadline := time.Now().Add(time.Second)
	for _, f := range socks {
		for !f.closed.Load() {
			if time.Now().After(deadline) {
				t.Fatal("the old socket was never closed")
			}
			time.Sleep(time.Millisecond)
		}
	}
}

// A path that has sent and then stalls is a network that dropped out, not a path Windows blocks;
// moving it to the slower helper API would cost the member rate for the rest of the event.
func TestWatchKeepsAPathThatHasSent(t *testing.T) {
	r := &Runner{headroom: headroom.New()}
	opens := 0
	w := watchFor(r, nil, []*fakeSock{{err: winErr("wsasendto", 10051)}}, func() (opened, error) {
		opens++
		return opened{}, nil
	})
	snap := running(500, 0)
	w.snapshot = func() paint.Stats { return snap }

	t0 := time.Unix(0, 0)
	w.step(t0)
	snap.Sent = 1000
	for s := 1; s <= 10; s++ {
		_, _ = w.socks[0].WriteTo([]byte{1}, dst)
		snap.Errors += 5
		w.step(t0.Add(time.Duration(s) * time.Second))
	}
	if opens != 0 {
		t.Fatalf("fell back %d times on a path that had sent", opens)
	}
	if got := r.Status().Message; !strings.Contains(got, "has no IPv6") {
		t.Fatalf("status %q", got)
	}
}

func TestWatchClearsTheProblemOnceSendsLand(t *testing.T) {
	r := &Runner{headroom: headroom.New()}
	w := watchFor(r, nil, []*fakeSock{{}}, nil)
	snap := running(0, 0)
	w.snapshot = func() paint.Stats { return snap }
	t0 := time.Unix(0, 0)
	w.step(t0)
	w.step(t0.Add(stallAfter))
	if r.Status().Message == "" {
		t.Fatal("no status while nothing is sent")
	}
	snap.Sent = 10
	w.step(t0.Add(stallAfter + time.Second))
	if got := r.Status().Message; got != "" {
		t.Fatalf("status %q after sends landed", got)
	}
}

// A path the snapshot blamed before the switch must not be blamed on the one after it.
func TestWatchIgnoresAnErrorFromBeforeTheSwitch(t *testing.T) {
	r := &Runner{headroom: headroom.New()}
	w := watchFor(r, nil, []*fakeSock{{err: winErr("wsasendto", 10013)}}, func() (opened, error) {
		return opened{socks: []socket{&fakeSock{}}, path: pathEcho}, nil
	})
	t0 := time.Unix(0, 0)
	snap := running(0, 0)
	snap.LastError, snap.LastErrorAt = "wsasendto: forbidden", t0
	w.snapshot = func() paint.Stats { return snap }
	w.step(t0)
	_, _ = w.socks[0].WriteTo([]byte{1}, dst)
	snap.Errors = 1
	w.step(t0.Add(stallAfter))
	w.step(t0.Add(stallAfter + time.Second))
	w.step(t0.Add(2*stallAfter + time.Second))
	got := r.Status().Message
	if strings.Contains(got, "No pings") && strings.Contains(got, "forbidden") {
		t.Fatalf("status %q blames the old path's error", got)
	}
	if !strings.Contains(got, "not completing") {
		t.Fatalf("status %q", got)
	}
}
