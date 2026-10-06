package settings

import (
	"context"
	"testing"
	"time"

	tcvalkey "github.com/testcontainers/testcontainers-go/modules/valkey"

	"github.com/ESA-Blueshell/website/services/pinger/internal/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/internal/paint"
)

func valkeyAddr(t *testing.T) string {
	t.Helper()
	ctx := context.Background()
	c, err := tcvalkey.Run(ctx, "valkey/valkey:8-alpine")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = c.Terminate(ctx) })
	host, err := c.Host(ctx)
	if err != nil {
		t.Fatal(err)
	}
	port, err := c.MappedPort(ctx, "6379/tcp")
	if err != nil {
		t.Fatal(err)
	}
	return host + ":" + port.Port()
}

func open(t *testing.T, addr string) *Store {
	t.Helper()
	s, err := Open(addr)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(s.Close)
	return s
}

func TestAFreshValkeyGivesNoPrefixAndTheDefaultRate(t *testing.T) {
	s := open(t, valkeyAddr(t))

	got, err := s.Load(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	want := paint.Settings{RatePPS: 50_000}
	if got != want {
		t.Fatalf("got %+v, want %+v", got, want)
	}
}

func TestSavedSettingsSurviveANewConnection(t *testing.T) {
	addr := valkeyAddr(t)
	ctx := context.Background()
	p, _ := canvas.ParsePrefix("2001:db8:b317:a000::/64")
	want := paint.Settings{Prefix: p, RatePPS: 1234, Paused: true}

	if err := open(t, addr).Save(ctx, want); err != nil {
		t.Fatal(err)
	}
	got, err := open(t, addr).Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if got != want {
		t.Fatalf("got %+v, want %+v", got, want)
	}
}

func TestTheWatcherPicksUpAChangeMadeElsewhere(t *testing.T) {
	addr := valkeyAddr(t)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	w := NewWatcher(open(t, addr), 20*time.Millisecond)
	go w.Run(ctx)

	p, _ := canvas.ParsePrefix("2001:db8:1::")
	if err := open(t, addr).Save(ctx, paint.Settings{Prefix: p, RatePPS: 10}); err != nil {
		t.Fatal(err)
	}

	deadline := time.Now().Add(3 * time.Second)
	for w.Current().Prefix != p {
		if time.Now().After(deadline) {
			t.Fatalf("watcher still holds %+v", w.Current())
		}
		time.Sleep(10 * time.Millisecond)
	}
}

func TestSavingThroughTheWatcherTakesEffectAtOnceAndPersists(t *testing.T) {
	addr := valkeyAddr(t)
	ctx := context.Background()
	w := NewWatcher(open(t, addr), time.Hour)
	p, _ := canvas.ParsePrefix("2001:db8:2::")
	want := paint.Settings{Prefix: p, RatePPS: 99}

	if err := w.Save(ctx, want); err != nil {
		t.Fatal(err)
	}

	if w.Current() != want {
		t.Fatalf("current %+v, want %+v", w.Current(), want)
	}
	if got, _ := open(t, addr).Load(ctx); got != want {
		t.Fatalf("stored %+v, want %+v", got, want)
	}
}
