//go:build unix

package paint

import (
	"context"
	"net"
	"syscall"
	"testing"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
)

// sinkConn makes one real syscall per send, on a UDP socket connected to a local port nobody
// reads, so the kernel drops the packet cheaply and nothing leaves the host.
type sinkConn struct{ c *net.UDPConn }

func (s sinkConn) WriteTo(b []byte, _ net.Addr) (int, error) { return s.c.Write(b) }

func sinkConns(b *testing.B) []Conn {
	b.Helper()
	sink, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		b.Fatal(err)
	}
	b.Cleanup(func() { _ = sink.Close() })
	_ = sink.SetReadBuffer(1)
	out := make([]Conn, Workers())
	for i := range out {
		c, err := net.DialUDP("udp", nil, sink.LocalAddr().(*net.UDPAddr))
		if err != nil {
			b.Fatal(err)
		}
		b.Cleanup(func() { _ = c.Close() })
		out[i] = sinkConn{c}
	}
	return out
}

type discardConn struct{}

func (discardConn) WriteTo(b []byte, _ net.Addr) (int, error) { return len(b), nil }

func discardConns() []Conn {
	out := make([]Conn, Workers())
	for i := range out {
		out[i] = discardConn{}
	}
	return out
}

func cpuTime() time.Duration {
	var ru syscall.Rusage
	_ = syscall.Getrusage(syscall.RUSAGE_SELF, &ru)
	return time.Duration(ru.Utime.Nano() + ru.Stime.Nano())
}

// benchSend runs a sender until it has sent b.N packets and reports the CPU it burnt as a share of
// one core.
func benchSend(b *testing.B, conns []Conn, ratePPS int) {
	p, err := canvas.ParsePrefix("2001:db8:b317:a000::/64")
	if err != nil {
		b.Fatal(err)
	}
	px := make([]canvas.Pixel, 450_000)
	for i := range px {
		px[i] = canvas.Pixel{X: uint16(i % canvas.Width), Y: uint16(i / canvas.Width), A: 0xff}
	}
	settings := func() Settings { return Settings{Prefix: p, RatePPS: ratePPS, Enabled: true} }
	b.ReportAllocs()
	s := NewSender(conns, px, open, settings)
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	b.ResetTimer()
	wall, cpu := time.Now(), cpuTime()
	go func() { s.Run(ctx); close(done) }()
	for s.sent.Load() < uint64(b.N) {
		time.Sleep(time.Millisecond)
	}
	cancel()
	<-done
	b.StopTimer()
	elapsed := time.Since(wall)
	b.ReportMetric(100*float64(cpuTime()-cpu)/float64(elapsed), "cpu%")
	b.ReportMetric(float64(s.sent.Load())/elapsed.Seconds(), "pps")
}

func BenchmarkSendDiscard(b *testing.B) { benchSend(b, discardConns(), 1<<40) }

func BenchmarkSendSink(b *testing.B) { benchSend(b, sinkConns(b), 1<<40) }

func BenchmarkSendSinkAtRate(b *testing.B) {
	for _, r := range []struct {
		name string
		pps  int
	}{{"100k", 100_000}, {"500k", 500_000}, {"1M", 1_000_000}} {
		b.Run(r.name, func(b *testing.B) { benchSend(b, sinkConns(b), r.pps) })
	}
}

func BenchmarkSendDiscardAtRate(b *testing.B) {
	for _, r := range []struct {
		name string
		pps  int
	}{{"100k", 100_000}, {"500k", 500_000}, {"1M", 1_000_000}, {"2M", 2_000_000}} {
		b.Run(r.name, func(b *testing.B) { benchSend(b, discardConns(), r.pps) })
	}
}
