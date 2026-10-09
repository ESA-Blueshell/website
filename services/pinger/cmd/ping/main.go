// Command ping is the standalone helper: it paints the image the association chose onto the
// SNTPings canvas from the machine it runs on, so members can help from their own connections. It
// asks the api what to paint — the prefix, rate, image and box — so there is nothing to configure:
// run it and it follows whatever an admin set on the main site. It needs IPv6. It opens an
// unprivileged ICMP socket where it can (macOS, most Linux) and falls back to a raw socket
// otherwise (Windows needs admin). Be considerate: SNTPings bans prefixes that ping too hard.
package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"runtime"
	"strconv"
	"strings"
	"syscall"
	"time"

	"golang.org/x/net/icmp"

	"github.com/ESA-Blueshell/website/services/pinger/apipaint"
	"github.com/ESA-Blueshell/website/services/pinger/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

const defaultServer = "https://esa-blueshell.nl/api"

func main() {
	server := flag.String("server", defaultServer, "where to read the paint job from")
	rate := flag.Int("rate", 0, "packets a second; 0 follows what the association set, keep any override considerate")
	sector := flag.String("sector", "1/1", "which slice of the image to paint, as N/M, so helpers can split the work without overlapping")
	prefix := flag.String("prefix", "", "override the announced /64 (normally read from the server)")
	anytime := flag.Bool("anytime", false, "send even outside the event window (for a local dry run against a test prefix)")
	flag.Parse()

	if err := run(*server, *rate, *sector, *prefix, *anytime); err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		os.Exit(1)
	}
}

func run(server string, rate int, sectorArg, prefixArg string, anytime bool) error {
	if rate < 0 || rate > 200_000 {
		return fmt.Errorf("rate must be from 1 to 200000, or 0 to follow the server")
	}
	n, m, err := parseSector(sectorArg)
	if err != nil {
		return err
	}
	var prefixOverride canvas.Prefix
	if prefixArg != "" {
		if prefixOverride, err = canvas.ParsePrefix(prefixArg); err != nil {
			return err
		}
	}

	socks, datagram, err := openICMP()
	if err != nil {
		return err
	}
	defer paint.CloseSockets(socks)

	window := paint.EventWindow()
	if anytime {
		window = paint.Window{Start: time.Time{}, End: time.Date(9999, 1, 1, 0, 0, 0, 0, time.UTC)}
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	var poller *apipaint.Poller
	settings := func() paint.Settings {
		cur := paint.Settings{}
		if poller != nil {
			cur = poller.Current()
		}
		if rate > 0 {
			cur.RatePPS = rate
		}
		if prefixArg != "" {
			cur.Prefix = prefixOverride
		}
		return cur
	}
	sender := paint.NewSender(paint.Conns(socks), nil, window, settings)
	if datagram {
		sender.UseDatagramAddresses()
	}
	poller = apipaint.NewPoller(apipaint.NewClient(server), 5*time.Second, sectorSink{sender: sender, n: n, m: m}, nil)

	fmt.Printf("Following the paint job at %s (sector %d/%d).\n", server, n, m)
	fmt.Println("Press Ctrl+C to stop. Please be considerate — do not raise the rate to abuse the network.")
	go poller.Run(ctx)
	go report(ctx, sender)
	sender.Run(ctx)
	return nil
}

// sectorSink keeps only this helper's slice of the placed image before handing it to the sender,
// so N helpers each paint a different N/M without overlapping.
type sectorSink struct {
	sender *paint.Sender
	n, m   int
}

func (s sectorSink) SetPixels(px []canvas.Pixel) { s.sender.SetPixels(sectorPixels(px, s.n, s.m)) }

// openICMP opens frame sockets where the helper has CAP_NET_RAW on Linux, else an unprivileged ICMP
// datagram socket where the OS allows it, else a raw socket. The bool reports whether the datagram
// socket won, so the sender knows to address it with UDP destinations.
func openICMP() ([]socket, bool, error) {
	if frames, err := paint.OpenFrameSockets(false, nil); err == nil {
		out := make([]socket, len(frames))
		for i, f := range frames {
			out[i] = f
		}
		return out, false, nil
	}
	paint.WarnIfConntrack()
	if c, err := listenAll("udp6"); err == nil {
		return c, true, nil
	}
	c, err := listenAll("ip6:ipv6-icmp")
	if err != nil {
		return nil, false, fmt.Errorf("could not open an ICMPv6 socket (need IPv6; on Windows run as administrator, "+
			"on Linux either run with sudo or set once: sudo sysctl -w net.ipv6.ping_group_range=\"0 2147483647\"): %w", err)
	}
	return c, false, nil
}

type socket interface {
	paint.Conn
	Close() error
}

// listenAll opens one socket per send worker: workers sharing a socket queue behind each other.
func listenAll(network string) ([]socket, error) {
	return paint.OpenSockets(func() (socket, error) { return listen(network) })
}

// listen opens a FreshSocket on macOS, where a long-lived socket that reaches many destinations
// wedges, and a plain socket elsewhere.
func listen(network string) (socket, error) {
	if runtime.GOOS == "darwin" {
		return paint.ListenFresh(network, nil)
	}
	return icmp.ListenPacket(network, "::")
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
	out := make([]canvas.Pixel, 0, len(all)/m+1)
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
