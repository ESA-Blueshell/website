package runner

import (
	"fmt"
	"log/slog"
	"os"
	"runtime"

	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv6"

	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

// socket is the ICMPv6 socket the sender writes to. Nothing reads it: discardInbound has the
// kernel drop what comes back.
type socket = paint.Socket

// openICMP opens an ICMPv6 socket and reports whether it is a datagram socket (addressed with UDP
// destinations), preferring the unprivileged datagram socket and falling back to raw where the OS
// needs it (Windows). Root opens raw first: macOS caps the unprivileged datagram socket at a few
// outstanding sends to unresolved destinations, wedging a local test; raw has no such cap.
func openICMP() ([]socket, bool, error) {
	if os.Geteuid() == 0 {
		if c, err := listenAll("ip6:ipv6-icmp"); err == nil {
			return c, false, nil
		}
	}
	if c, err := listenAll("udp6"); err == nil {
		return c, true, nil
	}
	c, err := listenAll("ip6:ipv6-icmp")
	if err != nil {
		return nil, false, fmt.Errorf("could not open an ICMPv6 socket (need IPv6; on Windows run as administrator, "+
			"on Linux either run with sudo or set once: sudo sysctl -w net.ipv6.ping_group_range=\"0 2147483647\"): %w", err)
	}
	return c, false, nil
}

// listenAll opens one socket per send worker: workers sharing a socket queue behind each other.
func listenAll(network string) ([]socket, error) {
	return paint.OpenSockets(func() (socket, error) { return listen(network) })
}

// listen opens a FreshSocket on macOS, where a long-lived socket that reaches many destinations
// wedges, and a plain socket elsewhere. Every socket, each replacement included, is prepared.
func listen(network string) (socket, error) {
	if runtime.GOOS == "darwin" {
		return paint.ListenFresh(network, prepare)
	}
	c, err := icmp.ListenPacket(network, "::")
	if err != nil {
		return nil, err
	}
	prepare(c)
	return c, nil
}

func prepare(c *icmp.PacketConn) {
	markLowPriority(c)
	discardInbound(c)
}

// discardInbound has the kernel drop every reply and ICMP error instead of queueing it for a reader
// the app does not run: the filter blocks all ICMPv6 types (macOS, Linux raw, BSD), and the smallest
// receive buffer drops whatever the filter cannot (Windows, Linux ping sockets). A full receive
// buffer only drops arrivals; it never blocks a send. On macOS a loopback target measured 12k sends
// a second with a default buffer left unread and 70k with this.
func discardInbound(c *icmp.PacketConn) {
	p := c.IPv6PacketConn()
	if p == nil {
		return
	}
	var block ipv6.ICMPFilter
	block.SetAll(true)
	if err := p.SetICMPFilter(&block); err != nil {
		slog.Debug("filter inbound ICMPv6", "err", err)
	}
	if b, ok := p.PacketConn.(interface{ SetReadBuffer(int) error }); ok {
		if err := b.SetReadBuffer(1); err != nil {
			slog.Debug("shrink receive buffer", "err", err)
		}
	}
}
