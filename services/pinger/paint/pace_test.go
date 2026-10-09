package paint

import (
	"context"
	"testing"
	"time"
)

func TestChunkSizeCoversTwentyMillisecondsWithinBounds(t *testing.T) {
	for _, c := range []struct {
		share float64
		want  int
	}{{10, 1}, {6_250, 125}, {1_000_000, chunkMax}} {
		if got := chunkSize(c.share); got != c.want {
			t.Fatalf("chunkSize(%v) = %d, want %d", c.share, got, c.want)
		}
	}
}

func TestPacerHoldsItsShare(t *testing.T) {
	var p pacer
	const share = 20_000
	begin := time.Now()
	sent := 0
	for time.Since(begin) < 200*time.Millisecond {
		if !p.take(context.Background(), 100, share) {
			t.Fatal("take refused with a live context")
		}
		sent += 100
	}
	elapsed := time.Since(begin).Seconds()
	if limit := share*(elapsed+paceSlack.Seconds()) + 100; float64(sent) > limit {
		t.Fatalf("sent %d in %.3fs at %d a second", sent, elapsed, share)
	}
}

func TestPacerGivesUpWhenTheContextEnds(t *testing.T) {
	var p pacer
	ctx, cancel := context.WithCancel(context.Background())
	p.take(ctx, 10, 1)
	cancel()
	if p.take(ctx, 10, 1) {
		t.Fatal("take waited out a cancelled context")
	}
}
