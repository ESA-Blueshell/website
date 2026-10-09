//go:build darwin

package paint

import (
	"errors"
	"net"
	"sync"
	"time"

	"golang.org/x/net/bpf"
	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv6"
	"golang.org/x/sys/unix"
)

// frameSockets is how many /dev/bpf descriptors OpenFrameSockets opens: a write costs a few
// microseconds, so two carry the rate cap, and the sender's workers share them.
const frameSockets = 2

// FrameSocket writes echo requests as whole Ethernet frames through /dev/bpf, past the IP stack and
// any network content filter. On a Mac whose security software filters sockets, every ICMP datagram
// waits on that filter, which holds the whole machine to a few thousand new destinations a second
// however many sockets share the work. A destination whose route is not Ethernet, such as a VPN,
// goes through a FreshSocket.
type FrameSocket struct {
	fd     int
	iface  int32
	probe  *icmp.PacketConn
	kernel *FreshSocket
	dgram  bool
	route  *frameRoute
	mu     sync.Mutex
	frame  []byte
}

// OpenFrameSockets opens frameSockets frame sockets sharing one learned route, when this user may
// write /dev/bpf. bypass is Linux's alone. prepare runs on each kernel ICMP socket, whose traffic
// class the frames copy off the probe.
func OpenFrameSockets(_ bool, prepare func(*icmp.PacketConn)) ([]*FrameSocket, error) {
	route := &frameRoute{}
	out := make([]*FrameSocket, 0, frameSockets)
	for range frameSockets {
		s, err := openFrameSocket(route, prepare)
		if err != nil {
			CloseSockets(out)
			return nil, err
		}
		out = append(out, s)
	}
	return out, nil
}

func openFrameSocket(route *frameRoute, prepare func(*icmp.PacketConn)) (*FrameSocket, error) {
	fd, err := openBPF()
	if err != nil {
		return nil, err
	}
	// Writing needs no capture: keep nothing, so passing traffic is never copied here.
	if err := setFilter(fd, []bpf.Instruction{bpf.RetConstant{Val: 0}}); err != nil {
		_ = unix.Close(fd)
		return nil, err
	}
	network, dgram := "udp6", true
	probe, err := icmp.ListenPacket(network, "::")
	if err != nil {
		network, dgram = "ip6:ipv6-icmp", false
		if probe, err = icmp.ListenPacket(network, "::"); err != nil {
			_ = unix.Close(fd)
			return nil, err
		}
	}
	if prepare != nil {
		prepare(probe)
	}
	kernel, err := ListenFresh(network, prepare)
	if err != nil {
		_ = probe.Close()
		_ = unix.Close(fd)
		return nil, err
	}
	return &FrameSocket{fd: fd, probe: probe, kernel: kernel, dgram: dgram, route: route}, nil
}

// WriteBatch writes ms as frames, every message routed as the first one is, and stops at the first
// write the interface refuses.
func (s *FrameSocket) WriteBatch(ms []ipv6.Message, _ int) (int, error) {
	if len(ms) == 0 {
		return 0, nil
	}
	t := s.route.template(destination(ms[0].Addr), s.probe)
	if t == nil {
		return s.writeKernel(ms)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.iface != t.ifindex {
		ifc, err := net.InterfaceByIndex(int(t.ifindex))
		if err != nil {
			return 0, err
		}
		if err := bindBPF(s.fd, ifc.Name); err != nil {
			s.route.invalidate()
			return 0, err
		}
		s.iface = t.ifindex
	}
	for i, m := range ms {
		if len(m.Buffers) != 1 || len(m.Buffers[0]) > maxICMPLen {
			return i, errors.New("a frame takes one echo request of at most 64 bytes")
		}
		f := s.frame[:0]
		if cap(f) < frameHead+maxICMPLen {
			f = make([]byte, 0, frameHead+maxICMPLen)
		}
		f = f[:frameHead+len(m.Buffers[0])]
		s.frame = f
		t.frame(f, destination(m.Addr), m.Buffers[0])
		if _, err := unix.Write(s.fd, f); err != nil {
			if errors.Is(err, unix.ENXIO) || errors.Is(err, unix.ENETDOWN) {
				s.route.invalidate()
			}
			return i, err
		}
	}
	return len(ms), nil
}

func (s *FrameSocket) writeKernel(ms []ipv6.Message) (int, error) {
	for i, m := range ms {
		dst := m.Addr
		if s.dgram {
			d := destination(dst)
			dst = &net.UDPAddr{IP: d[:]}
		}
		if _, err := s.kernel.WriteTo(m.Buffers[0], dst); err != nil {
			return i, err
		}
	}
	return len(ms), nil
}

func (s *FrameSocket) WriteTo(b []byte, dst net.Addr) (int, error) {
	if _, err := s.WriteBatch([]ipv6.Message{{Buffers: [][]byte{b}, Addr: dst}}, 0); err != nil {
		return 0, err
	}
	return len(b), nil
}

// ReadFrom reads the kernel fallback socket; replies to frames reach no socket.
func (s *FrameSocket) ReadFrom(b []byte) (int, net.Addr, error) { return s.kernel.ReadFrom(b) }

func (s *FrameSocket) SetReadDeadline(t time.Time) error { return s.kernel.SetReadDeadline(t) }

func (s *FrameSocket) Close() error {
	_ = unix.Close(s.fd)
	_ = s.probe.Close()
	return s.kernel.Close()
}

// WarnIfConntrack does nothing: only Linux's netfilter takes an entry per ping.
func WarnIfConntrack() {}
