//go:build linux

package paint

import (
	"net"
	"os"
	"testing"
	"time"

	"golang.org/x/net/ipv6"
)

// The frame benchmarks send real frames, so they need what TestFrameMatchesKernel needs: a dummy
// Ethernet link the test prefix routes over, where every frame is dropped on the host.
type sendPath struct{ ring, vnet bool }

func openBenchFrames(b *testing.B, path sendPath) []*FrameSocket {
	b.Helper()
	if os.Getenv("PINGER_FRAMES_E2E") == "" {
		b.Skip("set PINGER_FRAMES_E2E and route 2001:db8:5747:5055::/64 over a dummy link to run")
	}
	useTxRing, ringVnet = path.ring, path.vnet
	defer func() { useTxRing, ringVnet = true, true }()
	socks, err := OpenFrameSockets(true, nil)
	if err != nil {
		b.Fatal(err)
	}
	b.Cleanup(func() { CloseSockets(socks) })
	if r := socks[0].ring; (r != nil) != path.ring || path.ring && (r.vnet != 0) != path.vnet {
		b.Fatalf("socket %+v, want %+v", r, path)
	}
	return socks
}

func sendPaths(b *testing.B, run func(b *testing.B, path sendPath)) {
	b.Run("sendmmsg", func(b *testing.B) { run(b, sendPath{}) })
	b.Run("txring-paged", func(b *testing.B) { run(b, sendPath{ring: true}) })
	b.Run("txring", func(b *testing.B) { run(b, sendPath{ring: true, vnet: true}) })
}

// BenchmarkFrameSocketWriteBatch is one socket's cost per frame, b.N counting frames.
func BenchmarkFrameSocketWriteBatch(b *testing.B) { sendPaths(b, benchWriteBatch) }

func benchWriteBatch(b *testing.B, path sendPath) {
	s := openBenchFrames(b, path)[0]
	echo := echoRequest()
	msgs := make([]ipv6.Message, batch)
	for i := range msgs {
		ip := net.ParseIP("2001:db8:5747:5055::")
		ip[14], ip[15] = byte(i>>8), byte(i)
		msgs[i] = ipv6.Message{Buffers: [][]byte{echo}, Addr: &net.IPAddr{IP: ip}}
	}
	if _, err := s.WriteBatch(msgs[:1], 0); err != nil {
		b.Fatal(err)
	}
	b.ReportAllocs()
	b.ResetTimer()
	wall, cpu := time.Now(), cpuTime()
	for sent := 0; sent < b.N; {
		n, err := s.WriteBatch(msgs, 0)
		if err != nil {
			b.Fatal(err)
		}
		sent += n
	}
	elapsed := time.Since(wall)
	b.StopTimer()
	b.ReportMetric(100*float64(cpuTime()-cpu)/float64(elapsed), "cpu%")
	b.ReportMetric(float64(b.N)/elapsed.Seconds(), "pps")
}

// BenchmarkSendFrames is the whole sender on frame sockets, unpaced, with its worker scaler.
func BenchmarkSendFrames(b *testing.B) {
	sendPaths(b, func(b *testing.B, path sendPath) {
		benchSendWith(b, Conns(openBenchFrames(b, path)), 1<<40, 450_000, nil)
	})
}
