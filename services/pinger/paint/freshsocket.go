package paint

import (
	"errors"
	"net"
	"os"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/net/icmp"
)

const (
	// freshSendBuffer is the send buffer FreshSocket asks for, under macOS's default 8 MiB
	// kern.ipc.maxsockbuf.
	freshSendBuffer = 4 << 20
	// freshEvery swaps the socket after this many sends. Behind a content filter each send costs
	// more the more destinations its socket has reached: 16 workers held about 1,500 pps swapping
	// every 4,096 and 3,000 every 256. Each swap closes a socket on a goroutine for about a second,
	// so much lower piles up blocked threads for little more rate.
	freshEvery = 256
	// freshStall bounds a send: a wedged socket fails the send and is replaced instead of
	// holding the worker forever.
	freshStall = 100 * time.Millisecond
)

// FreshSocket is an ICMPv6 socket that replaces itself every freshEvery sends and whenever a send
// stalls. On macOS each new destination is charged to the socket's send buffer and not handed
// back while the socket lives, about 500 bytes a destination on a Mac with a network content
// filter: the default 8 KiB wedged after 16 sends and 4 MiB after about 8,000. A fresh socket
// starts with the whole buffer again.
type FreshSocket struct {
	open   func() (net.PacketConn, error)
	every  uint64
	stall  time.Duration
	mu     sync.RWMutex
	conn   net.PacketConn
	closed bool
	sends  atomic.Uint64
}

// ListenFresh opens a FreshSocket on network ("udp6" or "ip6:ipv6-icmp"). prepare, when set, runs
// on every socket it opens, so an option set on the first carries over to its replacements.
func ListenFresh(network string, prepare func(*icmp.PacketConn)) (*FreshSocket, error) {
	return newFreshSocket(func() (net.PacketConn, error) {
		c, err := icmp.ListenPacket(network, "::")
		if err != nil {
			return nil, err
		}
		if b, ok := c.IPv6PacketConn().PacketConn.(interface{ SetWriteBuffer(int) error }); ok {
			_ = b.SetWriteBuffer(freshSendBuffer)
		}
		if prepare != nil {
			prepare(c)
		}
		return c, nil
	}, freshEvery, freshStall)
}

func newFreshSocket(open func() (net.PacketConn, error), every uint64, stall time.Duration) (*FreshSocket, error) {
	c, err := open()
	if err != nil {
		return nil, err
	}
	return &FreshSocket{open: open, every: every, stall: stall, conn: c}, nil
}

func (s *FreshSocket) WriteTo(b []byte, dst net.Addr) (int, error) {
	s.mu.RLock()
	c := s.conn
	_ = c.SetWriteDeadline(time.Now().Add(s.stall))
	n, err := c.WriteTo(b, dst)
	s.mu.RUnlock()
	if errors.Is(err, os.ErrDeadlineExceeded) || s.sends.Add(1)%s.every == 0 {
		s.replace(c)
	}
	return n, err
}

// replace swaps old for a new socket, unless another sender already did or none opens.
func (s *FreshSocket) replace(old net.PacketConn) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed || s.conn != old {
		return
	}
	c, err := s.open()
	if err != nil {
		return
	}
	s.conn = c
	// Closing a socket that reached many destinations takes macOS a second or more; the sends
	// must not wait it out.
	go old.Close()
}

// ReadFrom reads the current socket; a read on a socket that gets replaced fails, and the next
// read goes to the new one.
func (s *FreshSocket) ReadFrom(b []byte) (int, net.Addr, error) {
	s.mu.RLock()
	c := s.conn
	s.mu.RUnlock()
	return c.ReadFrom(b)
}

func (s *FreshSocket) SetReadDeadline(t time.Time) error {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.conn.SetReadDeadline(t)
}

func (s *FreshSocket) Close() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.closed = true
	return s.conn.Close()
}
