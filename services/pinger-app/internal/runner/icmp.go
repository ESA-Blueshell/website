package runner

import (
	"log/slog"
	"runtime"

	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv6"

	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

// socket is the ICMPv6 socket the sender writes to. Nothing reads it: discardInbound has the
// kernel drop what comes back.
type socket = paint.Socket

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
