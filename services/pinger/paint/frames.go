//go:build linux || darwin

package paint

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"log/slog"
	"net"
	"net/netip"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/net/icmp"
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

// checkOwnFrame refuses a frame that does not leave from this host to a single next hop: its source
// MAC must be the interface's own, its source address one of ours, and its destination MAC unicast
// and someone else's. PACKET_OUTGOING already rules out frames received from the link.
func checkOwnFrame(f []byte, ifindex int) error {
	ifc, err := net.InterfaceByIndex(ifindex)
	if err != nil {
		return err
	}
	dstMAC, srcMAC := net.HardwareAddr(f[:6]), net.HardwareAddr(f[6:12])
	if !bytes.Equal(srcMAC, ifc.HardwareAddr) {
		return fmt.Errorf("the probe left %s from %s, not its own address %s", ifc.Name, srcMAC, ifc.HardwareAddr)
	}
	if dstMAC[0]&1 != 0 || bytes.Equal(dstMAC, srcMAC) {
		return fmt.Errorf("the probe's next hop %s is not another single host", dstMAC)
	}
	src := netip.AddrFrom16([16]byte(f[ethHeader+8 : ethHeader+24]))
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return err
	}
	for _, a := range addrs {
		if p, ok := a.(*net.IPNet); ok {
			if ip, ok := netip.AddrFromSlice(p.IP); ok && ip.Unmap() == src {
				return nil
			}
		}
	}
	return fmt.Errorf("the probe's source %s is not an address of this host", src)
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

func destination(a net.Addr) [16]byte {
	var d [16]byte
	switch a := a.(type) {
	case *net.IPAddr:
		copy(d[:], a.IP.To16())
	case *net.UDPAddr:
		copy(d[:], a.IP.To16())
	}
	return d
}
