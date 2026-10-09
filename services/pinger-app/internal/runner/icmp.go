package runner

import (
	"fmt"
	"os"

	"golang.org/x/net/icmp"
)

// openICMP opens an ICMPv6 socket and reports whether it is a datagram socket (addressed with UDP
// destinations), preferring the unprivileged datagram socket and falling back to raw where the OS
// needs it (Windows). Root opens raw first: macOS caps the unprivileged datagram socket at a few
// outstanding sends to unresolved destinations, wedging a local test; raw has no such cap.
func openICMP() (*icmp.PacketConn, bool, error) {
	if os.Geteuid() == 0 {
		if c, err := icmp.ListenPacket("ip6:ipv6-icmp", "::"); err == nil {
			return c, false, nil
		}
	}
	if c, err := icmp.ListenPacket("udp6", "::"); err == nil {
		return c, true, nil
	}
	c, err := icmp.ListenPacket("ip6:ipv6-icmp", "::")
	if err != nil {
		return nil, false, fmt.Errorf("could not open an ICMPv6 socket (need IPv6; on Windows run as administrator, "+
			"on Linux either run with sudo or set once: sudo sysctl -w net.ipv6.ping_group_range=\"0 2147483647\"): %w", err)
	}
	return c, false, nil
}
