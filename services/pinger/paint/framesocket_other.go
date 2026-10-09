//go:build !linux && !darwin

package paint

import (
	"errors"
	"net"
	"time"

	"golang.org/x/net/icmp"
)

// FrameSocket is Linux's packet-socket sender; elsewhere OpenFrameSockets always fails.
type FrameSocket struct{}

func OpenFrameSockets(bool, func(*icmp.PacketConn)) ([]*FrameSocket, error) {
	return nil, errors.ErrUnsupported
}

func (*FrameSocket) WriteTo([]byte, net.Addr) (int, error)  { return 0, errors.ErrUnsupported }
func (*FrameSocket) ReadFrom([]byte) (int, net.Addr, error) { return 0, nil, errors.ErrUnsupported }
func (*FrameSocket) SetReadDeadline(time.Time) error        { return errors.ErrUnsupported }
func (*FrameSocket) Close() error                           { return nil }

// WarnIfConntrack does nothing: only Linux's netfilter takes an entry per ping.
func WarnIfConntrack() {}
