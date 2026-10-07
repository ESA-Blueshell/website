package prefs

import (
	"os"
	"path/filepath"
	"testing"
)

func TestClamp(t *testing.T) {
	cases := []struct {
		in, want int
	}{
		{in: 0, want: MinRatePPS},
		{in: -100, want: MinRatePPS},
		{in: 1, want: 1},
		{in: 200, want: 200},
		{in: MaxRatePPS, want: MaxRatePPS},
		{in: MaxRatePPS + 1, want: MaxRatePPS},
		{in: 10_000_000, want: MaxRatePPS},
	}
	for _, c := range cases {
		if got := Clamp(c.in); got != c.want {
			t.Errorf("Clamp(%d) = %d, want %d", c.in, got, c.want)
		}
	}
}

func TestLoadMissingFileReturnsDefault(t *testing.T) {
	s := New(t.TempDir())
	if got := s.Load().RatePPS; got != DefaultRatePPS {
		t.Fatalf("Load on missing file = %d, want default %d", got, DefaultRatePPS)
	}
}

func TestSaveLoadRoundTrip(t *testing.T) {
	s := New(t.TempDir())
	if err := s.Save(Prefs{RatePPS: 4096}); err != nil {
		t.Fatalf("Save: %v", err)
	}
	if got := s.Load().RatePPS; got != 4096 {
		t.Fatalf("round-trip rate = %d, want 4096", got)
	}
}

// A rate saved out of range is clamped on the way in, so a hand-edited file cannot ask the sender
// for a banned rate.
func TestSaveClampsOutOfRange(t *testing.T) {
	s := New(t.TempDir())
	if err := s.Save(Prefs{RatePPS: MaxRatePPS + 500}); err != nil {
		t.Fatalf("Save: %v", err)
	}
	if got := s.Load().RatePPS; got != MaxRatePPS {
		t.Fatalf("saved out-of-range rate loaded as %d, want %d", got, MaxRatePPS)
	}
}

// A file the member could not have written cannot smuggle in an out-of-range rate either: Load
// clamps whatever it reads.
func TestLoadClampsAndToleratesGarbage(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, fileName)
	if err := os.WriteFile(path, []byte(`{"ratePps": 999999999}`), 0o600); err != nil {
		t.Fatalf("write: %v", err)
	}
	if got := New(dir).Load().RatePPS; got != MaxRatePPS {
		t.Fatalf("Load clamped to %d, want %d", got, MaxRatePPS)
	}

	if err := os.WriteFile(path, []byte("not json"), 0o600); err != nil {
		t.Fatalf("write: %v", err)
	}
	if got := New(dir).Load().RatePPS; got != DefaultRatePPS {
		t.Fatalf("Load on garbage = %d, want default %d", got, DefaultRatePPS)
	}
}

func TestSaveFileIsPrivate(t *testing.T) {
	dir := t.TempDir()
	s := New(dir)
	if err := s.Save(Prefs{RatePPS: 200}); err != nil {
		t.Fatalf("Save: %v", err)
	}
	info, err := os.Stat(filepath.Join(dir, fileName))
	if err != nil {
		t.Fatalf("stat: %v", err)
	}
	if perm := info.Mode().Perm(); perm != 0o600 {
		t.Fatalf("prefs file mode = %o, want 600", perm)
	}
}
