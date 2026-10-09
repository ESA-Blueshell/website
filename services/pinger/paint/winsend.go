package paint

import (
	"errors"
	"net"
	"os"
	"sync"
	"time"

	"golang.org/x/net/ipv6"
)

// The Windows senders are built from these; they live untagged so their tests run on every OS.

var errNotIPv6 = errors.New("destination is not an IPv6 address")

// ipv6Dest is the 16-byte address a sender writes into a sockaddr it reuses, so a send allocates
// nothing.
func ipv6Dest(dst net.Addr) ([16]byte, error) {
	var ip net.IP
	switch a := dst.(type) {
	case *net.IPAddr:
		ip = a.IP
	case *net.UDPAddr:
		ip = a.IP
	}
	var out [16]byte
	if len(ip) != net.IPv6len || ip.To4() != nil {
		return out, errNotIPv6
	}
	copy(out[:], ip)
	return out, nil
}

// echoPayload is what follows the 8-byte echo header in b. The ICMP helper API writes its own
// header, so it takes the payload alone.
func echoPayload(b []byte) []byte {
	if len(b) <= 8 {
		return nil
	}
	return b[8:]
}

// sendAll sends every message in msgs and counts them all as gone: a lost ping costs nothing worth
// tracking. Only a first send that fails reports, so a broken path fails the batch at once instead
// of spending a system call on each message.
func sendAll(msgs []ipv6.Message, send func(b []byte, dst net.Addr) error) (int, error) {
	for i := range msgs {
		if len(msgs[i].Buffers) == 0 {
			continue
		}
		if err := send(msgs[i].Buffers[0], msgs[i].Addr); err != nil && i == 0 {
			return 0, err
		}
	}
	return len(msgs), nil
}

// readGate is a read that never yields a packet. It waits out the read deadline, or until Close, so
// a reader looping on it sleeps instead of spinning.
type readGate struct {
	mu       sync.Mutex
	deadline time.Time
	closed   chan struct{}
	once     sync.Once
}

func newReadGate() *readGate { return &readGate{closed: make(chan struct{})} }

func (g *readGate) SetReadDeadline(t time.Time) error {
	g.mu.Lock()
	g.deadline = t
	g.mu.Unlock()
	return nil
}

func (g *readGate) ReadFrom([]byte) (int, net.Addr, error) {
	g.mu.Lock()
	d := g.deadline
	g.mu.Unlock()
	if d.IsZero() {
		<-g.closed
		return 0, nil, net.ErrClosed
	}
	t := time.NewTimer(time.Until(d))
	defer t.Stop()
	select {
	case <-g.closed:
		return 0, nil, net.ErrClosed
	case <-t.C:
		return 0, nil, os.ErrDeadlineExceeded
	}
}

func (g *readGate) Close() { g.once.Do(func() { close(g.closed) }) }
