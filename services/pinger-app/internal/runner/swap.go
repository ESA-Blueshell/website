package runner

import (
	"net"
	"sync/atomic"

	"golang.org/x/net/ipv6"
)

// swapSocket is a send socket the watch can replace while the workers send on it, so a path that
// gets nothing out gives way to the fallback without restarting the sender. It keeps the first
// send error since the watch last looked, which the sender itself only books once a chunk ends.
type swapSocket struct {
	cur atomic.Pointer[socket]
	err atomic.Pointer[error]
}

func newSwapSocket(c socket) *swapSocket {
	s := &swapSocket{}
	s.cur.Store(&c)
	return s
}

func (s *swapSocket) load() socket { return *s.cur.Load() }

func (s *swapSocket) WriteTo(b []byte, dst net.Addr) (int, error) {
	n, err := s.load().WriteTo(b, dst)
	if err != nil {
		s.note(err)
	}
	return n, err
}

// WriteBatch hands the batch to the socket's own batch send, or sends it one packet at a time and
// stops at the first failure, as a batch send does.
func (s *swapSocket) WriteBatch(ms []ipv6.Message, flags int) (int, error) {
	c := s.load()
	if bc, ok := c.(interface {
		WriteBatch([]ipv6.Message, int) (int, error)
	}); ok {
		n, err := bc.WriteBatch(ms, flags)
		if err != nil {
			s.note(err)
		}
		return n, err
	}
	for i := range ms {
		if len(ms[i].Buffers) == 0 {
			continue
		}
		if _, err := c.WriteTo(ms[i].Buffers[0], ms[i].Addr); err != nil {
			s.note(err)
			return i, err
		}
	}
	return len(ms), nil
}

// note keeps err unless an error is already waiting: a broken path fails every send, and storing
// each would allocate per packet.
func (s *swapSocket) note(err error) {
	if s.err.Load() == nil {
		s.keep(err)
	}
}

func (s *swapSocket) keep(err error) { s.err.Store(&err) }

// takeErr returns the error noted since the last call, or nil.
func (s *swapSocket) takeErr() error {
	if p := s.err.Swap(nil); p != nil {
		return *p
	}
	return nil
}

// swap puts next in place and returns the socket it replaced, which the caller closes.
func (s *swapSocket) swap(next socket) socket {
	old := s.cur.Swap(&next)
	s.err.Store(nil)
	return *old
}

func (s *swapSocket) Close() error { return s.load().Close() }
