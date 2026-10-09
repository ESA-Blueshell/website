package runner

import (
	"fmt"
	"net"
	"os"
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

// openICMP opens an ICMPv6 socket and reports whether it is a datagram socket (addressed with UDP
// destinations), preferring the unprivileged datagram socket and falling back to raw where the OS
// needs it (Windows). Root opens raw first: macOS caps the unprivileged datagram socket at a few
// outstanding sends to unresolved destinations, wedging a local test; raw has no such cap.
func openICMP() (socket, bool, error) {
	if os.Geteuid() == 0 {
		if c, err := listen("ip6:ipv6-icmp"); err == nil {
			return c, false, nil
		}
	}
	if c, err := listen("udp6"); err == nil {
		return c, true, nil
	}
	c, err := listen("ip6:ipv6-icmp")
	if err != nil {
		return nil, false, fmt.Errorf("could not open an ICMPv6 socket (need IPv6; on Windows run as administrator, "+
			"on Linux either run with sudo or set once: sudo sysctl -w net.ipv6.ping_group_range=\"0 2147483647\"): %w", err)
	}
	return c, false, nil
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
