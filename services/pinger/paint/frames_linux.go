//go:build linux

package paint

import (
	"encoding/binary"
	"errors"
	"fmt"
	"log/slog"
	"math/rand/v2"
	"net"
	"net/netip"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/net/bpf"
	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv6"
	"golang.org/x/sys/unix"
)

const (
	ethHeader  = 14
	ip6Header  = 40
	frameHead  = ethHeader + ip6Header
	maxICMPLen = 64
	// relearnEvery bounds how long a learned route is trusted: a router failover or a new
	// source address shows up within this long.
	relearnEvery = 30 * time.Second
	// learnWait is how long one probe waits to see its own frame leave; a cold neighbour entry
	// costs a solicitation round trip first.
	learnWait = 500 * time.Millisecond
)

// frameTemplate is the Ethernet and IPv6 header the kernel put on a probe to one /64, which every
// echo request to that /64 shares but for the destination's low 64 bits and the checksum.
type frameTemplate struct {
	ifindex int32
	gateway [8]byte
	head    [frameHead]byte
	net64   [8]byte
	// sum is the ones'-complement sum of the pseudo-header but for the destination's low 64 bits
	// and the upper-layer length, which depends on the echo.
	sum     uint32
	learned time.Time
}

// frameRoute learns the frame template for the /64 being painted, shared by every FrameSocket
// one OpenFrameSockets opened. A /64 whose route is not Ethernet (a tunnel, PPP) has no template,
// and its sockets fall back to the raw ICMP socket until the next relearn.
type frameRoute struct {
	mu      sync.Mutex
	current atomic.Pointer[frameTemplate]
	failed  atomic.Pointer[failedLearn]
}

type failedLearn struct {
	net64 [8]byte
	at    time.Time
}

// template returns the frame template for dst, learning it first when dst lies outside the
// learned /64 or the learned one has aged out; nil means send dst through the raw socket.
func (r *frameRoute) template(dst [16]byte, probe *icmp.PacketConn) *frameTemplate {
	now := time.Now()
	if t := r.current.Load(); t != nil && [8]byte(dst[:8]) == t.net64 && now.Sub(t.learned) < relearnEvery {
		return t
	}
	if f := r.failed.Load(); f != nil && f.net64 == [8]byte(dst[:8]) && now.Sub(f.at) < relearnEvery {
		return nil
	}
	if !r.mu.TryLock() {
		// Another socket is learning: an aged template still matches its /64, so keep using it.
		if t := r.current.Load(); t != nil && [8]byte(dst[:8]) == t.net64 {
			return t
		}
		r.mu.Lock()
	}
	defer r.mu.Unlock()
	if t := r.current.Load(); t != nil && [8]byte(dst[:8]) == t.net64 && time.Since(t.learned) < relearnEvery {
		return t
	}
	t, err := learnTemplate(dst, probe)
	if err != nil {
		r.failed.Store(&failedLearn{net64: [8]byte(dst[:8]), at: time.Now()})
		logFallback(err)
		return nil
	}
	r.failed.Store(nil)
	r.current.Store(t)
	return t
}

// invalidate drops the learned template so the next send learns the route again.
func (r *frameRoute) invalidate() { r.current.Store(nil) }

var fallbackLogged atomic.Bool

func logFallback(err error) {
	if fallbackLogged.CompareAndSwap(false, true) {
		slog.Warn("sending through the raw socket instead of frames", "err", err)
	}
}

// learnTemplate pings dst once through the kernel and reads the frame it sent back off a packet
// socket, so routing, source address selection and the neighbour lookup are the kernel's own.
func learnTemplate(dst [16]byte, probe *icmp.PacketConn) (*frameTemplate, error) {
	id := rand.N(uint16(0xffff)) + 1
	// Only ETH_P_ALL sees frames leaving; the filter keeps a busy link from drowning the probe.
	fd, err := unix.Socket(unix.AF_PACKET, unix.SOCK_RAW|unix.SOCK_CLOEXEC, int(htons(unix.ETH_P_ALL)))
	if err != nil {
		return nil, fmt.Errorf("open packet socket: %w", err)
	}
	defer unix.Close(fd)
	if err := attachProbeFilter(fd, id); err != nil {
		return nil, err
	}
	tv := unix.NsecToTimeval((50 * time.Millisecond).Nanoseconds())
	if err := unix.SetsockoptTimeval(fd, unix.SOL_SOCKET, unix.SO_RCVTIMEO, &tv); err != nil {
		return nil, err
	}
	echo, err := (&icmp.Message{Type: ipv6.ICMPTypeEchoRequest, Body: &icmp.Echo{ID: int(id), Seq: 1}}).Marshal(nil)
	if err != nil {
		return nil, err
	}
	buf := make([]byte, 2048)
	for range 3 {
		if _, err := probe.WriteTo(echo, &net.IPAddr{IP: dst[:]}); err != nil {
			return nil, fmt.Errorf("probe %s: %w", netip.AddrFrom16(dst), err)
		}
		for deadline := time.Now().Add(learnWait); time.Now().Before(deadline); {
			n, from, err := unix.Recvfrom(fd, buf, 0)
			if err != nil {
				continue
			}
			ll, ok := from.(*unix.SockaddrLinklayer)
			if !ok || ll.Pkttype != unix.PACKET_OUTGOING || !isProbe(buf[:n], dst, id, ll.Hatype) {
				continue
			}
			if ll.Hatype != unix.ARPHRD_ETHER {
				return nil, fmt.Errorf("the route to %s is not Ethernet (link type %d)", netip.AddrFrom16(dst), ll.Hatype)
			}
			return newTemplate(buf[:n], int32(ll.Ifindex)), nil
		}
	}
	return nil, errors.New("the probe frame never left; no route or no neighbour")
}

// attachProbeFilter passes only ICMPv6 echo requests carrying id, read at the network header so
// it holds whatever the link type.
func attachProbeFilter(fd int, id uint16) error {
	const net = 0xfff00000 // SKF_NET_OFF
	prog, err := bpf.Assemble([]bpf.Instruction{
		bpf.LoadAbsolute{Off: net + 6, Size: 1},
		bpf.JumpIf{Cond: bpf.JumpNotEqual, Val: 58, SkipTrue: 5},
		bpf.LoadAbsolute{Off: net + ip6Header, Size: 1},
		bpf.JumpIf{Cond: bpf.JumpNotEqual, Val: uint32(ipv6.ICMPTypeEchoRequest), SkipTrue: 3},
		bpf.LoadAbsolute{Off: net + ip6Header + 4, Size: 2},
		bpf.JumpIf{Cond: bpf.JumpNotEqual, Val: uint32(id), SkipTrue: 1},
		bpf.RetConstant{Val: 0xffff},
		bpf.RetConstant{Val: 0},
	})
	if err != nil {
		return err
	}
	filter := make([]unix.SockFilter, len(prog))
	for i, in := range prog {
		filter[i] = unix.SockFilter{Code: in.Op, Jt: in.Jt, Jf: in.Jf, K: in.K}
	}
	return unix.SetsockoptSockFprog(fd, unix.SOL_SOCKET, unix.SO_ATTACH_FILTER, &unix.SockFprog{Len: uint16(len(filter)), Filter: &filter[0]})
}

// isProbe matches our probe by destination and echo ID: Ethernet frames carry it after the link
// header, and frames of any other link type are matched too so the caller can say why it refuses.
func isProbe(f []byte, dst [16]byte, id uint16, hatype uint16) bool {
	off := 0
	if hatype == unix.ARPHRD_ETHER {
		if len(f) < ethHeader || binary.BigEndian.Uint16(f[12:]) != unix.ETH_P_IPV6 {
			return false
		}
		off = ethHeader
	}
	p := f[off:]
	return len(p) >= ip6Header+8 && p[0]>>4 == 6 && p[6] == 58 && [16]byte(p[24:40]) == dst &&
		p[ip6Header] == byte(ipv6.ICMPTypeEchoRequest) && binary.BigEndian.Uint16(p[ip6Header+4:]) == id
}

func newTemplate(f []byte, ifindex int32) *frameTemplate {
	t := &frameTemplate{ifindex: ifindex, learned: time.Now()}
	copy(t.head[:], f[:frameHead])
	copy(t.gateway[:], f[:6])
	copy(t.net64[:], f[ethHeader+24:])
	ip := t.head[ethHeader:]
	t.sum = sum16(ip[8:32]) + 58
	return t
}

// frame writes the echo request icmp to dst into out, which holds frameHead+len(icmp) bytes.
func (t *frameTemplate) frame(out []byte, dst [16]byte, icmpMsg []byte) {
	copy(out, t.head[:])
	ip := out[ethHeader:]
	binary.BigEndian.PutUint16(ip[4:], uint16(len(icmpMsg)))
	copy(ip[32:40], dst[8:])
	m := ip[ip6Header:]
	copy(m, icmpMsg)
	m[2], m[3] = 0, 0
	s := t.sum + sum16(dst[8:]) + uint32(len(icmpMsg)) + sum16(m[:len(icmpMsg)])
	for s > 0xffff {
		s = s>>16 + s&0xffff
	}
	binary.BigEndian.PutUint16(m[2:], ^uint16(s))
}

// sum16 adds b as big-endian 16-bit words, an odd trailing byte padded with zero.
func sum16(b []byte) uint32 {
	var s uint32
	for len(b) >= 2 {
		s += uint32(b[0])<<8 | uint32(b[1])
		b = b[2:]
	}
	if len(b) == 1 {
		s += uint32(b[0]) << 8
	}
	return s
}

func htons(v uint16) uint16 { return v<<8 | v>>8 }
