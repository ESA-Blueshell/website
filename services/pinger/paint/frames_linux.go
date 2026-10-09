//go:build linux

package paint

import (
	"encoding/binary"
	"errors"
	"fmt"
	"math/rand/v2"
	"net"
	"net/netip"
	"time"

	"golang.org/x/net/bpf"
	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv6"
	"golang.org/x/sys/unix"
)

// learnTemplate pings dst once through the kernel and reads the frame it sent back off a packet
// socket, so routing, source address selection and the neighbour lookup are the kernel's own.
func learnTemplate(dst [16]byte, probe *icmp.PacketConn) (*frameTemplate, error) {
	id, seq := rand.N(uint16(0xffff))+1, uint16(rand.Uint32())
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
	echo, err := (&icmp.Message{Type: ipv6.ICMPTypeEchoRequest, Body: &icmp.Echo{ID: int(id), Seq: int(seq)}}).Marshal(nil)
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
			if binary.BigEndian.Uint16(buf[frameHead+6:]) != seq {
				continue
			}
			if err := checkOwnFrame(buf[:n], ll.Ifindex); err != nil {
				return nil, err
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

func htons(v uint16) uint16 { return v<<8 | v>>8 }
