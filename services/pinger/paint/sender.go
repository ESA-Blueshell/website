// Package paint sends the logo to the canvas, one echo request per pixel, inside the rate cap.
package paint

import (
	"context"
	"math/rand/v2"
	"net"
	"os"
	"runtime"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv6"
	"golang.org/x/time/rate"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
)

// Conn is the raw ICMPv6 socket; *icmp.PacketConn satisfies it.
type Conn interface {
	WriteTo(b []byte, dst net.Addr) (int, error)
}

// Workers is how many goroutines send at once, and so how many sockets a caller opens: one socket
// carries one send at a time (Go's write lock and the kernel's socket lock both serialise it), so
// workers sharing a socket queue behind each other instead of adding rate.
func Workers() int { return max(2, runtime.NumCPU()) }

// MaxRatePPS caps the rate however it is set: the event bans prefixes that ping excessively hard.
const MaxRatePPS = 200_000

// Settings are the prefix and rate the api hands the sender. The pixels travel separately, through
// SetPixels, because they are large and change only when the image or its box changes.
type Settings struct {
	Prefix  canvas.Prefix
	RatePPS int
	// Enabled is the SiteCie toggle: false idles the sender even with a prefix and image set.
	Enabled bool
}

type State string

const (
	Idle    State = "idle"
	Closed  State = "closed"
	Running State = "running"
)

// target is the image as pixels, versioned so the Run loop notices a swap and rebuilds its
// destination addresses. The generation only ever grows.
type target struct {
	pixels []canvas.Pixel
	gen    uint64
}

// Stats is a point-in-time copy of what the sender has done.
type Stats struct {
	State       State
	Sent        uint64
	Errors      uint64
	Passes      uint64
	PassDone    int
	PassTotal   int
	ActualPPS   uint64
	LastError   string
	LastErrorAt time.Time
	// Rates is the packets-a-second sample for each of the last 40 seconds, oldest first.
	Rates []uint64
	// Failing is set when the last failThreshold sends all errored, so the path looks broken.
	// The sender keeps retrying with backoff either way.
	Failing bool
}

const (
	batch      = 64
	rateWindow = 40
	// failThreshold is how many sends in a row must fail before the sender reports itself as
	// failing. A handful of full-buffer errors among successes is normal and the backoff soaks
	// them up; only a run this long, with not one send getting out, means the path is broken.
	failThreshold = 100
)

// Sender repaints the logo pass after pass, each pass in a fresh shuffled order so the logo
// fills in evenly instead of as a scanline others can race.
type Sender struct {
	conns    []Conn
	target   atomic.Pointer[target]
	window   Window
	settings func() Settings
	now      func() time.Time
	workers  int
	echo     []byte
	limiter  *rate.Limiter

	state     atomic.Value
	sent      atomic.Uint64
	errors    atomic.Uint64
	passes    atomic.Uint64
	passDone  atomic.Int64
	actualPPS atomic.Uint64
	errMu     sync.Mutex
	lastErr   string
	lastErrAt time.Time
	consec    atomic.Int64
	rateMu    sync.Mutex
	rates     []uint64
	datagram  bool
}

// echoRequest builds the ICMPv6 echo request the sender emits. The ID is this process, but it does
// not change the length, so EchoRequestSize reads off the same construction.
func echoRequest() []byte {
	b, err := (&icmp.Message{
		Type: ipv6.ICMPTypeEchoRequest,
		// No payload: the address alone paints the pixel, so every byte past the echo header is
		// uplink spent for nothing.
		Body: &icmp.Echo{ID: os.Getpid() & 0xffff, Seq: 1},
	}).Marshal(nil)
	if err != nil {
		panic(err)
	}
	return b
}

// EchoRequestSize is the on-wire byte length of one echo request the sender emits: the ICMPv6
// header alone, since it carries no payload. A bandwidth estimate adds the IPv6 header to this so
// it tracks the real packet rather than a hand-copied constant.
func EchoRequestSize() int { return len(echoRequest()) }

// NewSender sends through conns, worker i on conns[i % len(conns)]; pass Workers() sockets so no
// two workers share one.
func NewSender(conns []Conn, pixels []canvas.Pixel, window Window, settings func() Settings) *Sender {
	s := &Sender{
		conns:    conns,
		window:   window,
		settings: settings,
		now:      time.Now,
		workers:  Workers(),
		echo:     echoRequest(),
		limiter:  rate.NewLimiter(1, batch),
	}
	s.SetPixels(pixels)
	s.state.Store(Idle)
	return s
}

// SetPixels swaps the image the sender paints. The next pass picks up the new pixels: the Run
// loop sees the generation change and rebuilds its destination addresses. Safe to call while the
// sender runs.
func (s *Sender) SetPixels(pixels []canvas.Pixel) {
	gen := uint64(1)
	if prev := s.target.Load(); prev != nil {
		gen = prev.gen + 1
	}
	s.target.Store(&target{pixels: pixels, gen: gen})
}

// Seed restores the running totals from the last saved state, so a restart continues the counts
// rather than starting from zero. Call it before Run.
func (s *Sender) Seed(sent, passes, errors uint64) {
	s.sent.Store(sent)
	s.passes.Store(passes)
	s.errors.Store(errors)
}

// UseDatagramAddresses makes the sender address an unprivileged ICMP datagram socket (udp6)
// rather than a raw socket. Call it before Run when the socket was opened that way.
func (s *Sender) UseDatagramAddresses() { s.datagram = true }

func (s *Sender) Snapshot() Stats {
	s.errMu.Lock()
	lastErr, lastErrAt := s.lastErr, s.lastErrAt
	s.errMu.Unlock()
	s.rateMu.Lock()
	rates := append([]uint64(nil), s.rates...)
	s.rateMu.Unlock()
	pixels := s.target.Load().pixels
	return Stats{
		State:       s.state.Load().(State),
		Sent:        s.sent.Load(),
		Errors:      s.errors.Load(),
		Passes:      s.passes.Load(),
		PassDone:    int(min(s.passDone.Load(), int64(len(pixels)))),
		PassTotal:   len(pixels),
		ActualPPS:   s.actualPPS.Load(),
		LastError:   lastErr,
		LastErrorAt: lastErrAt,
		Rates:       rates,
		Failing:     s.consec.Load() >= failThreshold,
	}
}

// Run sends until ctx ends, idling whenever there is no prefix, no image or the event is closed.
func (s *Sender) Run(ctx context.Context) {
	go s.sampleRate(ctx)
	var dests []net.Addr
	var destsFor canvas.Prefix
	var destsGen uint64
	for ctx.Err() == nil {
		cfg := s.settings()
		t := s.target.Load()
		if state := s.decide(cfg); state != Running {
			s.state.Store(state)
			select {
			case <-ctx.Done():
			case <-time.After(100 * time.Millisecond):
			}
			continue
		}
		s.state.Store(Running)
		if dests == nil || destsFor != cfg.Prefix || destsGen != t.gen {
			dests = destinations(cfg.Prefix, t.pixels, s.datagram)
			destsFor, destsGen = cfg.Prefix, t.gen
		}
		if s.pass(ctx, cfg.Prefix, dests, t.gen) {
			s.passes.Add(1)
		}
	}
}

func (s *Sender) decide(cfg Settings) State {
	switch {
	case !cfg.Enabled:
		return Idle
	case cfg.Prefix.IsZero() || len(s.target.Load().pixels) == 0:
		return Idle
	case cfg.RatePPS <= 0:
		return Idle
	case !s.window.Contains(s.now()):
		return Closed
	}
	return Running
}

func destinations(p canvas.Prefix, pixels []canvas.Pixel, datagram bool) []net.Addr {
	out := make([]net.Addr, len(pixels))
	for i, px := range pixels {
		ip := p.Address(px).AsSlice()
		if datagram {
			out[i] = &net.UDPAddr{IP: ip}
		} else {
			out[i] = &net.IPAddr{IP: ip}
		}
	}
	return out
}

// pass reports whether it reached every pixel; it stops early when the settings change under it.
func (s *Sender) pass(ctx context.Context, p canvas.Prefix, dests []net.Addr, gen uint64) bool {
	order := rand.Perm(len(dests))
	var next atomic.Int64
	var aborted atomic.Bool
	s.passDone.Store(0)

	var wg sync.WaitGroup
	for w := range s.workers {
		conn := s.conns[w%len(s.conns)]
		wg.Go(func() {
			var backoff Backoff
			bc := batcher(conn)
			msgs := make([]ipv6.Message, batch)
			for i := range msgs {
				msgs[i].Buffers = [][]byte{s.echo}
			}
			for {
				from := int(next.Add(batch)) - batch
				if from >= len(order) || ctx.Err() != nil || aborted.Load() {
					return
				}
				cfg := s.settings()
				// Abort when the prefix changes, the sender leaves Running, or the image is
				// swapped: dests then belong to a stale target and the next pass rebuilds them.
				if cfg.Prefix != p || s.decide(cfg) != Running || s.target.Load().gen != gen {
					aborted.Store(true)
					return
				}
				if s.limiter.Limit() != rate.Limit(cfg.RatePPS) {
					s.limiter.SetLimit(rate.Limit(cfg.RatePPS))
				}
				to := min(from+batch, len(order))
				if s.limiter.WaitN(ctx, to-from) != nil {
					return
				}
				var sent, trailing int
				if bc != nil {
					for k, i := range order[from:to] {
						msgs[k].Addr = dests[i]
					}
					sent, trailing = s.writeBatch(bc, msgs[:to-from])
				} else {
					sent, trailing = s.writeEach(conn, dests, order[from:to])
				}
				// The shared counters move once per batch: per packet, every worker contends on
				// the same cache lines at millions of sends a second.
				s.settle(&backoff, sent, trailing)
				// One backoff per batch that lost a send, not one per lost packet: a broken path
				// must not crawl at a packet a second. The next pass retries the pixels it missed.
				if sent < to-from {
					select {
					case <-ctx.Done():
						return
					case <-time.After(backoff.Next()):
					}
				}
			}
		})
	}
	wg.Wait()
	return !aborted.Load() && ctx.Err() == nil
}

// writeEach sends one packet per syscall. It reports how many got out, and how many failed after
// the last one that did.
func (s *Sender) writeEach(conn Conn, dests []net.Addr, idx []int) (sent, trailing int) {
	for _, i := range idx {
		if _, err := conn.WriteTo(s.echo, dests[i]); err != nil {
			s.recordError(err)
			trailing++
			continue
		}
		sent++
		trailing = 0
	}
	return sent, trailing
}

// writeBatch sends msgs in as few syscalls as the socket allows. A batch write stops at the first
// message that fails, so that one is counted and skipped and the rest go again.
func (s *Sender) writeBatch(bc batchConn, msgs []ipv6.Message) (sent, trailing int) {
	for len(msgs) > 0 {
		n, err := bc.WriteBatch(msgs, 0)
		if n > 0 {
			sent += n
			trailing = 0
			msgs = msgs[n:]
		}
		if err != nil {
			s.recordError(err)
			trailing++
			msgs = msgs[min(1, len(msgs)):]
		} else if n == 0 {
			break
		}
	}
	return sent, trailing
}

// settle books a batch: sent made it out, and trailing failed after the last one that did.
func (s *Sender) settle(backoff *Backoff, sent, trailing int) {
	if sent > 0 {
		backoff.Reset()
		s.consec.Store(int64(trailing))
		s.sent.Add(uint64(sent))
		s.passDone.Add(int64(sent))
	} else {
		s.consec.Add(int64(trailing))
	}
}

func (s *Sender) recordError(err error) {
	s.errors.Add(1)
	s.errMu.Lock()
	s.lastErr, s.lastErrAt = err.Error(), s.now()
	s.errMu.Unlock()
}

func (s *Sender) sampleRate(ctx context.Context) {
	tick := time.NewTicker(time.Second)
	defer tick.Stop()
	last := s.sent.Load()
	for {
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
			now := s.sent.Load()
			rate := now - last
			s.actualPPS.Store(rate)
			last = now
			s.rateMu.Lock()
			s.rates = append(s.rates, rate)
			if len(s.rates) > rateWindow {
				s.rates = s.rates[len(s.rates)-rateWindow:]
			}
			s.rateMu.Unlock()
		}
	}
}
