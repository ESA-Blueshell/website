//go:build linux

package paint_test

import (
	"context"
	"net"
	"os"
	"testing"
	"time"

	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv6"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

// TestSendPathSustains proves the engine keeps sending at rate to the spread per-pixel addresses a
// real paint job emits, rather than wedging after a handful of packets. It needs an ICMPv6 datagram
// socket (sysctl net.ipv6.ping_group_range) and the documentation prefix routed to loopback so the
// writes land locally and nothing leaves the host. The CI pinger job sets both up and exports
// PINGER_SENDPATH_E2E; without them the test skips, so a developer machine stays green.
//
// This guards the macOS failure mode where the unprivileged datagram socket blocks after ~16
// outstanding sends to unresolved destinations; Linux has no such cap, which is why this is the OS
// the send path is verified on.
func TestSendPathSustains(t *testing.T) {
	if os.Getenv("PINGER_SENDPATH_E2E") == "" {
		t.Skip("set PINGER_SENDPATH_E2E and route 2001:db8:5747:5055::/64 to lo to run the send-path check")
	}
	// A runner with IPv6 disabled cannot open the socket; skip rather than fail, since the check is
	// about the engine not wedging, not about the runner's IPv6 support.
	conn, err := icmp.ListenPacket("udp6", "::")
	if err != nil {
		t.Skipf("no ICMPv6 datagram socket (IPv6 off, or net.ipv6.ping_group_range unset): %v", err)
	}
	defer conn.Close()

	prefix, err := canvas.ParsePrefix("2001:db8:5747:5055::/64")
	if err != nil {
		t.Fatalf("parse prefix: %v", err)
	}

	// Probe once: without the loopback route for the test prefix the send is unreachable. Skip when
	// the environment cannot deliver it, so a runner that forbids the route does not fail the suite.
	echo, _ := (&icmp.Message{Type: ipv6.ICMPTypeEchoRequest, Body: &icmp.Echo{ID: 1, Seq: 1, Data: []byte("probe")}}).Marshal(nil)
	if _, perr := conn.WriteTo(echo, &net.UDPAddr{IP: prefix.Address(canvas.Pixel{}).AsSlice()}); perr != nil {
		t.Skipf("the test prefix is not routable here (route 2001:db8:5747:5055::/64 to lo to run it): %v", perr)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	// Drain replies so a full receive buffer cannot couple back onto sends.
	go func() {
		buf := make([]byte, 1500)
		for ctx.Err() == nil {
			_ = conn.SetReadDeadline(time.Now().Add(200 * time.Millisecond))
			_, _, _ = conn.ReadFrom(buf)
		}
	}()

	pixels := make([]canvas.Pixel, 0, 2000)
	for i := range 2000 {
		pixels = append(pixels, canvas.Pixel{X: uint16(i % 100), Y: uint16(i / 100), R: 10, G: 20, B: 30, A: 255})
	}
	window := paint.Window{Start: time.Time{}, End: time.Date(9999, 1, 1, 0, 0, 0, 0, time.UTC)}
	settings := func() paint.Settings { return paint.Settings{Prefix: prefix, RatePPS: 100_000, Enabled: true} }
	sender := paint.NewSender([]paint.Conn{conn}, pixels, window, settings)
	sender.UseDatagramAddresses()
	go sender.Run(ctx)

	// A sender that wedges stalls near the socket's outstanding cap (~16 on the macOS datagram
	// socket); a healthy one clears whole passes. Require well past a single full pass.
	const want = 5000
	deadline := time.Now().Add(2500 * time.Millisecond)
	var snap paint.Stats
	for time.Now().Before(deadline) {
		snap = sender.Snapshot()
		if snap.Sent >= want {
			break
		}
		time.Sleep(50 * time.Millisecond)
	}
	snap = sender.Snapshot()
	if snap.Sent < want {
		t.Fatalf("send path stalled: sent=%d errors=%d (want sent >= %d); the socket wedged on spread destinations", snap.Sent, snap.Errors, want)
	}
	if snap.Errors > snap.Sent/2 {
		t.Fatalf("too many send errors: sent=%d errors=%d", snap.Sent, snap.Errors)
	}
}
