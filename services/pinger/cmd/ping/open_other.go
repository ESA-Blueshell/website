//go:build !windows

package main

import "fmt"

// openICMP opens an unprivileged ICMP datagram socket where the OS allows it, and falls back to
// a raw socket otherwise. The bool reports whether the datagram socket won, so the sender knows
// to address it with UDP destinations.
func openICMP() ([]socket, bool, error) {
	if c, err := listenAll("udp6"); err == nil {
		return c, true, nil
	}
	c, err := listenAll("ip6:ipv6-icmp")
	if err != nil {
		return nil, false, fmt.Errorf("could not open an ICMPv6 socket (need IPv6; on Linux either run with sudo "+
			"or set once: sudo sysctl -w net.ipv6.ping_group_range=\"0 2147483647\"): %w", err)
	}
	return c, false, nil
}
