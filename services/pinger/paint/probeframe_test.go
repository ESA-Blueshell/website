package paint

import (
	"encoding/binary"
	"net/netip"
	"testing"
)

func probeFrame(dst netip.Addr, nonce []byte) []byte {
	f := make([]byte, 14, 14+40+8+len(nonce))
	binary.BigEndian.PutUint16(f[12:], 0x86dd)
	ip := make([]byte, 40)
	ip[0], ip[6], ip[7] = 0x60, 58, 64
	binary.BigEndian.PutUint16(ip[4:], uint16(8+len(nonce)))
	copy(ip[24:], dst.AsSlice())
	f = append(f, ip...)
	f = append(f, 128, 0, 0, 0, 0x12, 0x34, 0, 1)
	return append(f, nonce...)
}

func TestIsProbeFrameNeedsDestinationAndNonce(t *testing.T) {
	dst := netip.MustParseAddr("2001:610:5ea:221e:f000::1")
	nonce := []byte{1, 2, 3, 4, 5, 6, 7, 8}
	if !isProbeFrame(probeFrame(dst, nonce), dst.As16(), nonce) {
		t.Fatal("our own probe does not match")
	}
	if isProbeFrame(probeFrame(dst, []byte{1, 2, 3, 4, 5, 6, 7, 9}), dst.As16(), nonce) {
		t.Fatal("matched another nonce")
	}
	if isProbeFrame(probeFrame(netip.MustParseAddr("2001:db8::1"), nonce), dst.As16(), nonce) {
		t.Fatal("matched another destination")
	}
	if isProbeFrame(probeFrame(dst, nonce)[:60], dst.As16(), nonce) {
		t.Fatal("matched a truncated frame")
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
	a, b := probeFrame(netip.MustParseAddr("2001:db8::1"), []byte{9}), []byte{1, 2, 3}
	got := bpfFrames(append(record(b), record(a)...))
	if len(got) != 2 || string(got[0]) != string(b) || string(got[1]) != string(a) {
		t.Fatalf("got %d frames", len(got))
	}
}
