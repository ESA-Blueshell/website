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

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
)

// Conn is the raw ICMPv6 socket; *icmp.PacketConn satisfies it.
type Conn interface {
	WriteTo(b []byte, dst net.Addr) (int, error)
}

// Workers is the most goroutines that send at once, and so how many sockets a caller opens: one
// socket carries one send at a time, so workers sharing a socket queue behind each other. The
// scaler runs fewer while fewer carry the rate. macOS gets two: past that its sendto spins on a
// kernel lock, so a third worker lowers the rate and burns another core.
func Workers() int {
	if runtime.GOOS == "darwin" {
		return 2
	}
	return max(2, runtime.NumCPU())
}

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

// target is the image as pixels, versioned so the Run loop notices a swap and reshuffles. The
// generation only ever grows.
type target struct {
	pixels []canvas.Pixel
	gen    uint64
}

// Stats is a point-in-time copy of what the sender has done.
type Stats struct {
	State     State
	Sent      uint64
	Errors    uint64
	Passes    uint64
	PassDone  int
	PassTotal int
	ActualPPS uint64
	// LastError is the error that failed a whole chunk; a send lost among successes is only counted.
	LastError   string
	LastErrorAt time.Time
	// Rates is the packets-a-second sample for each of the last 40 seconds, oldest first.
	Rates []uint64
	// Failing is set while a worker's last chunk got nothing out, so the path looks broken. The
	// sender keeps retrying after failPause either way.
	Failing bool
}

const (
	batch      = 64
	rateWindow = 40
	// chunkSpan is how much of a worker's share of the rate it sends between two sleeps, capped
	// at chunkMax packets: a sleep and wake per few packets costs more CPU than the sends.
	chunkSpan = 20 * time.Millisecond
	chunkMax  = 1024
	// paceSlack is how far a worker may fall behind its schedule and still catch up, so a sleep
	// that overshoots does not cost rate. It also bounds the burst after an idle spell.
	paceSlack = 2 * time.Millisecond
	// failPause is how long a worker rests after a chunk with not one send out, so a broken socket
	// does not spin. A lost ping costs nothing worth more bookkeeping than a count.
	failPause = 20 * time.Millisecond
)

// Sender repaints the logo pass after pass, each pass in a fresh shuffled order so the logo
// fills in evenly instead of as a scanline others can race.
type Sender struct {
	conns    []Conn
	target   atomic.Pointer[target]
	offsets  atomic.Pointer[offsets]
	window   Window
	settings func() Settings
	now      func() time.Time
	workers  int
	echo     []byte
	pacers   []pacer

	state     atomic.Value
	sent      atomic.Uint64
	errors    atomic.Uint64
	passes    atomic.Uint64
	passDone  atomic.Int64
	actualPPS atomic.Uint64
	errMu     sync.Mutex
	lastErr   string
	lastErrAt time.Time
	failing   atomic.Bool
	active    atomic.Int64
	busy      atomic.Int64
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
	}
	s.pacers = make([]pacer, s.workers)
	s.active.Store(1)
	s.SetPixels(pixels)
	s.state.Store(Idle)
	return s
}

// SetPixels swaps the image the sender paints; the pass under way stops and the next one paints
// the new pixels. Safe to call while the sender runs.
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
		Failing:     s.failing.Load(),
	}
}

// Run sends until ctx ends, idling whenever there is no prefix, no image or the event is closed.
func (s *Sender) Run(ctx context.Context) {
	go s.sampleRate(ctx)
	var order []uint32
	var orderGen uint64
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
		if order == nil || orderGen != t.gen {
			order = make([]uint32, len(t.pixels))
			for i := range order {
				order[i] = uint32(i)
			}
			orderGen = t.gen
		}
		rand.Shuffle(len(order), func(i, j int) { order[i], order[j] = order[j], order[i] })
		if s.pass(ctx, cfg.Prefix, t, order) {
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

// pass reports whether it reached every pixel; it stops early when the settings change under it.
func (s *Sender) pass(ctx context.Context, p canvas.Prefix, t *target, order []uint32) bool {
	var next atomic.Int64
	var aborted atomic.Bool
	over := make(chan struct{})
	var once sync.Once
	end := func() { once.Do(func() { close(over) }) }
	s.passDone.Store(0)

	var wg sync.WaitGroup
	for w := range s.workers {
		wk := worker{
			s: s, idx: w, conn: s.conns[w%len(s.conns)], pace: &s.pacers[w],
			prefix: p, target: t, order: order, next: &next, aborted: &aborted, over: over, end: end,
		}
		wg.Go(func() { wk.run(ctx) })
	}
	wg.Wait()
	return !aborted.Load() && ctx.Err() == nil
}

// worker is one send goroutine's view of a pass. Workers share only the claim counter, which moves
// once per chunk.
type worker struct {
	s       *Sender
	idx     int
	conn    Conn
	pace    *pacer
	prefix  canvas.Prefix
	target  *target
	order   []uint32
	next    *atomic.Int64
	aborted *atomic.Bool
	// over closes when the pass runs out of pixels or aborts, so a parked worker leaves at once.
	over chan struct{}
	end  func()
}

func (w *worker) run(ctx context.Context) {
	s := w.s
	bc := batcher(w.conn)
	// One reused address per message: the conns copy it out during the call, so a pass of any size
	// allocates nothing per packet.
	dsts := make([]net.Addr, batch)
	ips := make([]net.IP, batch)
	for i := range dsts {
		ips[i] = make(net.IP, net.IPv6len)
		if s.datagram {
			dsts[i] = &net.UDPAddr{IP: ips[i]}
		} else {
			dsts[i] = &net.IPAddr{IP: ips[i]}
		}
	}
	// speed is the packets a second this worker's last chunk went out at. A chunk is cut to what
	// it sends in chunkSpan, so the scaler's samples stay smooth on a socket slower than the rate.
	var speed float64
	var msgs []ipv6.Message
	if bc != nil {
		msgs = make([]ipv6.Message, batch)
		for i := range msgs {
			msgs[i] = ipv6.Message{Buffers: [][]byte{s.echo}, Addr: dsts[i]}
		}
	}
	for {
		active := int(s.active.Load())
		if w.idx >= active {
			if !w.idle(ctx) {
				return
			}
			continue
		}
		cfg := s.settings()
		share := float64(cfg.RatePPS) / float64(active)
		n := chunkSize(share)
		if speed > 0 {
			n = min(n, chunkSize(speed))
		} else {
			n = min(n, batch)
		}
		if !w.pace.take(ctx, n, share) {
			return
		}
		if ctx.Err() != nil || w.aborted.Load() {
			return
		}
		cfg = s.settings()
		// Abort when the prefix changes, the sender leaves Running, or the image is swapped: the
		// order then belongs to a stale target and the next pass reshuffles.
		if cfg.Prefix != w.prefix || s.decide(cfg) != Running || s.target.Load() != w.target {
			w.aborted.Store(true)
			w.end()
			return
		}
		from := int(w.next.Add(int64(n))) - n
		if from >= len(w.order) {
			w.end()
			return
		}
		chunk := w.order[from:min(from+n, len(w.order))]
		offs := s.offsets.Load()
		var sent, failed, skipped int
		var err error
		began := time.Now()
		for len(chunk) > 0 {
			part := chunk[:min(batch, len(chunk))]
			chunk = chunk[len(part):]
			k := w.address(part, offs, ips)
			skipped += len(part) - k
			if k == 0 {
				continue
			}
			var ok int
			if bc != nil {
				ok, err = writeBatch(bc, msgs[:k], err)
			} else {
				ok, err = s.writeEach(w.conn, dsts[:k], err)
			}
			sent += ok
			failed += k - ok
		}
		took := time.Since(began)
		s.busy.Add(int64(took))
		if took > 0 && sent+failed > 0 {
			speed = float64(sent+failed) / took.Seconds()
		}
		if failed > 0 {
			s.errors.Add(uint64(failed))
		}
		// A chunk whose every pixel moved off the canvas sent nothing and failed nothing.
		if sent > 0 || failed == 0 {
			s.sent.Add(uint64(sent))
			s.passDone.Add(int64(sent + skipped))
			if s.failing.Load() {
				s.failing.Store(false)
			}
			continue
		}
		s.fail(err)
		select {
		case <-ctx.Done():
			return
		case <-time.After(failPause):
		}
	}
}

// address writes the destination of each pixel in part into ips, shifted by its placement's offset,
// and returns how many it wrote; a pixel shifted off the canvas is skipped.
func (w *worker) address(part []uint32, offs *offsets, ips []net.IP) int {
	pixels := w.target.pixels
	if offs == nil {
		for k, i := range part {
			a := w.prefix.Address(pixels[i]).As16()
			copy(ips[k], a[:])
		}
		return len(part)
	}
	k := 0
	for _, i := range part {
		px, on := offs.shift(pixels[i])
		if !on {
			continue
		}
		a := w.prefix.Address(px).As16()
		copy(ips[k], a[:])
		k++
	}
	return k
}

// idle parks a worker the scaler has not asked for; it reports false once the pass is over.
func (w *worker) idle(ctx context.Context) bool {
	select {
	case <-ctx.Done():
		return false
	case <-w.over:
		return false
	case <-time.After(scaleEvery / 2):
		return true
	}
}

// writeEach sends one packet per syscall. It reports how many got out, and the last error, or
// err when none failed.
func (s *Sender) writeEach(conn Conn, dsts []net.Addr, err error) (int, error) {
	sent := 0
	for _, dst := range dsts {
		if _, e := conn.WriteTo(s.echo, dst); e != nil {
			err = e
			continue
		}
		sent++
	}
	return sent, err
}

// writeBatch sends msgs in as few syscalls as the socket allows. A batch write stops at the first
// message that fails, so that one is skipped and the rest go again.
func writeBatch(bc batchConn, msgs []ipv6.Message, err error) (int, error) {
	sent := 0
	for len(msgs) > 0 {
		n, e := bc.WriteBatch(msgs, 0)
		// sendmmsg refusing its first message returns -1, which x/net hands on as the count.
		n = max(n, 0)
		sent += n
		msgs = msgs[n:]
		if e != nil {
			err = e
			msgs = msgs[min(1, len(msgs)):]
		} else if n == 0 {
			break
		}
	}
	return sent, err
}

// fail books a chunk that got nothing out. It runs once per failPause at most, so the lock and
// the clock cost nothing.
func (s *Sender) fail(err error) {
	s.failing.Store(true)
	if err == nil {
		return
	}
	s.errMu.Lock()
	s.lastErr, s.lastErrAt = err.Error(), s.now()
	s.errMu.Unlock()
}

// sampleRate books the packets-a-second sample every second and resizes the worker pool every
// scaleEvery.
func (s *Sender) sampleRate(ctx context.Context) {
	tick := time.NewTicker(scaleEvery)
	defer tick.Stop()
	sc := newScaler(s.workers)
	perSecond := int(time.Second / scaleEvery)
	lastSent, lastBusy, lastSecond := s.sent.Load(), s.busy.Load(), s.sent.Load()
	for n := 1; ; n++ {
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
		}
		sent, busy := s.sent.Load(), s.busy.Load()
		if s.state.Load() == Running {
			pps := float64(sent-lastSent) / scaleEvery.Seconds()
			cores := float64(busy-lastBusy) / float64(scaleEvery)
			s.active.Store(int64(sc.step(time.Now(), pps, cores, float64(s.settings().RatePPS))))
		}
		lastSent, lastBusy = sent, busy
		if n%perSecond != 0 {
			continue
		}
		rate := sent - lastSecond
		lastSecond = sent
		s.actualPPS.Store(rate)
		s.rateMu.Lock()
		s.rates = append(s.rates, rate)
		if len(s.rates) > rateWindow {
			s.rates = s.rates[len(s.rates)-rateWindow:]
		}
		s.rateMu.Unlock()
	}
}
