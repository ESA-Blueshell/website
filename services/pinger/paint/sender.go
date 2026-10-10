// Package paint sends the logo to the canvas, one echo request per pixel, inside the rate cap.
package paint

import (
	"context"
	"errors"
	"math/rand/v2"
	"net"
	"os"
	"runtime"
	"sort"
	"sync"
	"sync/atomic"
	"syscall"
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

// target is the image as pixels, versioned so a swap is told apart from the image it replaces.
// The generation only ever grows.
type target struct {
	pixels []canvas.Pixel
	gen    uint64
}

// round is one pass over the image: the order it goes in, the prefix it addresses and how far the
// workers have claimed into it. Workers roll into a fresh round the moment one runs out, so passes
// follow each other without a gap.
type round struct {
	target *target
	prefix canvas.Prefix
	// seed orders the pass: pixels go in ascending rank(seed, pixel), so a swapped image can pick up
	// where the pass stood instead of starting over.
	seed  uint64
	order []uint32
	next  atomic.Int64
	done  atomic.Int64
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
	// queueWait is how long a transmit queue that refuses every send as full (ENOBUFS) is waited
	// on before the rest of the batch counts as lost. A frame socket that skips the qdisc writes
	// straight into the NIC's ring, and a virtio ring holds 256 frames, fewer than one chunk at
	// 30,000 a second; the pacer makes up the wait.
	queueWait  = 20 * time.Millisecond
	queueRetry = 100 * time.Microsecond
)

// Sender repaints the logo pass after pass, each pass in a fresh random order so the logo
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
	round     atomic.Pointer[round]
	roundMu   sync.Mutex
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
	// keys and spare are the rank sort's scratch, reused under roundMu.
	keys, spare []uint64
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

// SetPixels swaps the image the sender paints; the pass under way stops and a fresh one paints
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
	t := s.target.Load()
	var done int64
	if r := s.round.Load(); r != nil && r.target == t {
		done = r.done.Load()
	}
	return Stats{
		State:       s.state.Load().(State),
		Sent:        s.sent.Load(),
		Errors:      s.errors.Load(),
		Passes:      s.passes.Load(),
		PassDone:    int(min(done, int64(len(t.pixels)))),
		PassTotal:   len(t.pixels),
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
	for ctx.Err() == nil {
		if state := s.decide(s.settings()); state != Running {
			s.state.Store(state)
			select {
			case <-ctx.Done():
			case <-time.After(100 * time.Millisecond):
			}
			continue
		}
		s.state.Store(Running)
		s.stream(ctx)
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

// stream runs the workers until the sender may no longer send. The workers and their pacing
// outlive a pass: a small logo runs a pass in milliseconds, and stopping between passes then costs
// a large share of the rate.
func (s *Sender) stream(ctx context.Context) {
	over := make(chan struct{})
	var once sync.Once
	end := func() { once.Do(func() { close(over) }) }
	var wg sync.WaitGroup
	for w := range s.workers {
		wk := worker{s: s, idx: w, conn: s.conns[w%len(s.conns)], pace: &s.pacers[w], over: over, end: end}
		wg.Go(func() { wk.run(ctx) })
	}
	wg.Wait()
}

// roll replaces the spent or stale round r with a fresh one over the current image, counting a
// pass when r ran out. A round made stale by a new image keeps its pass: the api pushes a share
// every second, and restarting on each would never finish a pass over a large logo. Of the workers
// that find r spent, only the first rolls; the rest get its round.
func (s *Sender) roll(r *round, p canvas.Prefix, finished bool) *round {
	s.roundMu.Lock()
	defer s.roundMu.Unlock()
	if cur := s.round.Load(); cur != r {
		return cur
	}
	if finished {
		s.passes.Add(1)
	}
	t := s.target.Load()
	nr := &round{target: t, prefix: p, seed: rand.Uint64()}
	carry := !finished && r != nil && r.prefix == p
	if carry {
		nr.seed = r.seed
	}
	nr.order = s.rank(nr.seed, t.pixels)
	if carry {
		if at := int(min(r.next.Load(), int64(len(r.order)))); at > 0 {
			reached := rank(r.seed, r.target.pixels[r.order[at-1]])
			from := sort.Search(len(nr.order), func(i int) bool { return rank(nr.seed, t.pixels[nr.order[i]]) > reached })
			nr.next.Store(int64(from))
			nr.done.Store(int64(from))
		}
	}
	s.round.Store(nr)
	return nr
}

// rank places a pixel in the pass seed orders. It hashes the pixel's position alone, so the same
// pixel keeps its place across images and colours.
func rank(seed uint64, px canvas.Pixel) uint32 {
	z := seed ^ uint64(px.X)<<16 ^ uint64(px.Y)
	z = (z ^ z>>30) * 0xbf58476d1ce4e5b9
	z = (z ^ z>>27) * 0x94d049bb133111eb
	return uint32((z ^ z>>31) >> 32)
}

// rank returns the indices of pixels in ascending rank. A radix sort keeps it linear, since it
// runs on every share push and a large logo holds millions of pixels.
func (s *Sender) rank(seed uint64, pixels []canvas.Pixel) []uint32 {
	n := len(pixels)
	if cap(s.keys) < n {
		s.keys, s.spare = make([]uint64, n), make([]uint64, n)
	}
	keys, spare := s.keys[:n], s.spare[:n]
	for i, px := range pixels {
		keys[i] = uint64(rank(seed, px))<<32 | uint64(i)
	}
	for shift := 32; shift < 64; shift += 16 {
		var counts [1 << 16]int
		for _, k := range keys {
			counts[k>>shift&0xffff]++
		}
		pos := 0
		for b, c := range counts {
			counts[b] = pos
			pos += c
		}
		for _, k := range keys {
			b := k >> shift & 0xffff
			spare[counts[b]] = k
			counts[b]++
		}
		keys, spare = spare, keys
	}
	order := make([]uint32, n)
	for i, k := range keys {
		order[i] = uint32(k)
	}
	return order
}

// worker is one send goroutine. Workers share only the round's claim counter, which moves once per
// chunk.
type worker struct {
	s    *Sender
	idx  int
	conn Conn
	pace *pacer
	// over closes when the stream ends, so a parked worker leaves at once.
	over chan struct{}
	end  func()
	bc   batchConn
	dsts []net.Addr
	ips  []net.IP
	msgs []ipv6.Message
}

func (w *worker) run(ctx context.Context) {
	s := w.s
	w.bc = batcher(w.conn)
	// One reused address per message: the conns copy it out during the call, so a pass of any size
	// allocates nothing per packet.
	w.dsts = make([]net.Addr, batch)
	w.ips = make([]net.IP, batch)
	for i := range w.dsts {
		w.ips[i] = make(net.IP, net.IPv6len)
		if s.datagram {
			w.dsts[i] = &net.UDPAddr{IP: w.ips[i]}
		} else {
			w.dsts[i] = &net.IPAddr{IP: w.ips[i]}
		}
	}
	if w.bc != nil {
		w.msgs = make([]ipv6.Message, batch)
		for i := range w.msgs {
			w.msgs[i] = ipv6.Message{Buffers: [][]byte{s.echo}, Addr: w.dsts[i]}
		}
	}
	// speed is the packets a second this worker's last chunk went out at. A chunk is cut to what
	// it sends in chunkSpan, so the scaler's samples stay smooth on a socket slower than the rate.
	var speed float64
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
		// take returns at once while behind schedule, so it never sees ctx end on a slow socket.
		if !w.pace.take(ctx, n, share) || ctx.Err() != nil {
			return
		}
		select {
		case <-w.over:
			return
		default:
		}
		cfg = s.settings()
		if s.decide(cfg) != Running {
			w.end()
			return
		}
		began := time.Now()
		sent, failed, err := w.chunk(n, cfg.Prefix)
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
			if s.failing.Load() {
				s.failing.Store(false)
			}
			continue
		}
		s.fail(err)
		select {
		case <-ctx.Done():
			return
		case <-w.over:
			return
		case <-time.After(failPause):
		}
	}
}

// chunk sends the next n pixels, rolling into a fresh round when this one runs out or the prefix
// or image it paints has changed.
func (w *worker) chunk(n int, p canvas.Prefix) (sent, failed int, err error) {
	s := w.s
	offs := s.offsets.Load()
	r := s.round.Load()
	if r == nil || r.prefix != p || r.target != s.target.Load() {
		r = s.roll(r, p, false)
	}
	for n > 0 && len(r.order) > 0 {
		from := int(r.next.Add(int64(n))) - n
		if from >= len(r.order) {
			r = s.roll(r, p, true)
			continue
		}
		claim := r.order[from:min(from+n, len(r.order))]
		n -= len(claim)
		reached := 0
		for len(claim) > 0 {
			part := claim[:min(batch, len(claim))]
			claim = claim[len(part):]
			k := w.address(r, part, offs)
			reached += len(part) - k
			if k == 0 {
				continue
			}
			var ok int
			if w.bc != nil {
				ok, err = writeBatch(w.bc, w.msgs[:k], err)
			} else {
				ok, err = s.writeEach(w.conn, w.dsts[:k], err)
			}
			sent += ok
			failed += k - ok
			reached += ok
		}
		r.done.Add(int64(reached))
	}
	return sent, failed, err
}

// address writes the destination of each pixel in part into ips, shifted by its placement's offset,
// and returns how many it wrote; a pixel shifted off the canvas is skipped.
func (w *worker) address(r *round, part []uint32, offs *offsets) int {
	pixels, ips := r.target.pixels, w.ips
	if offs == nil {
		for k, i := range part {
			a := r.prefix.Address(pixels[i]).As16()
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
		a := r.prefix.Address(px).As16()
		copy(ips[k], a[:])
		k++
	}
	return k
}

// idle parks a worker the scaler has not asked for; it reports false once the stream is over.
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
// message that fails. One refused by a full transmit queue goes again once the queue drains, since
// skipping it would only throw the rest at the same full queue; any other failure is skipped and
// the rest go again.
func writeBatch(bc batchConn, msgs []ipv6.Message, err error) (int, error) {
	sent := 0
	var giveUp time.Time
	for len(msgs) > 0 {
		n, e := bc.WriteBatch(msgs, 0)
		// sendmmsg refusing its first message returns -1, which x/net hands on as the count.
		n = max(n, 0)
		sent += n
		msgs = msgs[n:]
		if n > 0 {
			giveUp = time.Time{}
		}
		switch {
		case e == nil:
			if n == 0 {
				return sent, err
			}
		case errors.Is(e, syscall.ENOBUFS):
			now := time.Now()
			if giveUp.IsZero() {
				giveUp = now.Add(queueWait)
			} else if now.After(giveUp) {
				return sent, e
			}
			time.Sleep(queueRetry)
		default:
			err = e
			msgs = msgs[min(1, len(msgs)):]
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
