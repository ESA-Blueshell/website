//go:build windows

package runner

import (
	"fmt"

	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

// openICMP opens raw sockets for an administrator and the ICMP helper API for anyone else. A raw
// socket that opens can still be refused every send by Windows or security software, so the
// helper API stays on hand as its fallback. Either takes raw destinations, so it never reports a
// datagram socket.
func openICMP() (opened, error) {
	raw, rawErr := paint.OpenRaw()
	if rawErr == nil {
		return opened{socks: windowsSockets(raw), path: pathRaw, track: true, fallback: openEchoAPI}, nil
	}
	api, err := openEchoAPI()
	if err != nil {
		return opened{}, fmt.Errorf("could not open an ICMPv6 socket (need IPv6): raw ICMPv6 socket: %v; ICMP helper API: %w", rawErr, err)
	}
	return api, nil
}

func openEchoAPI() (opened, error) {
	socks, err := paint.OpenEchoAPI()
	if err != nil {
		return opened{path: pathEcho}, err
	}
	return opened{socks: windowsSockets(socks), path: pathEcho, track: true}, nil
}

func windowsSockets(socks []paint.WindowsSocket) []socket {
	out := make([]socket, len(socks))
	for i, c := range socks {
		out[i] = c
	}
	return out
}
