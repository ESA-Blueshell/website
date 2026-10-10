//go:build linux

package paint

import (
	"errors"
	"log/slog"
	"net"
	"sync"
	"sync/atomic"
	"time"
	"unsafe"

	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv6"
	"golang.org/x/sys/unix"
)

// frameSendBuffer is the packet socket's send buffer: frames queue here while the NIC drains them.
const frameSendBuffer = 4 << 20

const frameSlot = frameHead + maxICMPLen

// FrameSocket sends echo requests as whole Ethernet frames on a packet socket, past the kernel's
// IPv6 output path: no route lookup per packet, no netfilter (so no conntrack entry per pixel, which
// fills the table and drops pings and the host's own new connections), and with bypass no qdisc.
// It needs CAP_NET_RAW. A destination whose route is not Ethernet goes through its raw ICMP socket.
type FrameSocket struct {
	fd    int
	raw   *icmp.PacketConn
	route *frameRoute
	// ring is nil where the kernel refuses a TX ring; frames then go out by sendmmsg.
	ring  *txRing
	mu    sync.Mutex
	frame []byte
	iovs  []unix.Iovec
	hdrs  []mmsghdr
	addr  unix.RawSockaddrLinklayer
}

var errFrameSize = errors.New("a frame takes one echo request of at most 64 bytes")

// useTxRing is off only in benchmarks that measure the sendmmsg path.
var useTxRing = true

var ringFallbackLogged atomic.Bool

func logRingFallback(err error) {
	if ringFallbackLogged.CompareAndSwap(false, true) {
		slog.Warn("no TX ring on the packet socket, sending frames by sendmmsg", "err", err)
	}
}

type mmsghdr struct {
	hdr unix.Msghdr
	len uint32
}

// OpenFrameSockets opens Workers() frame sockets sharing one learned route. bypass skips the qdisc,
// which saves CPU but also skips the fair queueing that keeps the host's other traffic moving.
// prepare runs on each raw ICMP socket, whose traffic class the frames copy.
func OpenFrameSockets(bypass bool, prepare func(*icmp.PacketConn)) ([]*FrameSocket, error) {
	route := &frameRoute{}
	return OpenSockets(func() (*FrameSocket, error) { return openFrameSocket(route, bypass, prepare) })
}

func openFrameSocket(route *frameRoute, bypass bool, prepare func(*icmp.PacketConn)) (*FrameSocket, error) {
	fd, err := packetSocket(bypass)
	if err != nil {
		return nil, err
	}
	var ring *txRing
	if useTxRing {
		if ring, err = openTxRing(fd); err != nil {
			logRingFallback(err)
			// A half-set-up ring or virtio header would change how sendmmsg reads every message.
			_ = unix.Close(fd)
			if fd, err = packetSocket(bypass); err != nil {
				return nil, err
			}
		}
	}
	raw, err := icmp.ListenPacket("ip6:ipv6-icmp", "::")
	if err != nil {
		_ = ring.close()
		_ = unix.Close(fd)
		return nil, err
	}
	// Nothing reads the replies, so the kernel need not queue every ICMPv6 message to this socket.
	var block ipv6.ICMPFilter
	block.SetAll(true)
	_ = raw.IPv6PacketConn().SetICMPFilter(&block)
	if prepare != nil {
		prepare(raw)
	}
	return &FrameSocket{fd: fd, raw: raw, route: route, ring: ring}, nil
}

// packetSocket opens a send-only packet socket. Protocol 0: no incoming frame is ever copied to it.
func packetSocket(bypass bool) (int, error) {
	fd, err := unix.Socket(unix.AF_PACKET, unix.SOCK_RAW|unix.SOCK_CLOEXEC, 0)
	if err != nil {
		return -1, err
	}
	if bypass {
		_ = unix.SetsockoptInt(fd, unix.SOL_PACKET, unix.PACKET_QDISC_BYPASS, 1)
	}
	if unix.SetsockoptInt(fd, unix.SOL_SOCKET, unix.SO_SNDBUFFORCE, frameSendBuffer) != nil {
		_ = unix.SetsockoptInt(fd, unix.SOL_SOCKET, unix.SO_SNDBUF, frameSendBuffer)
	}
	return fd, nil
}

// WriteBatch sends ms in one syscall, through the TX ring or sendmmsg, every message routed as the
// first one is.
func (s *FrameSocket) WriteBatch(ms []ipv6.Message, flags int) (int, error) {
	if len(ms) == 0 {
		return 0, nil
	}
	t := s.route.template(destination(ms[0].Addr), s.raw)
	if t == nil {
		return s.raw.IPv6PacketConn().WriteBatch(ms, flags)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.addr = unix.RawSockaddrLinklayer{
		Family:   unix.AF_PACKET,
		Protocol: htons(unix.ETH_P_IPV6),
		Ifindex:  t.ifindex,
		Halen:    6,
		Addr:     t.gateway,
	}
	var n int
	var err error
	if s.ring != nil {
		n, err = s.writeRing(t, ms)
	} else {
		n, err = s.writeMmsg(t, ms)
	}
	if errors.Is(err, unix.ENETDOWN) || errors.Is(err, unix.ENXIO) || errors.Is(err, unix.ENODEV) {
		s.route.invalidate()
	}
	return n, err
}

// writeRing queues ms in the TX ring and flushes them in one send.
func (s *FrameSocket) writeRing(t *frameTemplate, ms []ipv6.Message) (int, error) {
	for i, m := range ms {
		if len(m.Buffers) != 1 || len(m.Buffers[0]) > maxICMPLen {
			return i, errors.Join(errFrameSize, s.ring.flush(s.fd, &s.addr))
		}
		f, err := s.ring.claim(s.fd, &s.addr)
		if err != nil {
			return i, err
		}
		n := frameHead + len(m.Buffers[0])
		t.frame(f[:n], destination(m.Addr), m.Buffers[0])
		s.ring.ready(n)
	}
	if err := s.ring.flush(s.fd, &s.addr); err != nil {
		return 0, err
	}
	return len(ms), nil
}

func (s *FrameSocket) writeMmsg(t *frameTemplate, ms []ipv6.Message) (int, error) {
	s.grow(len(ms))
	for i, m := range ms {
		if len(m.Buffers) != 1 || len(m.Buffers[0]) > maxICMPLen {
			return 0, errFrameSize
		}
		f := s.frame[i*frameSlot : i*frameSlot+frameHead+len(m.Buffers[0])]
		t.frame(f, destination(m.Addr), m.Buffers[0])
		s.iovs[i].Base = &f[0]
		s.iovs[i].SetLen(len(f))
		h := &s.hdrs[i].hdr
		h.Name = (*byte)(unsafe.Pointer(&s.addr))
		h.Namelen = unix.SizeofSockaddrLinklayer
		h.Iov = &s.iovs[i]
		h.SetIovlen(1)
	}
	return sendmmsg(s.fd, s.hdrs[:len(ms)])
}

func (s *FrameSocket) grow(n int) {
	if len(s.hdrs) >= n {
		return
	}
	s.frame = make([]byte, n*frameSlot)
	s.iovs = make([]unix.Iovec, n)
	s.hdrs = make([]mmsghdr, n)
}

func (s *FrameSocket) WriteTo(b []byte, dst net.Addr) (int, error) {
	if _, err := s.WriteBatch([]ipv6.Message{{Buffers: [][]byte{b}, Addr: dst}}, 0); err != nil {
		return 0, err
	}
	return len(b), nil
}

// ReadFrom reads the raw ICMP socket, which filters out every message: it only ever times out.
func (s *FrameSocket) ReadFrom(b []byte) (int, net.Addr, error) { return s.raw.ReadFrom(b) }

func (s *FrameSocket) SetReadDeadline(t time.Time) error { return s.raw.SetReadDeadline(t) }

func (s *FrameSocket) Close() error {
	_ = s.ring.close()
	_ = unix.Close(s.fd)
	return s.raw.Close()
}

// sendmmsg blocks while the send buffer is full, which is the backpressure a saturated link needs.
func sendmmsg(fd int, hs []mmsghdr) (int, error) {
	n, _, errno := unix.Syscall6(unix.SYS_SENDMMSG, uintptr(fd), uintptr(unsafe.Pointer(&hs[0])), uintptr(len(hs)), 0, 0, 0)
	if errno != 0 {
		return 0, errno
	}
	return int(n), nil
}
