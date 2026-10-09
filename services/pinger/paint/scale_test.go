package paint

import (
	"testing"
	"time"
)

func TestScalerDoublesWhileSaturatedAndBehind(t *testing.T) {
	c := newScaler(16)
	now := time.Unix(0, 0)
	if got := c.step(now, 50_000, 0.95, 1_000_000); got != 2 {
		t.Fatalf("got %d workers, want 2", got)
	}
	if got := c.step(now, 100_000, 1.9, 1_000_000); got != 4 {
		t.Fatalf("got %d workers, want 4", got)
	}
}

func TestScalerBacksOffADoublingThatAddedNothing(t *testing.T) {
	c := newScaler(16)
	now := time.Unix(0, 0)
	c.step(now, 100_000, 1, 1_000_000)
	if got := c.step(now, 95_000, 2, 1_000_000); got != 1 {
		t.Fatalf("got %d workers, want 1 after a doubling that lost rate", got)
	}
	if got := c.step(now.Add(time.Second), 100_000, 1, 1_000_000); got != 1 {
		t.Fatalf("got %d workers, want the ceiling of 1 held", got)
	}
	if got := c.step(now.Add(scaleHold+time.Second), 100_000, 1, 1_000_000); got != 2 {
		t.Fatalf("got %d workers, want a fresh probe once the hold ends", got)
	}
}

func TestScalerLeavesIdleWorkersOut(t *testing.T) {
	c := newScaler(16)
	c.active = 8
	// Eight workers carry the full rate on half a core between them.
	if got := c.step(time.Unix(0, 0), 100_000, 0.5, 100_000); got != 1 {
		t.Fatalf("got %d workers, want 1", got)
	}
}

func TestScalerHoldsWhileNotSaturated(t *testing.T) {
	c := newScaler(16)
	// Behind, but the one worker is mostly waiting: another would not help.
	if got := c.step(time.Unix(0, 0), 50_000, 0.3, 100_000); got != 1 {
		t.Fatalf("got %d workers, want 1", got)
	}
}

func TestScalerBacksOffADoublingThatAddedLittle(t *testing.T) {
	c := newScaler(2)
	now := time.Unix(0, 0)
	c.step(now, 2_092, 1, 100_000)
	if got := c.step(now, 2_499, 2, 100_000); got != 1 {
		t.Fatalf("got %d workers, want 1 for a fifth more rate", got)
	}
}
