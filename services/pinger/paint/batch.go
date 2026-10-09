package paint

import (
	"runtime"

	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv6"
)

// batchConn sends many packets in one syscall: sendmmsg on Linux.
type batchConn interface {
	WriteBatch(ms []ipv6.Message, flags int) (int, error)
}

// batcher returns the batch path for conn, or nil to send one packet per syscall. Off Linux,
// ipv6.PacketConn.WriteBatch sends only the first message, so it is used on Linux alone.
func batcher(conn Conn) batchConn {
	if bc, ok := conn.(batchConn); ok {
		return bc
	}
	if c, ok := conn.(*icmp.PacketConn); ok && runtime.GOOS == "linux" {
		if p := c.IPv6PacketConn(); p != nil {
			return p
		}
	}
	return nil
}
