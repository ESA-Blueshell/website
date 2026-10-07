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
	conn, window, err := openSocket(dryRun)
	if err != nil {
		return err
	}

	preview := &previewHolder{}
	var poller *apipaint.Poller
	sender := paint.NewSender(conn, nil, window, func() paint.Settings {
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

	client := apipaint.NewClient(env("PINGER_API_URL", "http://localhost:8080"))
	poller = apipaint.NewPoller(client, 2*time.Second, sender, preview.set)
	go poller.Run(ctx)
	go sender.Run(ctx)
	go persistStats(ctx, store, sender)

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

// openSocket opens the raw ICMPv6 socket, which needs NET_RAW. A dry run swaps in a socket that
// sends nothing and opens the window, so the page can be tried before the event.
func openSocket(dryRun bool) (paint.Conn, paint.Window, error) {
	if dryRun {
		slog.Warn("dry run: nothing leaves this host")
		return discard{}, paint.Window{Start: time.Time{}, End: time.Date(9999, 1, 1, 0, 0, 0, 0, time.UTC)}, nil
	}
	conn, err := icmp.ListenPacket("ip6:ipv6-icmp", "::")
	if err != nil {
		return nil, paint.Window{}, err
	}
	return conn, paint.EventWindow(), nil
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
