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

// MaxRatePPS caps the rate however it is set: the event bans prefixes that ping excessively hard.
const MaxRatePPS = 200_000

// Settings are the prefix and rate the api hands the sender. The pixels travel separately, through
// SetPixels, because they are large and change only when the image or its box changes.
type Settings struct {
	Prefix  canvas.Prefix
	RatePPS int
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
	conn     Conn
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

func NewSender(conn Conn, pixels []canvas.Pixel, window Window, settings func() Settings) *Sender {
	echo, err := (&icmp.Message{
		Type: ipv6.ICMPTypeEchoRequest,
		Body: &icmp.Echo{ID: os.Getpid() & 0xffff, Seq: 1, Data: []byte("blueshell")},
	}).Marshal(nil)
	if err != nil {
		panic(err)
	}
	s := &Sender{
		conn:     conn,
		window:   window,
		settings: settings,
		now:      time.Now,
		workers:  max(2, runtime.NumCPU()),
		echo:     echo,
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
	for range s.workers {
		wg.Go(func() {
			var backoff Backoff
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
				failed := false
				for _, i := range order[from:to] {
					if _, err := s.conn.WriteTo(s.echo, dests[i]); err != nil {
						s.recordError(err)
						failed = true
						continue
					}
					backoff.Reset()
					s.consec.Store(0)
					s.sent.Add(1)
					s.passDone.Add(1)
				}
				// One backoff per batch that lost a send, not one per lost packet: a broken path
				// must not crawl at a packet a second. The next pass retries the pixels it missed.
				if failed {
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

func (s *Sender) recordError(err error) {
	s.errors.Add(1)
	s.consec.Add(1)
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
