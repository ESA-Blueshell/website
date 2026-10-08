package paint

import (
	"testing"
	"time"
)

func TestBackoffDoublesFromAMillisecondUpToASecond(t *testing.T) {
	var b Backoff
	want := []time.Duration{
		1 * time.Millisecond, 2 * time.Millisecond, 4 * time.Millisecond, 8 * time.Millisecond,
		16 * time.Millisecond, 32 * time.Millisecond, 64 * time.Millisecond, 128 * time.Millisecond,
		256 * time.Millisecond, 512 * time.Millisecond, time.Second, time.Second,
	}
	for i, w := range want {
		if got := b.Next(); got != w {
			t.Fatalf("step %d: got %s, want %s", i, got, w)
		}
	}
}

func TestBackoffStartsOverAfterASuccess(t *testing.T) {
	var b Backoff
	b.Next()
	b.Next()
	b.Reset()

	if got := b.Next(); got != time.Millisecond {
		t.Fatalf("got %s, want 1ms", got)
	}
}
