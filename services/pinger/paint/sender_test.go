package paint

import (
	"context"
	"net"
	"net/netip"
	"sync"
	"sync/atomic"
	"syscall"
	"testing"
	"time"

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
	s := NewSender(conn, px, w, box.get)
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

	snap := s.Snapshot()
	if snap.Errors != 3 {
		t.Fatalf("counted %d errors, want 3", snap.Errors)
	}
	if snap.LastError != syscall.ENOBUFS.Error() {
		t.Fatalf("last error %q", snap.LastError)
	}
}

func TestSenderReportsABrokenPathAndRecovers(t *testing.T) {
	conn := &fakeConn{}
	conn.fails.Store(1 << 30)
	box := &settingsBox{}
	box.set(Settings{Prefix: prefix(t, "2001:db8::"), RatePPS: 100_000, Enabled: true})

	s := start(t, conn, pixels(200), open, box)
	eventually(t, func() bool { return s.Snapshot().Failing })

	conn.fails.Store(0)
	eventually(t, func() bool { return !s.Snapshot().Failing })
}
