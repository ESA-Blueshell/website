package paint

import (
	"net"
	"time"
)

// PacketSocket is a send socket whose incoming packets can also be drained.
type PacketSocket interface {
	Socket
	ReadFrom(b []byte) (int, net.Addr, error)
	SetReadDeadline(t time.Time) error
}
