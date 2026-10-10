package runner

import (
	"log/slog"
	"net"
	"net/netip"
	"time"
)

// noIPv6Message is the status line for a network that cannot reach the canvas at all.
const noIPv6Message = "This network has no IPv6, so pings can't reach the canvas. " +
	"Try a phone hotspot or another network, or turn on IPv6 in your router."

// ipv6Recheck is how often the route check runs again, so a member who switches networks sees
// the verdict follow.
const ipv6Recheck = 30 * time.Second

// canvasProbe is an address in the event's prefix; port 9 is discard, though nothing is sent.
var canvasProbe = netip.AddrPortFrom(netip.MustParseAddr("2001:610:5ea:221e::1"), 9)

// routeSource returns the source address this machine would reach the canvas from. Connecting a
// UDP socket only runs route selection: it sends nothing and needs no admin rights on any OS.
func routeSource() (netip.Addr, error) {
	c, err := net.DialUDP("udp6", nil, net.UDPAddrFromAddrPort(canvasProbe))
	if err != nil {
		return netip.Addr{}, err
	}
	defer c.Close()
	return c.LocalAddr().(*net.UDPAddr).AddrPort().Addr(), nil
}

// reachesIPv6 reports whether a route lookup ended at a source the internet can answer. A
// link-local or ULA source means the network hands out no global IPv6.
func reachesIPv6(src netip.Addr, err error) bool {
	return err == nil && src.Is6() && !src.Is4In6() && src.IsGlobalUnicast() && !src.IsPrivate()
}

// ipv6Watch holds the latest route check.
type ipv6Watch struct {
	probe func() (netip.Addr, error)
	next  time.Time
	ok    bool
}

func newIPv6Watch() *ipv6Watch { return &ipv6Watch{probe: routeSource} }

// missing reports whether the network has no IPv6, checking again once ipv6Recheck has passed or
// at once when forced.
func (v *ipv6Watch) missing(now time.Time, force bool) bool {
	if v == nil {
		return false
	}
	if force || !now.Before(v.next) {
		src, err := v.probe()
		ok := reachesIPv6(src, err)
		if ok != v.ok || v.next.IsZero() {
			slog.Info("ipv6 route check", "reaches", ok, "source", src, "err", err)
		}
		v.ok, v.next = ok, now.Add(ipv6Recheck)
	}
	return !v.ok
}
