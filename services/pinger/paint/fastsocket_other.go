//go:build !darwin

package paint

import "golang.org/x/net/icmp"

// ListenFast is a FreshSocket off macOS; the link-layer path is the Mac's alone.
func ListenFast(network string, prepare func(*icmp.PacketConn)) (PacketSocket, error) {
	return ListenFresh(network, prepare)
}
