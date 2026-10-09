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
