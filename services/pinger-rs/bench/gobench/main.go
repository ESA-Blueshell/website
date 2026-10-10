//go:build linux

// Command gobench runs the Go sender on frame sockets the way cmd/pinger does, and prints the same
// result line as `pinger-rs --synthetic`. bench/run.sh copies it into the pinger module to build.
package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"syscall"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

func cpu() time.Duration {
	var ru syscall.Rusage
	_ = syscall.Getrusage(syscall.RUSAGE_SELF, &ru)
	return time.Duration(ru.Utime.Nano() + ru.Stime.Nano())
}

func main() {
	pixels := flag.Int("synthetic", 450_000, "pixels in the image")
	prefix := flag.String("prefix", "2001:db8:b317:a000::", "the /64")
	rate := flag.Int("rate", 1<<40, "packets a second")
	duration := flag.Duration("duration", 10*time.Second, "how long to measure")
	flag.Parse()

	p, err := canvas.ParsePrefix(*prefix)
	if err != nil {
		log.Fatal(err)
	}
	px := make([]canvas.Pixel, *pixels)
	for i := range px {
		px[i] = canvas.Pixel{X: uint16(i % canvas.Width), Y: uint16(i / canvas.Width % canvas.Height), A: 0xff}
	}
	frames, err := paint.OpenFrameSockets(true, nil)
	if err != nil {
		log.Fatal(err)
	}
	open := paint.Window{End: time.Date(9999, 1, 1, 0, 0, 0, 0, time.UTC)}
	s := paint.NewSender(paint.Conns(frames), px, open, func() paint.Settings {
		return paint.Settings{Prefix: p, RatePPS: *rate, Enabled: true}
	})
	go s.Run(context.Background())
	time.Sleep(time.Second)
	s0, c0, t0 := s.Snapshot().Sent, cpu(), time.Now()
	time.Sleep(*duration)
	st, c1, wall := s.Snapshot(), cpu(), time.Since(t0)
	fmt.Printf("result pps=%.0f cpu=%.1f%% sent=%d errors=%d err=%s\n",
		float64(st.Sent-s0)/wall.Seconds(), 100*float64(c1-c0)/float64(wall), st.Sent-s0, st.Errors, st.LastError)
}
