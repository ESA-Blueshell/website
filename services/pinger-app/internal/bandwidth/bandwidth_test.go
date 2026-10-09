package bandwidth

import (
	"math"
	"testing"
)

// TestBytesPerPacket pins the per-packet size to an independent count: the IPv6 header is 40 bytes,
// the ICMPv6 echo header is 8, and the sender carries no payload, so 48 bytes on the wire.
func TestBytesPerPacket(t *testing.T) {
	const want = 40 + 8
	if BytesPerPacket != want {
		t.Fatalf("BytesPerPacket = %d, want %d", BytesPerPacket, want)
	}
}

func TestMbps(t *testing.T) {
	cases := []struct {
		pps  int
		want float64
	}{
		// Worked out by hand, not with the code's formula: rate * 48 bytes * 8 bits / 1e6.
		{pps: 0, want: 0},
		{pps: -5, want: 0},
		{pps: 200, want: 0.0768},  // 200 * 384 / 1e6
		{pps: 1000, want: 0.384},  // 1000 * 384 / 1e6
		{pps: 200000, want: 76.8}, // 200000 * 384 / 1e6
	}
	for _, c := range cases {
		got := Mbps(c.pps)
		if math.Abs(got-c.want) > 1e-6 {
			t.Errorf("Mbps(%d) = %g, want %g", c.pps, got, c.want)
		}
	}
}
