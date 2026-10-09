package paint

import "io"

// Socket is a send socket the caller also closes.
type Socket interface {
	Conn
	io.Closer
}

// OpenSockets opens Workers() sockets with open, one per send worker, closing the ones already
// open if any fails.
func OpenSockets[S Socket](open func() (S, error)) ([]S, error) {
	out := make([]S, 0, Workers())
	for range Workers() {
		c, err := open()
		if err != nil {
			CloseSockets(out)
			return nil, err
		}
		out = append(out, c)
	}
	return out, nil
}

// Conns is socks as the sender takes them.
func Conns[S Socket](socks []S) []Conn {
	out := make([]Conn, len(socks))
	for i, c := range socks {
		out[i] = c
	}
	return out
}

func CloseSockets[S Socket](socks []S) {
	for _, c := range socks {
		_ = c.Close()
	}
}
