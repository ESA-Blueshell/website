// Command pinger paints the image the api holds onto the SNTPings canvas and serves its progress
// page. The prefix, rate, image and its box all come from the api; this process is the sender and
// the public watch page, nothing more.
package main

import (
	"bytes"
	"context"
	"errors"
	"image/png"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"sync/atomic"
	"syscall"
	"time"

	"golang.org/x/net/icmp"

	"github.com/ESA-Blueshell/website/services/pinger/apipaint"
	"github.com/ESA-Blueshell/website/services/pinger/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/internal/settings"
	"github.com/ESA-Blueshell/website/services/pinger/internal/web"
	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

func main() {
	if err := run(); err != nil {
		slog.Error("pinger stopped", "err", err)
		os.Exit(1)
	}
}

func run() error {
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	store, err := settings.Open(env("PINGER_VALKEY", "localhost:6379"))
	if err != nil {
		return err
	}
	defer store.Close()

	dryRun := os.Getenv("PINGER_DRY_RUN") != ""
	conns, window, err := openSocket(dryRun)
	if err != nil {
		return err
	}

	preview := &previewHolder{}
	var poller *apipaint.Poller
	sender := paint.NewSender(conns, nil, window, func() paint.Settings {
		if poller == nil {
			return paint.Settings{}
		}
		return poller.Current()
	})
	if st, err := store.LoadStats(ctx); err != nil {
		slog.Warn("load stats", "err", err)
	} else {
		sender.Seed(st.Sent, st.Passes, st.Errors)
	}

	apiURL := env("PINGER_API_URL", "http://localhost:8080")
	// The replica id keeps this replica's send counter and paint share apart from the other SiteCie
	// replicas'; it defaults to the hostname, which is the pod name in the cluster and is stable
	// across a restart of the same replica. The token is a secret read from the environment; an
	// empty one leaves the reporter idle and this replica painting every pixel.
	replicaID := env("PINGER_REPLICA_ID", hostnameOr("sitecie-replica"))
	serviceToken := os.Getenv("PINGER_REPORT_SERVICE_TOKEN")

	shared := apipaint.NewShareFilter(sender)
	client := apipaint.NewClient(apiURL)
	poller = apipaint.NewPoller(client, 2*time.Second, shared, preview.set)
	poller.SetOffsetSink(sender)
	go poller.Run(ctx)
	go apipaint.NewShareStream(apiURL, replicaID, apipaint.ServiceToken(serviceToken)).Run(ctx, shared.SetShare)
	go sender.Run(ctx)
	go persistStats(ctx, store, sender)

	reporter := apipaint.NewReporter(apiURL, serviceToken, replicaID, 10*time.Second, func() apipaint.ReportStats {
		s := sender.Snapshot()
		return apipaint.ReportStats{Online: s.State == paint.Running, PPS: int(s.ActualPPS), Sent: s.Sent, Errors: s.Errors}
	})
	go reporter.Run(ctx)

	handler := web.NewServer(sender, poller.Current, preview.get)
	srv := &http.Server{
		Addr:              env("PINGER_LISTEN", ":8090"),
		Handler:           handler,
		ReadHeaderTimeout: 5 * time.Second,
	}
	go func() {
		<-ctx.Done()
		shutdown, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = srv.Shutdown(shutdown)
	}()
	slog.Info("pinger up", "listen", srv.Addr)
	if err := srv.ListenAndServe(); !errors.Is(err, http.ErrServerClosed) {
		return err
	}
	return nil
}

// previewHolder keeps the PNG of the image as it lands, swapped whenever the poller places a new
// one, so the page can draw it. It holds nil when there is no image.
type previewHolder struct {
	png atomic.Pointer[[]byte]
}

func (h *previewHolder) get() []byte {
	if p := h.png.Load(); p != nil {
		return *p
	}
	return nil
}

func (h *previewHolder) set(placed canvas.Placement) {
	if placed.Image == nil {
		h.png.Store(nil)
		return
	}
	var buf bytes.Buffer
	if err := png.Encode(&buf, placed.Image); err != nil {
		slog.Warn("encode preview", "err", err)
		return
	}
	b := buf.Bytes()
	h.png.Store(&b)
}

// openSocket opens frame sockets on Linux, falling back to the raw ICMPv6 socket; both need
// NET_RAW. The frames skip the qdisc: this host sends nothing else that needs fair queueing. A dry
// run swaps in a socket that sends nothing and opens the window, so the page can be tried before
// the event.
func openSocket(dryRun bool) ([]paint.Conn, paint.Window, error) {
	if dryRun {
		slog.Warn("dry run: nothing leaves this host")
		return []paint.Conn{discard{}}, paint.Window{Start: time.Time{}, End: time.Date(9999, 1, 1, 0, 0, 0, 0, time.UTC)}, nil
	}
	frames, err := paint.OpenFrameSockets(true, nil)
	if err == nil {
		slog.Info("sending frames on a packet socket")
		return paint.Conns(frames), paint.EventWindow(), nil
	}
	slog.Warn("no packet socket, sending through the raw socket", "err", err)
	paint.WarnIfConntrack()
	socks, err := paint.OpenSockets(func() (*icmp.PacketConn, error) { return icmp.ListenPacket("ip6:ipv6-icmp", "::") })
	if err != nil {
		return nil, paint.Window{}, err
	}
	return paint.Conns(socks), paint.EventWindow(), nil
}

// persistStats writes the running totals to Valkey every few seconds and once more on the way
// out, so a restart resumes the counts. Everything the service keeps lives under the pinger:
// namespace, so `redis-cli --scan --pattern 'pinger:*' | xargs redis-cli del` wipes it all.
func persistStats(ctx context.Context, store *settings.Store, sender *paint.Sender) {
	save := func() {
		s := sender.Snapshot()
		if err := store.SaveStats(context.WithoutCancel(ctx), settings.Stats{Sent: s.Sent, Passes: s.Passes, Errors: s.Errors}); err != nil {
			slog.Warn("save stats", "err", err)
		}
	}
	tick := time.NewTicker(5 * time.Second)
	defer tick.Stop()
	for {
		select {
		case <-ctx.Done():
			save()
			return
		case <-tick.C:
			save()
		}
	}
}

type discard struct{}

func (discard) WriteTo(b []byte, _ net.Addr) (int, error) { return len(b), nil }

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

// hostnameOr is the host's name, which is the pod name in the cluster, or fallback where it cannot
// be read. It is this replica's stable device id for reporting.
func hostnameOr(fallback string) string {
	if h, err := os.Hostname(); err == nil && h != "" {
		return h
	}
	return fallback
}
