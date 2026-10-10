//go:build darwin

package paint

import (
	"os"
	"strings"
	"testing"
	"time"

	"golang.org/x/net/bpf"
	"golang.org/x/sys/unix"
)

// BenchmarkFrameWrite writes echo requests through /dev/bpf, one frame a write and in batches.
// It needs root and a feth pair, so nothing leaves the machine:
//
//	sudo ifconfig feth0 create && sudo ifconfig feth1 create && sudo ifconfig feth0 peer feth1
//	sudo ifconfig feth0 up && sudo ifconfig feth1 up
//	sudo PINGER_BENCH_IFACE=feth0 go test -run x -bench FrameWrite ./paint/
func BenchmarkFrameWrite(b *testing.B) {
	iface := os.Getenv("PINGER_BENCH_IFACE")
	if !strings.HasPrefix(iface, "feth") {
		b.Skip("set PINGER_BENCH_IFACE to a feth interface and run as root")
	}
	for _, mode := range []struct {
		name  string
		batch bool
	}{{"each", false}, {"batch", true}} {
		b.Run(mode.name, func(b *testing.B) {
			fd, err := openBPF()
			if err != nil {
				b.Fatal(err)
			}
			defer unix.Close(fd)
			if err := setFilter(fd, []bpf.Instruction{bpf.RetConstant{Val: 0}}); err != nil {
				b.Fatal(err)
			}
			if err := bindBPF(fd, iface); err != nil {
				b.Fatal(err)
			}
			if mode.batch {
				if err := unix.IoctlSetPointerInt(fd, biocSBatchWrite, 1); err != nil {
					b.Fatal(err)
				}
			}
			tpl := testTemplate(&testing.T{})
			route := &frameRoute{}
			route.current.Store(tpl)
			s := &FrameSocket{fd: fd, iface: tpl.ifindex, route: route, batch: mode.batch}
			ms := pixelMessages(batch)
			b.ResetTimer()
			wall, cpu := time.Now(), cpuTime()
			sent := 0
			for sent < b.N {
				n, err := s.WriteBatch(ms, 0)
				if err != nil {
					b.Fatal(err)
				}
				sent += n
			}
			elapsed := time.Since(wall)
			b.ReportMetric(100*float64(cpuTime()-cpu)/float64(elapsed), "cpu%")
			b.ReportMetric(float64(sent)/elapsed.Seconds(), "pps")
		})
	}
}
