package runner

import (
	"fmt"

	"golang.org/x/net/icmp"
)

// openICMP opens an unprivileged ICMP datagram socket where the OS allows it (macOS, most Linux),
// and falls back to a raw socket otherwise (Windows needs admin). The bool reports whether the
// datagram socket won, so the sender addresses it with UDP destinations. This mirrors the helper in
// the pinger's cmd/ping; it is socket setup, not paint logic, so the one engine stays shared.
func openICMP() (*icmp.PacketConn, bool, error) {
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
