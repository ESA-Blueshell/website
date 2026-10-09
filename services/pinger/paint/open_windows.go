//go:build windows

package paint

import (
	"fmt"
	"net"
	"time"
)

// WindowsSocket is an ICMPv6 sender on Windows that a caller can also drain.
type WindowsSocket interface {
	Socket
	ReadFrom(b []byte) (int, net.Addr, error)
	SetReadDeadline(t time.Time) error
}

// OpenWindows opens Workers() ICMPv6 senders: raw sockets when the process runs as an
// administrator, and otherwise handles on the ICMP helper API, which needs no rights but holds
// every request open until it times out. Both take raw and datagram destinations alike.
func OpenWindows() ([]WindowsSocket, error) {
	raw, rawErr := OpenSockets(func() (WindowsSocket, error) { return listenRaw() })
	if rawErr == nil {
		return raw, nil
	}
	api, apiErr := OpenSockets(func() (WindowsSocket, error) { return listenEchoAPI() })
	if apiErr == nil {
		return api, nil
	}
	return nil, fmt.Errorf("raw ICMPv6 socket: %v; ICMP helper API: %w", rawErr, apiErr)
}
