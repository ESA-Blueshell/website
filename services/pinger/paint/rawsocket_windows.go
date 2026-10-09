//go:build windows

package paint

import (
	"errors"
	"net"
	"os"
	"sync"
	"time"
	"unsafe"

	"golang.org/x/net/ipv6"
	"golang.org/x/sys/windows"
)

const (
	// rawSendBuffer lets the stack take a burst of sends at once; a send past the buffer blocks.
	rawSendBuffer = 4 << 20
	// rawSendStall bounds a blocked send, so a wedged path fails sends instead of holding a worker.
	rawSendStall = time.Second
)

// rawSocket is a blocking raw ICMPv6 socket written with WSASendTo directly. Go's net package sends
// on an overlapped socket through the IOCP poller, building a heap sockaddr and pinning the buffer
// for every packet, and parks the goroutine on the poller whenever a send does not finish at once.
// A blocking socket with one reused sockaddr pays for the system call alone. Opening it needs an
// administrator.
type rawSocket struct {
	h    windows.Handle
	mu   sync.Mutex
	sa   windows.RawSockaddrInet6
	buf  windows.WSABuf
	sent uint32
}

func listenRaw() (*rawSocket, error) {
	// No WSA_FLAG_OVERLAPPED: calls on the socket block in the calling thread.
	h, err := windows.WSASocket(windows.AF_INET6, windows.SOCK_RAW, windows.IPPROTO_ICMPV6, nil, 0,
		windows.WSA_FLAG_NO_HANDLE_INHERIT)
	if err != nil {
		return nil, os.NewSyscallError("wsasocket", err)
	}
	if err := windows.Bind(h, &windows.SockaddrInet6{}); err != nil {
		_ = windows.Closesocket(h)
		return nil, os.NewSyscallError("bind", err)
	}
	_ = windows.SetsockoptInt(h, windows.SOL_SOCKET, windows.SO_SNDBUF, rawSendBuffer)
	_ = windows.SetsockoptInt(h, windows.SOL_SOCKET, windows.SO_SNDTIMEO, int(rawSendStall/time.Millisecond))
	return &rawSocket{h: h, sa: windows.RawSockaddrInet6{Family: windows.AF_INET6}}, nil
}

func (s *rawSocket) WriteTo(b []byte, dst net.Addr) (int, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := s.send(b, dst); err != nil {
		return 0, err
	}
	return len(b), nil
}

// WriteBatch sends msgs one system call each, as Windows has no batch send for raw sockets, but
// takes the socket once for the lot. It reports the batch as sendAll does.
func (s *rawSocket) WriteBatch(msgs []ipv6.Message, _ int) (int, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return sendAll(msgs, s.send)
}

func (s *rawSocket) send(b []byte, dst net.Addr) error {
	addr, err := ipv6Dest(dst)
	if err != nil {
		return err
	}
	if len(b) == 0 {
		return errors.New("empty packet")
	}
	s.sa.Addr = addr
	s.buf = windows.WSABuf{Len: uint32(len(b)), Buf: &b[0]}
	err = windows.WSASendTo(s.h, &s.buf, 1, &s.sent, 0,
		(*windows.RawSockaddrAny)(unsafe.Pointer(&s.sa)), int32(unsafe.Sizeof(s.sa)), nil, nil)
	if err != nil {
		return os.NewSyscallError("wsasendto", err)
	}
	return nil
}

// ReadFrom blocks until an ICMPv6 packet arrives, as a raw socket receives a copy of each, or until
// Close.
func (s *rawSocket) ReadFrom(b []byte) (int, net.Addr, error) {
	n, from, err := windows.Recvfrom(s.h, b, 0)
	if err != nil {
		return 0, nil, os.NewSyscallError("recvfrom", err)
	}
	if sa, ok := from.(*windows.SockaddrInet6); ok {
		return n, &net.IPAddr{IP: net.IP(sa.Addr[:])}, nil
	}
	return n, nil, nil
}

// SetReadDeadline does nothing: Windows documents a socket whose receive timed out as unusable, and
// this one also sends.
func (s *rawSocket) SetReadDeadline(time.Time) error { return nil }

func (s *rawSocket) Close() error { return windows.Closesocket(s.h) }
