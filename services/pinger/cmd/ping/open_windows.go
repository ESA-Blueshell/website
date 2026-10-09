//go:build windows

package main

import (
	"fmt"

	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

// openICMP opens raw sockets for an administrator and the ICMP helper API for anyone else. Either
// takes raw destinations, so it never reports a datagram socket.
func openICMP() ([]socket, bool, error) {
	socks, err := paint.OpenWindows()
	if err != nil {
		return nil, false, fmt.Errorf("could not open an ICMPv6 socket (need IPv6): %w", err)
	}
	out := make([]socket, len(socks))
	for i, c := range socks {
		out[i] = c
	}
	return out, false, nil
}
