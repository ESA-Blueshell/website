package settings

import (
	"context"
	"os"
	"testing"

	"github.com/valkey-io/valkey-go"
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
