package paint

import (
	"errors"
	"net"
	"os"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"golang.org/x/net/icmp"
)

// cappedConn behaves like the macOS socket: each send takes buffer the socket never hands back,
// so once room runs out a send waits for its deadline and fails.
type cappedConn struct {
	net.PacketConn
	mu       sync.Mutex
	room     int
	deadline time.Time
	closed   bool
	sent     int
}

func (c *cappedConn) WriteTo(b []byte, _ net.Addr) (int, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.closed {
		return 0, net.ErrClosed
	}
	if c.room == 0 {
		if c.deadline.IsZero() {
			return 0, errors.New("blocked forever: no write deadline set")
		}
		return 0, os.ErrDeadlineExceeded
	}
	c.room--
	c.sent++
	return len(b), nil
}

func (c *cappedConn) SetWriteDeadline(t time.Time) error {
	c.mu.Lock()
	c.deadline = t
	c.mu.Unlock()
	return nil
}

func (c *cappedConn) SetReadDeadline(time.Time) error { return nil }

func (c *cappedConn) ReadFrom([]byte) (int, net.Addr, error) { return 7, nil, nil }

func (c *cappedConn) isClosed() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.closed
}

func (c *cappedConn) Close() error {
	c.mu.Lock()
	c.closed = true
	c.mu.Unlock()
	return nil
}

type cappedOpener struct {
	room   int
	opened []*cappedConn
	fail   atomic.Bool
}

func (o *cappedOpener) open() (net.PacketConn, error) {
	if o.fail.Load() {
		return nil, errors.New("out of sockets")
	}
	c := &cappedConn{room: o.room}
	o.opened = append(o.opened, c)
	return c, nil
}

func newTestSocket(t *testing.T, o *cappedOpener, every uint64) *FreshSocket {
	t.Helper()
	s, err := newFreshSocket(o.open, every, time.Millisecond)
	if err != nil {
		t.Fatal(err)
	}
	return s
}

func TestFreshSocketReplacesItselfBeforeTheBufferRunsOut(t *testing.T) {
	o := &cappedOpener{room: 16}
	s := newTestSocket(t, o, 8)
	for i := range 100 {
		if _, err := s.WriteTo([]byte("x"), &net.UDPAddr{}); err != nil {
			t.Fatalf("send %d: %v", i, err)
		}
	}
	if len(o.opened) < 100/8 {
		t.Fatalf("opened %d sockets, want at least %d", len(o.opened), 100/8)
	}
	for _, c := range o.opened[:len(o.opened)-1] {
		eventually(t, c.isClosed)
	}
}

func TestFreshSocketReplacesAStalledSocketSoTheNextSendGoesOut(t *testing.T) {
	o := &cappedOpener{room: 3}
	s := newTestSocket(t, o, 1000)
	var errs int
	for range 10 {
		if _, err := s.WriteTo([]byte("x"), &net.UDPAddr{}); err != nil {
			if !errors.Is(err, os.ErrDeadlineExceeded) {
				t.Fatalf("got %v, want a deadline error", err)
			}
			errs++
		}
	}
	// 3 go out, the 4th stalls and swaps the socket, 3 more, the 8th stalls, then 2.
	if errs != 2 || len(o.opened) != 3 {
		t.Fatalf("errors %d over %d sockets, want 2 over 3", errs, len(o.opened))
	}
}

func TestFreshSocketKeepsTheOldSocketWhenNoNewOneOpens(t *testing.T) {
	o := &cappedOpener{room: 100}
	s := newTestSocket(t, o, 2)
	o.fail.Store(true)
	for i := range 5 {
		if _, err := s.WriteTo([]byte("x"), &net.UDPAddr{}); err != nil {
			t.Fatalf("send %d: %v", i, err)
		}
	}
	if len(o.opened) != 1 || o.opened[0].isClosed() || o.opened[0].sent != 5 {
		t.Fatal("the only socket should stay open and carry every send")
	}
}

func TestFreshSocketReadsAndClosesTheCurrentSocket(t *testing.T) {
	o := &cappedOpener{room: 100}
	s := newTestSocket(t, o, 1)
	_, _ = s.WriteTo([]byte("x"), &net.UDPAddr{})
	if n, _, _ := s.ReadFrom(make([]byte, 8)); n != 7 {
		t.Fatalf("read %d bytes, want 7", n)
	}
	if err := s.SetReadDeadline(time.Now()); err != nil {
		t.Fatal(err)
	}
	if err := s.Close(); err != nil {
		t.Fatal(err)
	}
	opened := len(o.opened)
	if !o.opened[opened-1].isClosed() {
		t.Fatal("Close left the current socket open")
	}
	_, _ = s.WriteTo([]byte("x"), &net.UDPAddr{})
	if len(o.opened) != opened {
		t.Fatal("a closed socket opened another one")
	}
}

func TestListenFreshPreparesEverySocketItOpens(t *testing.T) {
	var prepared []*icmp.PacketConn
	s, err := ListenFresh("udp6", func(c *icmp.PacketConn) { prepared = append(prepared, c) })
	if err != nil {
		t.Skipf("no unprivileged ICMPv6 socket here: %v", err)
	}
	defer s.Close()
	s.replace(s.conn)
	if len(prepared) != 2 || prepared[1] != s.conn {
		t.Fatalf("prepared %d sockets, want the first and its replacement", len(prepared))
	}
}
