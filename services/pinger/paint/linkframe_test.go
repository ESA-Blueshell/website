package paint

import (
	"encoding/binary"
	"net"
	"testing"

	"golang.org/x/net/icmp"
)

var (
	routerMAC = []byte{0x50, 0xeb, 0xf6, 0xa1, 0xa3, 0x40}
	ourMAC    = []byte{0x9a, 0x4b, 0x22, 0x97, 0x54, 0x6d}
	ourIP     = net.ParseIP("2001:67c:2564:331::1")
	probeDst  = net.ParseIP("2001:610:5ea:221e:f000::1")
)

// kernelFrame is what the kernel puts on the wire for an echo request to dst.
func kernelFrame(dst net.IP) []byte {
	f := append(append([]byte{}, routerMAC...), ourMAC...)
	f = binary.BigEndian.AppendUint16(f, etherTypeIPv6)
	f = append(f, 0x60|0x02, 0x0a, 0xbc, 0xde) // traffic class 0x20, flow label 0xabcde
	f = binary.BigEndian.AppendUint16(f, 8)
	f = append(f, nextHeaderICMPv6, 64)
	f = append(f, ourIP.To16()...)
	f = append(f, dst.To16()...)
	return append(f, echoRequest()...)
}

func checksumOK(t *testing.T, frame []byte) {
	t.Helper()
	ip := frame[ethHeaderLen:]
	body := ip[ipv6HeaderLen:]
	var sum uint32
	add := func(b []byte) {
		for i := 0; i+1 < len(b); i += 2 {
			sum += uint32(binary.BigEndian.Uint16(b[i:]))
		}
		if len(b)%2 == 1 {
			sum += uint32(b[len(b)-1]) << 8
		}
	}
	add(ip[8:40])
	sum += uint32(len(body)) + nextHeaderICMPv6
	add(body)
	for sum > 0xffff {
		sum = sum>>16 + sum&0xffff
	}
	if sum != 0xffff {
		t.Fatalf("ICMPv6 checksum does not verify: %#x", sum)
	}
}

func TestLinkHeaderAddressesEachFrameAndChecksumsIt(t *testing.T) {
	h, ok := learnHeader(kernelFrame(probeDst), probeDst)
	if !ok {
		t.Fatal("header not learned off the kernel's own frame")
	}
	msg, err := icmp.ParseMessage(nextHeaderICMPv6, echoRequest())
	if err != nil {
		t.Fatal(err)
	}
	dst := net.ParseIP("2001:610:5ea:221e:12:34:5678:9aff")
	f, err := h.frame(nil, msg, dst)
	if err != nil {
		t.Fatal(err)
	}
	if len(f) != linkHeaderLen+EchoRequestSize() {
		t.Fatalf("frame is %d bytes", len(f))
	}
	if !net.IP(f[ethHeaderLen+24 : linkHeaderLen]).Equal(dst) || !net.IP(f[ethHeaderLen+8:ethHeaderLen+24]).Equal(ourIP) {
		t.Fatal("frame not addressed from our address to dst")
	}
	if string(f[:6]) != string(routerMAC) || string(f[6:12]) != string(ourMAC) {
		t.Fatal("frame not sent to the router from our MAC")
	}
	if tc := f[ethHeaderLen]<<4 | f[ethHeaderLen+1]>>4; tc != 0x20 {
		t.Fatalf("traffic class %#x, want the kernel's 0x20", tc)
	}
	if f[ethHeaderLen+1]&0x0f != 0 || f[ethHeaderLen+2] != 0 || f[ethHeaderLen+3] != 0 {
		t.Fatal("frame carries the probe's flow label")
	}
	checksumOK(t, f)
}

func TestLearnHeaderIgnoresOtherPings(t *testing.T) {
	if _, ok := learnHeader(kernelFrame(net.ParseIP("2001:db8::1")), probeDst); ok {
		t.Fatal("learned off a ping to another address")
	}
	if _, ok := learnHeader(kernelFrame(probeDst)[:40], probeDst); ok {
		t.Fatal("learned off a truncated frame")
	}
}

func TestBPFFramesSplitsAlignedRecords(t *testing.T) {
	record := func(frame []byte) []byte {
		hdr := make([]byte, 18)
		binary.NativeEndian.PutUint32(hdr[8:], uint32(len(frame)))
		binary.NativeEndian.PutUint32(hdr[12:], uint32(len(frame)))
		binary.NativeEndian.PutUint16(hdr[16:], 18)
		r := append(hdr, frame...)
		for len(r)%4 != 0 {
			r = append(r, 0)
		}
		return r
	}
	a, b := kernelFrame(probeDst), []byte{1, 2, 3}
	got := bpfFrames(append(record(b), record(a)...))
	if len(got) != 2 || string(got[0]) != string(b) || string(got[1]) != string(a) {
		t.Fatalf("got %d frames", len(got))
	}
}
