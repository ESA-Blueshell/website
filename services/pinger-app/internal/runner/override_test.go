package runner

import (
	"testing"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/paint"

	"github.com/ESA-Blueshell/website/services/pinger-app/internal/headroom"
)

// The app's chosen rate must win over the paint job's rate, while the prefix the api owns is kept,
// so the member sets how hard their own machine pings without moving where it paints.
func TestApplyRateOverridesPaintJobRate(t *testing.T) {
	prefix, err := canvas.ParsePrefix("2001:db8::/64")
	if err != nil {
		t.Fatalf("parse prefix: %v", err)
	}

	r := Runner{headroom: headroom.New()}
	r.headroom.SetEnabled(false)
	r.rate.Store(500)

	got := r.applyRate(paint.Settings{Prefix: prefix, RatePPS: 200_000})
	if got.RatePPS != 500 {
		t.Errorf("RatePPS = %d, want the app rate 500", got.RatePPS)
	}
	if got.Prefix != prefix {
		t.Errorf("Prefix = %v, want the api prefix kept %v", got.Prefix, prefix)
	}

	r.rate.Store(42)
	if got := r.applyRate(paint.Settings{RatePPS: 1000}); got.RatePPS != 42 {
		t.Errorf("RatePPS = %d, want the updated app rate 42", got.RatePPS)
	}
}

// The headroom ceiling holds the chosen rate lower while it is on, and never above the choice.
func TestApplyRateHoldsTheRateUnderTheHeadroomCeiling(t *testing.T) {
	r := Runner{headroom: headroom.New()}
	r.rate.Store(100_000)
	held := r.applyRate(paint.Settings{}).RatePPS
	if held >= 100_000 || held <= 0 {
		t.Errorf("RatePPS = %d, want it held below the chosen 100000", held)
	}

	r.rate.Store(10)
	if got := r.applyRate(paint.Settings{}).RatePPS; got != 10 {
		t.Errorf("RatePPS = %d, want the chosen 10 under a higher ceiling", got)
	}
}
