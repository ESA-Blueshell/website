package settings

import (
	"context"
	"os"
	"testing"
	"time"

	"github.com/valkey-io/valkey-go"

	"github.com/ESA-Blueshell/website/services/pinger/internal/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/internal/paint"
)

// valkeyAddr is the Valkey these tests talk to, from PINGER_TEST_VALKEY. CI runs a Valkey service
// and sets it; locally, run one and point this at it, or the Valkey-backed tests skip. Keeping the
// server out of the module means no test-only dependency.
func valkeyAddr(t *testing.T) string {
	t.Helper()
	addr := os.Getenv("PINGER_TEST_VALKEY")
	if addr == "" {
		t.Skip("set PINGER_TEST_VALKEY to a Valkey address to run the Valkey-backed tests")
	}
	// One shared Valkey, so wipe it for a clean slate each call.
	c, err := valkey.NewClient(valkey.ClientOption{InitAddress: []string{addr}, DisableCache: true, ForceSingleClient: true})
	if err != nil {
		t.Fatal(err)
	}
	defer c.Close()
	if err := c.Do(context.Background(), c.B().Flushall().Build()).Error(); err != nil {
		t.Fatal(err)
	}
	return addr
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
	want := paint.Settings{RatePPS: 128}
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

func TestARateAboveTheCapIsReadAsTheCap(t *testing.T) {
	addr := valkeyAddr(t)
	ctx := context.Background()
	if err := open(t, addr).Save(ctx, paint.Settings{RatePPS: 10_000_000}); err != nil {
		t.Fatal(err)
	}

	got, err := open(t, addr).Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if got.RatePPS != paint.MaxRatePPS || paint.MaxRatePPS != 200_000 {
		t.Fatalf("rate %d, want 200,000", got.RatePPS)
	}
}

func TestStatsRoundTripAndFreshZeroes(t *testing.T) {
	addr := valkeyAddr(t)
	ctx := context.Background()
	want := Stats{Sent: 123, Passes: 4, Errors: 2}
	if err := open(t, addr).SaveStats(ctx, want); err != nil {
		t.Fatal(err)
	}
	got, err := open(t, addr).LoadStats(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if got != want {
		t.Fatalf("got %+v, want %+v", got, want)
	}
	if z, err := open(t, valkeyAddr(t)).LoadStats(ctx); err != nil || z != (Stats{}) {
		t.Fatalf("fresh gave %+v, %v", z, err)
	}
}
