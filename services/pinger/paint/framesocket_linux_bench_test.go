//go:build linux

package paint

import (
	"context"
	"fmt"
	"net"
	"os"
	"strconv"
	"strings"
	"testing"
	"time"

	"golang.org/x/net/ipv6"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
)

// The frame benchmarks send real frames, so they need what TestFrameMatchesKernel needs: a dummy
// Ethernet link the test prefix routes over, where every frame is dropped on the host.
type sendPath struct{ ring, vnet, qdisc bool }

func openBenchFrames(b *testing.B, path sendPath) []*FrameSocket {
	b.Helper()
	if os.Getenv("PINGER_FRAMES_E2E") == "" {
		b.Skip("set PINGER_FRAMES_E2E and route 2001:db8:5747:5055::/64 over a dummy link to run")
	}
	useTxRing, ringVnet = path.ring, path.vnet
	defer func() { useTxRing, ringVnet = true, true }()
	socks, err := OpenFrameSockets(!path.qdisc, nil)
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

// BenchmarkSendFramesShallow paces the sender into tap0, whose kernel ring holds txqueuelen frames
// and which a reader empties at a fixed rate the way a NIC drains its TX ring, and reports how many
// of the frames the sender counted arrived. PINGER_DRAIN_COUNT names the file the reader keeps its
// count in. Run with -benchtime=1x: each case runs for a fixed six seconds.
func BenchmarkSendFramesShallow(b *testing.B) {
	countFile := os.Getenv("PINGER_DRAIN_COUNT")
	if countFile == "" {
		b.Skip("set PINGER_DRAIN_COUNT to a tap reader's count file to run")
	}
	paths := []struct {
		name string
		path sendPath
	}{
		{"sendmmsg", sendPath{}},
		{"txring", sendPath{ring: true, vnet: true}},
		{"sendmmsg-qdisc", sendPath{qdisc: true}},
		{"txring-qdisc", sendPath{ring: true, vnet: true, qdisc: true}},
	}
	for _, rate := range []int{30_000, 200_000} {
		for _, p := range paths {
			b.Run(fmt.Sprintf("%s/%dk", p.name, rate/1000), func(b *testing.B) {
				benchShallow(b, openBenchFrames(b, p.path), rate, countFile)
			})
		}
	}
}

func benchShallow(b *testing.B, socks []*FrameSocket, rate int, countFile string) {
	prefix, _ := canvas.ParsePrefix("2001:db8:b317:a000::/64")
	px := make([]canvas.Pixel, 450_000)
	for i := range px {
		px[i] = canvas.Pixel{X: uint16(i % canvas.Width), Y: uint16(i / canvas.Width % canvas.Height), A: 0xff}
	}
	s := NewSender(Conns(socks), px, open, func() Settings { return Settings{Prefix: prefix, RatePPS: rate, Enabled: true} })
	readCount := func() uint64 {
		raw, _ := os.ReadFile(countFile)
		n, _ := strconv.ParseUint(strings.TrimSpace(string(raw)), 10, 64)
		return n
	}
	delivered0, cpu0, wall := readCount(), cpuTime(), time.Now()
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	go func() { s.Run(ctx); close(done) }()
	time.Sleep(6 * time.Second)
	cancel()
	<-done
	elapsed, cpu := time.Since(wall), cpuTime()-cpu0
	// Frames still queued in the kernel leave within a few ring drains.
	time.Sleep(500 * time.Millisecond)
	sent, delivered := s.sent.Load(), readCount()-delivered0
	b.ReportMetric(float64(sent)/elapsed.Seconds(), "counted-pps")
	b.ReportMetric(float64(delivered)/elapsed.Seconds(), "delivered-pps")
	b.ReportMetric(100*(float64(sent)-float64(delivered))/max(1, float64(sent)), "lost%")
	b.ReportMetric(100*float64(cpu)/float64(elapsed), "cpu%")
	b.ReportMetric(float64(delivered)/cpu.Seconds()/1000, "kdelivered/cpu-s")
}
