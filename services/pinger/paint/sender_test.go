package paint

import (
	"context"
	"errors"
	"net"
	"net/netip"
	"sync"
	"sync/atomic"
	"syscall"
	"testing"
	"time"

	"golang.org/x/net/ipv6"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
)

type fakeConn struct {
	mu    sync.Mutex
	sent  []netip.Addr
	fails atomic.Int32
}

func (c *fakeConn) WriteTo(b []byte, dst net.Addr) (int, error) {
	if c.fails.Load() > 0 {
		c.fails.Add(-1)
		return 0, syscall.ENOBUFS
	}
	addr, _ := netip.AddrFromSlice(dst.(*net.IPAddr).IP)
	c.mu.Lock()
	c.sent = append(c.sent, addr)
	c.mu.Unlock()
	return len(b), nil
}

func (c *fakeConn) addresses() []netip.Addr {
	c.mu.Lock()
	defer c.mu.Unlock()
	return append([]netip.Addr(nil), c.sent...)
}

type settingsBox struct{ v atomic.Pointer[Settings] }

func (b *settingsBox) set(s Settings) { b.v.Store(&s) }
func (b *settingsBox) get() Settings  { return *b.v.Load() }

var (
	open   = Window{Start: time.Unix(0, 0), End: time.Unix(1<<40, 0)}
	closed = Window{Start: time.Unix(1<<40, 0), End: time.Unix(1<<41, 0)}
)

func prefix(t *testing.T, s string) canvas.Prefix {
	t.Helper()
	p, err := canvas.ParsePrefix(s)
	if err != nil {
		t.Fatal(err)
	}
	return p
}

func pixels(n int) []canvas.Pixel {
	out := make([]canvas.Pixel, n)
	for i := range out {
		out[i] = canvas.Pixel{X: uint16(3000 + i), Y: 2000, R: 1, G: 2, B: 3, A: 0xff}
	}
	return out
}

func start(t *testing.T, conn Conn, px []canvas.Pixel, w Window, box *settingsBox) *Sender {
	t.Helper()
	s := NewSender([]Conn{conn}, px, w, box.get)
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	go func() { s.Run(ctx); close(done) }()
	t.Cleanup(func() { cancel(); <-done })
	return s
}

func eventually(t *testing.T, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(3 * time.Second)
	for !cond() {
		if time.Now().After(deadline) {
			t.Fatal("condition not met in 3s")
		}
		time.Sleep(5 * time.Millisecond)
	}
}

func TestSenderPaintsEveryPixelOncePerPass(t *testing.T) {
	conn := &fakeConn{}
	box := &settingsBox{}
	p := prefix(t, "2001:db8:b317:a000::/64")
	box.set(Settings{Prefix: p, RatePPS: 100_000, Enabled: true})
	px := pixels(200)

	s := start(t, conn, px, open, box)
	eventually(t, func() bool { return s.Snapshot().Passes >= 1 })

	seen := map[netip.Addr]int{}
	for _, a := range conn.addresses()[:len(px)] {
		seen[a]++
	}
	for _, want := range px {
		if seen[p.Address(want)] != 1 {
			t.Fatalf("%s sent %d times in the first pass", p.Address(want), seen[p.Address(want)])
		}
	}
	if s.Snapshot().State != Running {
		t.Fatalf("state %s, want running", s.Snapshot().State)
	}
}

func TestSenderPaintsASwappedImage(t *testing.T) {
	conn := &fakeConn{}
	box := &settingsBox{}
	box.set(Settings{Prefix: prefix(t, "2001:db8::/64"), RatePPS: 100_000, Enabled: true})

	s := start(t, conn, pixels(10), open, box)
	eventually(t, func() bool { return s.Snapshot().PassTotal == 10 })

	s.SetPixels(pixels(40))
	eventually(t, func() bool { return s.Snapshot().PassTotal == 40 })
	eventually(t, func() bool { return s.Snapshot().Passes >= 2 })

	// After the swap the sender addresses all 40 pixels, not the 10 it started with.
	seen := map[string]bool{}
	for _, a := range conn.addresses() {
		seen[a.String()] = true
	}
	if len(seen) < 40 {
		t.Fatalf("addressed %d distinct pixels, want 40", len(seen))
	}
}

func TestSenderStaysSilentWhenItMayNotSend(t *testing.T) {
	p := prefix(t, "2001:db8:b317:a000::/64")
	cases := []struct {
		name     string
		window   Window
		settings Settings
		state    State
	}{
		{"no prefix", open, Settings{RatePPS: 100_000, Enabled: true}, Idle},
		{"no rate", open, Settings{Prefix: p, RatePPS: 0, Enabled: true}, Idle},
		{"SiteCie off", open, Settings{Prefix: p, RatePPS: 100_000, Enabled: false}, Idle},
		{"outside the event", closed, Settings{Prefix: p, RatePPS: 100_000, Enabled: true}, Closed},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			conn := &fakeConn{}
			box := &settingsBox{}
			box.set(c.settings)

			s := start(t, conn, pixels(10), c.window, box)
			eventually(t, func() bool { return s.Snapshot().State == c.state })
			time.Sleep(50 * time.Millisecond)

			if n := len(conn.addresses()); n != 0 {
				t.Fatalf("sent %d packets", n)
			}
		})
	}
}

func TestSenderHoldsTheRateCap(t *testing.T) {
	conn := &fakeConn{}
	box := &settingsBox{}
	box.set(Settings{Prefix: prefix(t, "2001:db8::"), RatePPS: 1000, Enabled: true})

	start(t, conn, pixels(5000), open, box)
	time.Sleep(500 * time.Millisecond)

	if n := len(conn.addresses()); n < 300 || n > 650 {
		t.Fatalf("sent %d packets in 500ms at 1000 pps", n)
	}
}

func TestSenderMovesToANewPrefixMidPass(t *testing.T) {
	conn := &fakeConn{}
	box := &settingsBox{}
	first, second := prefix(t, "2001:db8:1::"), prefix(t, "2001:db8:2::")
	box.set(Settings{Prefix: first, RatePPS: 2000, Enabled: true})

	start(t, conn, pixels(5000), open, box)
	eventually(t, func() bool { return len(conn.addresses()) > 0 })
	box.set(Settings{Prefix: second, RatePPS: 2000, Enabled: true})

	want := second.Address(pixels(1)[0])
	inSecond := func(a netip.Addr) bool { return netip.PrefixFrom(want, 64).Masked().Contains(a) }
	eventually(t, func() bool {
		sent := conn.addresses()
		return inSecond(sent[len(sent)-1])
	})
}

func TestSenderCountsALocalSendErrorAndCarriesOn(t *testing.T) {
	conn := &fakeConn{}
	conn.fails.Store(3)
	box := &settingsBox{}
	box.set(Settings{Prefix: prefix(t, "2001:db8::"), RatePPS: 100_000, Enabled: true})

	s := start(t, conn, pixels(100), open, box)
	eventually(t, func() bool { return s.Snapshot().Passes >= 2 })

	if snap := s.Snapshot(); snap.Errors != 3 || snap.Failing {
		t.Fatalf("counted %d errors, failing %v; want 3 and a path that works", snap.Errors, snap.Failing)
	}
}

func TestSenderReportsABrokenPathAndRecovers(t *testing.T) {
	conn := &fakeConn{}
	conn.fails.Store(1 << 30)
	box := &settingsBox{}
	box.set(Settings{Prefix: prefix(t, "2001:db8::"), RatePPS: 100_000, Enabled: true})

	s := start(t, conn, pixels(200), open, box)
	eventually(t, func() bool { return s.Snapshot().Failing })
	if got := s.Snapshot().LastError; got != syscall.ENOBUFS.Error() {
		t.Fatalf("last error %q", got)
	}

	conn.fails.Store(0)
	eventually(t, func() bool { return !s.Snapshot().Failing })
}

// exclusiveConn fails the test if two sends overlap on it, the way a real socket serialises them.
type exclusiveConn struct {
	t        *testing.T
	inFlight atomic.Int32
	sent     atomic.Int64
}

func (c *exclusiveConn) WriteTo(b []byte, _ net.Addr) (int, error) {
	if c.inFlight.Add(1) > 1 {
		c.t.Error("two workers wrote to one socket at once")
	}
	time.Sleep(50 * time.Microsecond)
	c.inFlight.Add(-1)
	c.sent.Add(1)
	return len(b), nil
}

func TestSenderGivesEachWorkerItsOwnSocket(t *testing.T) {
	conns := make([]*exclusiveConn, Workers())
	asConns := make([]Conn, len(conns))
	for i := range conns {
		conns[i] = &exclusiveConn{t: t}
		asConns[i] = conns[i]
	}
	box := &settingsBox{}
	box.set(Settings{Prefix: prefix(t, "2001:db8::"), RatePPS: 1_000_000, Enabled: true})
	s := NewSender(asConns, pixels(50_000), open, box.get)
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	go func() { s.Run(ctx); close(done) }()
	t.Cleanup(func() { cancel(); <-done })

	eventually(t, func() bool {
		for _, c := range conns {
			if c.sent.Load() == 0 {
				return false
			}
		}
		return true
	})
}

// batchFake sends whole batches; while fails is positive, each call gets half its batch out and
// then fails on the next message, the way sendmmsg stops at the first send that errs.
type batchFake struct {
	fakeConn
	batches atomic.Int64
	singles atomic.Int64
}

func (c *batchFake) WriteTo(b []byte, dst net.Addr) (int, error) {
	c.singles.Add(1)
	return c.fakeConn.WriteTo(b, dst)
}

func (c *batchFake) WriteBatch(ms []ipv6.Message, _ int) (int, error) {
	c.batches.Add(1)
	n, failing := len(ms), c.fails.Load() > 0
	if failing {
		c.fails.Add(-1)
		n = len(ms) / 2
	}
	c.mu.Lock()
	for _, m := range ms[:n] {
		addr, _ := netip.AddrFromSlice(m.Addr.(*net.IPAddr).IP)
		c.sent = append(c.sent, addr)
	}
	c.mu.Unlock()
	if failing {
		return n, syscall.ENOBUFS
	}
	return n, nil
}

func TestSenderSendsWholeBatchesWhereTheSocketCan(t *testing.T) {
	conn := &batchFake{}
	box := &settingsBox{}
	p := prefix(t, "2001:db8::/64")
	box.set(Settings{Prefix: p, RatePPS: 1_000_000, Enabled: true})
	px := pixels(batch * 10)

	s := start(t, conn, px, open, box)
	eventually(t, func() bool { return s.Snapshot().Passes >= 1 })

	seen := map[netip.Addr]int{}
	for _, a := range conn.addresses()[:len(px)] {
		seen[a]++
	}
	for _, want := range px {
		if seen[p.Address(want)] != 1 {
			t.Fatalf("%s sent %d times in the first pass", p.Address(want), seen[p.Address(want)])
		}
	}
	if conn.singles.Load() != 0 {
		t.Fatalf("%d single sends on a socket that takes batches", conn.singles.Load())
	}
}

func TestSenderSkipsOnlyTheMessageABatchFailedOn(t *testing.T) {
	conn := &batchFake{}
	conn.fails.Store(3)
	box := &settingsBox{}
	box.set(Settings{Prefix: prefix(t, "2001:db8::"), RatePPS: 1_000_000, Enabled: true})

	s := start(t, conn, pixels(batch*4), open, box)
	eventually(t, func() bool { return s.Snapshot().Passes >= 2 })
	snap := s.Snapshot()

	if snap.Errors != 3 {
		t.Fatalf("counted %d errors, want 3", snap.Errors)
	}
	if got := uint64(len(conn.addresses())); got < snap.Sent {
		t.Fatalf("counted %d sent, but only %d left the socket", snap.Sent, got)
	}
}

// refusingBatch fails its first message the way sendmmsg does when the kernel refuses it outright:
// -1 and the error, which x/net passes on unchanged.
type refusingBatch struct{ calls int }

func (c *refusingBatch) WriteBatch(ms []ipv6.Message, _ int) (int, error) {
	c.calls++
	if c.calls == 1 {
		return -1, syscall.EPERM
	}
	return len(ms), nil
}

func TestWriteBatchSkipsAMessageTheKernelRefusedOutright(t *testing.T) {
	msgs := make([]ipv6.Message, 4)

	sent, err := writeBatch(&refusingBatch{}, msgs, nil)

	if sent != 3 || !errors.Is(err, syscall.EPERM) {
		t.Fatalf("sent %d with %v, want 3 with EPERM", sent, err)
	}
}

type countConn struct{ n atomic.Int64 }

func (c *countConn) WriteTo(b []byte, _ net.Addr) (int, error) {
	c.n.Add(1)
	return len(b), nil
}

// A logo of a hundred pixels at 20,000 a second runs two hundred passes a second; a pause between
// passes shows up here as a rate well under the target.
func TestSenderHoldsTheTargetOnASmallLogo(t *testing.T) {
	conn := &countConn{}
	box := &settingsBox{}
	const rate = 20_000
	box.set(Settings{Prefix: prefix(t, "2001:db8::"), RatePPS: rate, Enabled: true})

	s := start(t, conn, pixels(100), open, box)
	eventually(t, func() bool { return conn.n.Load() > 0 })
	from, began := conn.n.Load(), time.Now()
	time.Sleep(time.Second)
	sent, elapsed := conn.n.Load()-from, time.Since(began).Seconds()

	if limit := rate*(elapsed+paceSlack.Seconds()) + float64(chunkSize(rate)); float64(sent) > limit {
		t.Fatalf("sent %d in %.3fs, over the %d a second cap", sent, elapsed, rate)
	}
	if float64(sent) < 0.95*rate*elapsed {
		t.Fatalf("sent %d in %.3fs, under 95%% of %d a second", sent, elapsed, rate)
	}
	if s.Snapshot().Passes < 100 {
		t.Fatalf("ran %d passes", s.Snapshot().Passes)
	}
}

// Passes run back to back, so the stream is cut into whole passes only by counting: each run of
// len(px) sends is one pass, and paints every pixel exactly once.
func TestSenderPaintsEveryPixelOnceInEachOfSeveralPasses(t *testing.T) {
	conn := &fakeConn{}
	box := &settingsBox{}
	p := prefix(t, "2001:db8:b317:a000::/64")
	box.set(Settings{Prefix: p, RatePPS: 20_000, Enabled: true})
	px := pixels(150)

	s := start(t, conn, px, open, box)
	eventually(t, func() bool { return s.Snapshot().Passes >= 5 })

	sent := conn.addresses()
	for pass := range 5 {
		seen := map[netip.Addr]int{}
		for _, a := range sent[pass*len(px) : (pass+1)*len(px)] {
			seen[a]++
		}
		for _, want := range px {
			if seen[p.Address(want)] != 1 {
				t.Fatalf("pass %d sent %s %d times", pass, p.Address(want), seen[p.Address(want)])
			}
		}
	}
}
