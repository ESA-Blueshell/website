package paint

import (
	"errors"
	"net"
	"testing"
)

type closeConn struct {
	nopWriter
	closed *int
}

type nopWriter struct{}

func (nopWriter) WriteTo(b []byte, _ net.Addr) (int, error) { return len(b), nil }

func (c closeConn) Close() error { *c.closed++; return nil }

func TestOpenSocketsOpensOnePerWorker(t *testing.T) {
	closed := 0
	socks, err := OpenSockets(func() (closeConn, error) { return closeConn{closed: &closed}, nil })
	if err != nil {
		t.Fatal(err)
	}
	if len(socks) != Workers() || len(Conns(socks)) != Workers() {
		t.Fatalf("opened %d sockets, want %d", len(socks), Workers())
	}
	CloseSockets(socks)
	if closed != Workers() {
		t.Fatalf("closed %d of %d", closed, Workers())
	}
}

func TestOpenSocketsClosesWhatItOpenedWhenOneFails(t *testing.T) {
	closed, opened := 0, 0
	socks, err := OpenSockets(func() (closeConn, error) {
		if opened == 1 {
			return closeConn{}, errors.New("no socket")
		}
		opened++
		return closeConn{closed: &closed}, nil
	})
	if err == nil || socks != nil || closed != 1 {
		t.Fatalf("socks=%v err=%v closed=%d; want an error, nothing returned and the first closed", socks, err, closed)
	}
}
