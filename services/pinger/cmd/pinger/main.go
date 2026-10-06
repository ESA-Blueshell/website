// Command pinger paints the Blueshell logo on the SNTPings canvas and serves its progress page.
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
	"syscall"
	"time"

	"golang.org/x/net/icmp"

	pinger "github.com/ESA-Blueshell/website/services/pinger"
	"github.com/ESA-Blueshell/website/services/pinger/internal/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/internal/paint"
	"github.com/ESA-Blueshell/website/services/pinger/internal/settings"
	"github.com/ESA-Blueshell/website/services/pinger/internal/web"
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

	src, err := png.Decode(bytes.NewReader(pinger.Logo))
	if err != nil {
		return err
	}
	placed := canvas.Place(src, 600)
	var preview bytes.Buffer
	if err := png.Encode(&preview, placed.Image); err != nil {
		return err
	}

	store, err := settings.Open(env("PINGER_VALKEY", "localhost:6379"))
	if err != nil {
		return err
	}
	defer store.Close()
	watcher := settings.NewWatcher(store, time.Second)
	go watcher.Run(ctx)

	dryRun := os.Getenv("PINGER_DRY_RUN") != ""
	conn, window, err := sendPath(dryRun)
	if err != nil {
		return err
	}
	sender := paint.NewSender(conn, placed.Pixels, window, watcher.Current)
	go sender.Run(ctx)

	var auth web.Auth = web.NewAPIAuth(env("PINGER_API_URL", "http://localhost:8080"), env("PINGER_HOST", "pings.esa-blueshell.nl"))
	if dryRun {
		auth = everyoneIsAdmin{}
	}
	handler := web.NewServer(sender, watcher, auth, preview.Bytes())
	if dryRun {
		handler = asAdmin(handler)
	}
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
	slog.Info("pinger up", "listen", srv.Addr, "pixels", len(placed.Pixels))
	if err := srv.ListenAndServe(); !errors.Is(err, http.ErrServerClosed) {
		return err
	}
	return nil
}

// sendPath opens the raw ICMPv6 socket, which needs NET_RAW. A dry run swaps in a socket that
// sends nothing and opens the window, so the page can be tried before the event.
func sendPath(dryRun bool) (paint.Conn, paint.Window, error) {
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

type discard struct{}

// everyoneIsAdmin and asAdmin stand in for forward-auth on a dry run, which has no api to ask.
type everyoneIsAdmin struct{}

func (everyoneIsAdmin) IsAdmin(*http.Request) (bool, error) { return true, nil }

func asAdmin(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		r.Header.Set("X-User-Groups", "ADMIN")
		next.ServeHTTP(w, r)
	})
}

func (discard) WriteTo(b []byte, _ net.Addr) (int, error) { return len(b), nil }

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
