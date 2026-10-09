package runner

import (
	"net"
	"runtime"
	"time"

	"golang.org/x/net/icmp"

	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

// socket is the ICMPv6 socket the sender writes to and drainSocket empties.
type socket interface {
	paint.Conn
	ReadFrom(b []byte) (int, net.Addr, error)
	SetReadDeadline(t time.Time) error
	Close() error
}

// listenAll opens one socket per send worker: workers sharing a socket queue behind each other.
func listenAll(network string) ([]socket, error) {
	return paint.OpenSockets(func() (socket, error) { return listen(network) })
}

// listen opens a FreshSocket on macOS, where a long-lived socket that reaches many destinations
// wedges, and a plain socket elsewhere. Every socket, each replacement included, is marked low
// priority.
func listen(network string) (socket, error) {
	if runtime.GOOS == "darwin" {
		return paint.ListenFresh(network, markLowPriority)
	}
	c, err := icmp.ListenPacket(network, "::")
	if err != nil {
		return nil, err
	}
	markLowPriority(c)
	return c, nil
}
