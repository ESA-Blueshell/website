package paint

import (
	"encoding/binary"
	"errors"
	"net"

	"golang.org/x/net/bpf"
	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv6"
)

const (
	ethHeaderLen     = 14
	ipv6HeaderLen    = 40
	linkHeaderLen    = ethHeaderLen + ipv6HeaderLen
	etherTypeIPv6    = 0x86dd
	nextHeaderICMPv6 = 58
)

// echoFilter keeps the Ethernet frames carrying an ICMPv6 echo request without extension
// headers, which is how the kernel sends the probe a link header is learned from.
var echoFilter = []bpf.Instruction{
	bpf.LoadAbsolute{Off: 12, Size: 2},
	bpf.JumpIf{Cond: bpf.JumpNotEqual, Val: etherTypeIPv6, SkipTrue: 5},
	bpf.LoadAbsolute{Off: ethHeaderLen + 6, Size: 1},
	bpf.JumpIf{Cond: bpf.JumpNotEqual, Val: nextHeaderICMPv6, SkipTrue: 3},
	bpf.LoadAbsolute{Off: linkHeaderLen, Size: 1},
	bpf.JumpIf{Cond: bpf.JumpNotEqual, Val: uint32(ipv6.ICMPTypeEchoRequest), SkipTrue: 1},
	bpf.RetConstant{Val: 1 << 16},
	bpf.RetConstant{Val: 0},
}

// dropAll keeps nothing, so a descriptor that only injects does not copy every passing frame.
var dropAll = []bpf.Instruction{bpf.RetConstant{Val: 0}}

// linkHeader is the Ethernet and IPv6 header the kernel put on a ping to one /64, reused for every
// other address in it: same interface, router, source address, hop limit and traffic class.
type linkHeader [linkHeaderLen]byte

// learnHeader takes the link header off frame, the kernel's own echo request to dst.
func learnHeader(frame []byte, dst net.IP) (linkHeader, bool) {
	var h linkHeader
	if len(frame) < linkHeaderLen+4 ||
		binary.BigEndian.Uint16(frame[12:]) != etherTypeIPv6 ||
		frame[ethHeaderLen+6] != nextHeaderICMPv6 ||
		!net.IP(frame[ethHeaderLen+24:linkHeaderLen]).Equal(dst) {
		return h, false
	}
	copy(h[:], frame)
	// A flow label names one flow, and the frames this header goes on are each their own.
	h[ethHeaderLen+1] &^= 0x0f
	h[ethHeaderLen+2], h[ethHeaderLen+3] = 0, 0
	return h, true
}

func (h *linkHeader) source() net.IP { return net.IP(h[ethHeaderLen+8 : ethHeaderLen+24]) }

// frame writes h, addressed to dst, and the echo request msg into out, with the ICMPv6 checksum
// the kernel fills in on a socket but a link-layer sender must compute itself.
func (h *linkHeader) frame(out []byte, msg *icmp.Message, dst net.IP) ([]byte, error) {
	body, err := msg.Marshal(icmp.IPv6PseudoHeader(h.source(), dst))
	if err != nil {
		return nil, err
	}
	out = append(append(out[:0], h[:]...), body...)
	binary.BigEndian.PutUint16(out[ethHeaderLen+4:], uint16(len(body)))
	copy(out[ethHeaderLen+24:linkHeaderLen], dst.To16())
	return out, nil
}

// bpfFrames splits a buffer read off /dev/bpf into its captured frames. Each sits behind a
// bpf_hdr (caplen at 8, hdrlen at 16) and starts on a 4-byte boundary.
func bpfFrames(buf []byte) [][]byte {
	var out [][]byte
	for len(buf) >= 18 {
		capLen := int(binary.NativeEndian.Uint32(buf[8:]))
		hdrLen := int(binary.NativeEndian.Uint16(buf[16:]))
		if hdrLen+capLen > len(buf) {
			break
		}
		out = append(out, buf[hdrLen:hdrLen+capLen])
		buf = buf[min(len(buf), (hdrLen+capLen+3)&^3):]
	}
	return out
}

func addrIP(a net.Addr) (net.IP, error) {
	switch a := a.(type) {
	case *net.UDPAddr:
		return a.IP, nil
	case *net.IPAddr:
		return a.IP, nil
	}
	return nil, errors.New("not an IP destination")
}
