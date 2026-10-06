// Command ping is the standalone helper: it paints the Blueshell logo onto the SNTPings canvas
// from the machine it runs on, so association members can help from their own connections. It
// needs IPv6. It opens an unprivileged ICMP socket where it can (macOS, most Linux) and falls
// back to a raw socket otherwise (Windows needs admin). Be considerate: SNTPings bans prefixes
// that ping too hard.
package main

import (
	"bytes"
	"context"
	"flag"
	"fmt"
	"image/png"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	"golang.org/x/net/icmp"

	pinger "github.com/ESA-Blueshell/website/services/pinger"
	"github.com/ESA-Blueshell/website/services/pinger/internal/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/internal/paint"
)

func main() {
	prefix := flag.String("prefix", "", "the /64 SNTPings announces at the event, e.g. 2001:db8:b317:a000::/64")
	rate := flag.Int("rate", 128, "packets a second to send; keep it considerate")
	sector := flag.String("sector", "1/1", "which slice of the logo to paint, as N/M, so helpers can split the work without overlapping")
	anytime := flag.Bool("anytime", false, "send even outside the event window (for a local dry run against a test prefix)")
	flag.Parse()

	if err := run(*prefix, *rate, *sector, *anytime); err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		os.Exit(1)
	}
}

func run(prefixArg string, rate int, sectorArg string, anytime bool) error {
	if prefixArg == "" {
		return fmt.Errorf("give the announced prefix with -prefix (see https://pings.utwente.io/)")
	}
	prefix, err := canvas.ParsePrefix(prefixArg)
	if err != nil {
		return err
	}
	if rate < 1 || rate > 200_000 {
		return fmt.Errorf("rate must be from 1 to 200000")
	}
	n, m, err := parseSector(sectorArg)
	if err != nil {
		return err
	}

	src, err := png.Decode(bytes.NewReader(pinger.Logo))
	if err != nil {
		return err
	}
	pixels := sectorPixels(canvas.PlaceLogo(src).Pixels, n, m)

	conn, datagram, err := openICMP()
	if err != nil {
		return err
	}
	defer conn.Close()

	window := paint.EventWindow()
	if anytime {
		window = paint.Window{Start: time.Time{}, End: time.Date(9999, 1, 1, 0, 0, 0, 0, time.UTC)}
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	settings := func() paint.Settings { return paint.Settings{Prefix: prefix, RatePPS: rate} }
	sender := paint.NewSender(conn, pixels, window, settings)
	if datagram {
		sender.UseDatagramAddresses()
	}

	fmt.Printf("Painting %d pixels (sector %d/%d) at up to %d packets a second.\n", len(pixels), n, m, rate)
	fmt.Println("Prefix:", prefix)
	fmt.Println("Press Ctrl+C to stop. Please be considerate — do not raise the rate to abuse the network.")
	go report(ctx, sender)
	sender.Run(ctx)
	return nil
}

// openICMP opens an unprivileged ICMP datagram socket where the OS allows it, and falls back to
// a raw socket otherwise. The bool reports whether the datagram socket won, so the sender knows
// to address it with UDP destinations.
func openICMP() (*icmp.PacketConn, bool, error) {
	if c, err := icmp.ListenPacket("udp6", "::"); err == nil {
		return c, true, nil
	}
	c, err := icmp.ListenPacket("ip6:ipv6-icmp", "::")
	if err != nil {
		return nil, false, fmt.Errorf("could not open an ICMPv6 socket (need IPv6; on Windows run as administrator, "+
			"on Linux either run with sudo or set once: sudo sysctl -w net.ipv6.ping_group_range=\"0 2147483647\"): %w", err)
	}
	return c, false, nil
}

// report prints progress once a second so a helper can see it is working.
func report(ctx context.Context, sender *paint.Sender) {
	tick := time.NewTicker(time.Second)
	defer tick.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
			s := sender.Snapshot()
			line := fmt.Sprintf("\r%-9s  %s pps  sent %-12s passes %-6d errors %d",
				s.State, thousands(s.ActualPPS), thousands(s.Sent), s.Passes, s.Errors)
			if s.Failing {
				line += "  (sending is failing — check your IPv6)"
			}
			fmt.Print(line)
		}
	}
}

// sectorPixels keeps every m-th pixel starting at n-1, so N helpers each paint a different slice.
func sectorPixels(all []canvas.Pixel, n, m int) []canvas.Pixel {
	if m <= 1 {
		return all
	}
	out := all[:0:0]
	for i, px := range all {
		if i%m == n-1 {
			out = append(out, px)
		}
	}
	return out
}

func parseSector(s string) (n, m int, err error) {
	parts := strings.SplitN(s, "/", 2)
	if len(parts) != 2 {
		return 0, 0, fmt.Errorf("sector must look like N/M, got %q", s)
	}
	n, err1 := strconv.Atoi(strings.TrimSpace(parts[0]))
	m, err2 := strconv.Atoi(strings.TrimSpace(parts[1]))
	if err1 != nil || err2 != nil || m < 1 || n < 1 || n > m {
		return 0, 0, fmt.Errorf("sector must be N/M with 1 <= N <= M, got %q", s)
	}
	return n, m, nil
}

func thousands(n uint64) string {
	s := strconv.FormatUint(n, 10)
	for i := len(s) - 3; i > 0; i -= 3 {
		s = s[:i] + "," + s[i:]
	}
	return s
}
