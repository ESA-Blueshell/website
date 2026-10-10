//go:build linux

package paint

import (
	"bytes"
	"encoding/binary"
	"net"
	"net/netip"
	"os"
	"testing"
	"time"

	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv6"
	"golang.org/x/sys/unix"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
)

func TestFrameChecksumMatchesICMP(t *testing.T) {
	src := netip.MustParseAddr("2001:db8:1::2")
	head := make([]byte, frameHead)
	head[ethHeader] = 0x60
	head[ethHeader+6] = 58
	head[ethHeader+7] = 64
	copy(head[ethHeader+8:], src.AsSlice())
	copy(head[ethHeader+24:], netip.MustParseAddr("2001:db8:5747:5055::").AsSlice())
	tmpl := newTemplate(head, 1)

	for _, dst := range []string{"2001:db8:5747:5055::1", "2001:db8:5747:5055:ffff:ffff:ffff:ffff", "2001:db8:5747:5055:1234:5678:9abc:def0"} {
		d := netip.MustParseAddr(dst)
		msg := icmp.Message{Type: ipv6.ICMPTypeEchoRequest, Body: &icmp.Echo{ID: 0xbeef, Seq: 1}}
		bare, _ := msg.Marshal(nil)
		want, _ := msg.Marshal(icmp.IPv6PseudoHeader(src.AsSlice(), d.AsSlice()))
		out := make([]byte, frameHead+len(bare))
		tmpl.frame(out, d.As16(), bare)
		if got := out[frameHead:]; !bytes.Equal(got, want) {
			t.Errorf("%s: icmp %x, want %x", dst, got, want)
		}
		if got := netip.AddrFrom16([16]byte(out[ethHeader+24 : ethHeader+40])); got != d {
			t.Errorf("destination %s, want %s", got, d)
		}
		if got := binary.BigEndian.Uint16(out[ethHeader+4:]); got != uint16(len(bare)) {
			t.Errorf("payload length %d, want %d", got, len(bare))
		}
	}
}

// TestFrameMatchesKernel sends one echo through the kernel and the same echo as a frame, and
// requires the two frames to match but for the flow label, which the kernel hashes per flow. It
// needs CAP_NET_RAW and the test prefix routed via an Ethernet-type link, so it runs only with
// PINGER_FRAMES_E2E set, for example in a privileged container:
//
//	ip link add dummy0 type dummy && ip link set dummy0 up
//	ip -6 addr add 2001:db8:1::2/64 dev dummy0 nodad
//	ip -6 neigh replace 2001:db8:1::1 lladdr 02:00:00:00:00:01 dev dummy0 nud permanent
//	ip -6 route add 2001:db8:5747:5055::/64 via 2001:db8:1::1 dev dummy0
func TestFrameMatchesKernel(t *testing.T) {
	if os.Getenv("PINGER_FRAMES_E2E") == "" {
		t.Skip("set PINGER_FRAMES_E2E and route 2001:db8:5747:5055::/64 over an Ethernet link to run")
	}
	capture, err := unix.Socket(unix.AF_PACKET, unix.SOCK_RAW, int(htons(unix.ETH_P_ALL)))
	if err != nil {
		t.Fatalf("open capture socket: %v", err)
	}
	defer unix.Close(capture)
	tv := unix.NsecToTimeval((100 * time.Millisecond).Nanoseconds())
	_ = unix.SetsockoptTimeval(capture, unix.SOL_SOCKET, unix.SO_RCVTIMEO, &tv)

	prefix, _ := canvas.ParsePrefix("2001:db8:5747:5055::/64")
	dst := prefix.Address(canvas.Pixel{X: 12, Y: 34, R: 56, G: 78, B: 90, A: 255}).As16()
	echo, _ := (&icmp.Message{Type: ipv6.ICMPTypeEchoRequest, Body: &icmp.Echo{ID: 0x4242, Seq: 7}}).Marshal(nil)

	kernel, err := icmp.ListenPacket("ip6:ipv6-icmp", "::")
	if err != nil {
		t.Fatalf("open raw socket: %v", err)
	}
	defer kernel.Close()
	if _, err := kernel.WriteTo(echo, &net.IPAddr{IP: dst[:]}); err != nil {
		t.Fatalf("kernel send: %v", err)
	}
	want := captureEcho(t, capture, dst, 0x4242)

	socks, err := OpenFrameSockets(false, nil)
	if err != nil {
		t.Fatalf("open frame sockets: %v", err)
	}
	defer CloseSockets(socks)
	if _, err := socks[0].WriteTo(echo, &net.IPAddr{IP: dst[:]}); err != nil {
		t.Fatalf("frame send: %v", err)
	}
	got := captureEcho(t, capture, dst, 0x4242)

	for _, f := range [][]byte{want, got} {
		f[ethHeader+1] &= 0xf0
		f[ethHeader+2], f[ethHeader+3] = 0, 0
	}
	if !bytes.Equal(got, want) {
		t.Fatalf("frame differs from the kernel's\n got %x\nwant %x", got, want)
	}
}

func captureEcho(t *testing.T, fd int, dst [16]byte, id uint16) []byte {
	t.Helper()
	buf := make([]byte, 2048)
	for deadline := time.Now().Add(2 * time.Second); time.Now().Before(deadline); {
		n, from, err := unix.Recvfrom(fd, buf, 0)
		if err != nil {
			continue
		}
		ll := from.(*unix.SockaddrLinklayer)
		if ll.Pkttype == unix.PACKET_OUTGOING && isProbe(buf[:n], dst, id, ll.Hatype) {
			return append([]byte(nil), buf[:n]...)
		}
	}
	t.Fatalf("no echo with id %#x to %s left", id, netip.AddrFrom16(dst))
	return nil
}

func TestCheckOwnFrameRefusesForeignFrames(t *testing.T) {
	ifc, src := ethernetWithIPv6(t)
	frame := func(dstMAC, srcMAC string, src netip.Addr) []byte {
		f := make([]byte, frameHead+8)
		d, _ := net.ParseMAC(dstMAC)
		s, _ := net.ParseMAC(srcMAC)
		copy(f, d)
		copy(f[6:], s)
		copy(f[ethHeader+8:], src.AsSlice())
		return f
	}
	own := ifc.HardwareAddr.String()
	cases := []struct {
		name string
		f    []byte
		ok   bool
	}{
		{"own frame", frame("02:00:00:00:00:01", own, src), true},
		{"someone else's source MAC", frame("02:00:00:00:00:01", "02:00:00:00:00:02", src), false},
		{"multicast next hop", frame("33:33:00:00:00:01", own, src), false},
		{"our own MAC as next hop", frame(own, own, src), false},
		{"someone else's source address", frame("02:00:00:00:00:01", own, netip.MustParseAddr("2001:db8::66")), false},
	}
	for _, c := range cases {
		if err := checkOwnFrame(c.f, ifc.Index); (err == nil) != c.ok {
			t.Errorf("%s: err %v, want ok=%v", c.name, err, c.ok)
		}
	}
}

func ethernetWithIPv6(t *testing.T) (net.Interface, netip.Addr) {
	ifcs, _ := net.Interfaces()
	for _, ifc := range ifcs {
		if len(ifc.HardwareAddr) != 6 || ifc.HardwareAddr.String() == "02:00:00:00:00:01" {
			continue
		}
		addrs, _ := ifc.Addrs()
		for _, a := range addrs {
			if n, ok := a.(*net.IPNet); ok && n.IP.To4() == nil {
				ip, _ := netip.AddrFromSlice(n.IP)
				return ifc, ip
			}
		}
	}
	t.Skip("no Ethernet interface with an IPv6 address")
	return net.Interface{}, netip.Addr{}
}

func TestTxRingFillsSlotsInOrder(t *testing.T) {
	r := &txRing{mem: make([]byte, ringFrames*ringFrameSize), vnet: vnetHdrLen}
	for i := range ringFrames + 2 {
		f, err := r.claim(-1, nil)
		if err != nil {
			t.Fatalf("claim %d: %v", i, err)
		}
		slot := i % ringFrames
		if want := slot*ringFrameSize + ringData + vnetHdrLen; &f[0] != &r.mem[want] {
			t.Fatalf("claim %d: frame space not at slot %d", i, slot)
		}
		r.ready(62)
		hdr := r.mem[slot*ringFrameSize:]
		if got := binary.NativeEndian.Uint32(hdr[4:]); got != vnetHdrLen+62 {
			t.Fatalf("slot %d: tp_len %d, want %d", slot, got, vnetHdrLen+62)
		}
		if got := binary.NativeEndian.Uint16(hdr[ringData+2:]); got != 62 {
			t.Fatalf("slot %d: vnet hdr_len %d, want the whole frame", slot, got)
		}
		if got := *r.status(slot); got != unix.TP_STATUS_SEND_REQUEST {
			t.Fatalf("slot %d: status %d, want send request", slot, got)
		}
		if i == ringFrames-1 {
			// The kernel hands slots back as their frames leave.
			for s := range ringFrames {
				*r.status(s) = unix.TP_STATUS_AVAILABLE
			}
		}
	}
}

func TestTxRingClaimFailsWhileTheKernelHoldsTheSlot(t *testing.T) {
	r := &txRing{mem: make([]byte, ringFrames*ringFrameSize)}
	*r.status(0) = unix.TP_STATUS_SENDING
	if _, err := r.claim(-1, &unix.RawSockaddrLinklayer{}); err == nil {
		t.Fatal("claimed a slot the kernel still holds")
	}
	if r.next != 0 {
		t.Fatalf("next %d, want 0: a failed claim must not skip the slot", r.next)
	}
}

// TestFrameRingSendsEveryFrameInOrder wraps the TX ring several times and requires every frame to
// leave once, in order, with its own destination. Like TestFrameMatchesKernel it needs
// PINGER_FRAMES_E2E and the test prefix routed over an Ethernet link.
func TestFrameRingSendsEveryFrameInOrder(t *testing.T) {
	if os.Getenv("PINGER_FRAMES_E2E") == "" {
		t.Skip("set PINGER_FRAMES_E2E and route 2001:db8:5747:5055::/64 over an Ethernet link to run")
	}
	capture, err := unix.Socket(unix.AF_PACKET, unix.SOCK_RAW, int(htons(unix.ETH_P_ALL)))
	if err != nil {
		t.Fatalf("open capture socket: %v", err)
	}
	defer unix.Close(capture)
	_ = unix.SetsockoptInt(capture, unix.SOL_SOCKET, unix.SO_RCVBUFFORCE, 64<<20)
	tv := unix.NsecToTimeval((200 * time.Millisecond).Nanoseconds())
	_ = unix.SetsockoptTimeval(capture, unix.SOL_SOCKET, unix.SO_RCVTIMEO, &tv)

	// No qdisc bypass: bypassed frames skip the taps the capture reads.
	socks, err := OpenFrameSockets(false, nil)
	if err != nil {
		t.Fatalf("open frame sockets: %v", err)
	}
	defer CloseSockets(socks)
	s := socks[0]
	if s.ring == nil || s.ring.vnet == 0 {
		t.Fatalf("socket has ring %+v, want a TX ring with a virtio header", s.ring)
	}

	const id, total = 0x5151, 3*ringFrames + 5
	echo, _ := (&icmp.Message{Type: ipv6.ICMPTypeEchoRequest, Body: &icmp.Echo{ID: id, Seq: 1}}).Marshal(nil)
	prefix := netip.MustParseAddr("2001:db8:5747:5055::").As16()
	msgs := make([]ipv6.Message, batch)
	for sent := 0; sent < total; {
		n := min(batch, total-sent)
		for i := range n {
			ip := net.IP(append([]byte(nil), prefix[:]...))
			binary.BigEndian.PutUint32(ip[12:], uint32(sent+i))
			msgs[i] = ipv6.Message{Buffers: [][]byte{echo}, Addr: &net.IPAddr{IP: ip}}
		}
		got, err := s.WriteBatch(msgs[:n], 0)
		if err != nil || got != n {
			t.Fatalf("write batch at %d: %d sent, %v", sent, got, err)
		}
		sent += n
	}

	buf := make([]byte, 2048)
	next := uint32(0)
	for deadline := time.Now().Add(5 * time.Second); next < total && time.Now().Before(deadline); {
		n, from, err := unix.Recvfrom(capture, buf, 0)
		if err != nil {
			continue
		}
		f := buf[:n]
		ll, ok := from.(*unix.SockaddrLinklayer)
		if !ok || ll.Pkttype != unix.PACKET_OUTGOING || n < frameHead+8 ||
			[8]byte(f[ethHeader+24:ethHeader+32]) != [8]byte(prefix[:8]) || binary.BigEndian.Uint16(f[frameHead+4:]) != id {
			continue
		}
		if got := binary.BigEndian.Uint32(f[ethHeader+36:]); got != next {
			t.Fatalf("frame %d went to ...:%x", next, got)
		}
		if n != frameHead+len(echo) {
			t.Fatalf("frame %d is %d bytes, want %d", next, n, frameHead+len(echo))
		}
		next++
	}
	if next != total {
		t.Fatalf("%d of %d frames left", next, total)
	}
}
