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
	if s.Load().HoldBack {
		t.Fatal("Load on missing file holds the rate back, want the full rate")
	}
}

func TestSaveLoadRoundTrip(t *testing.T) {
	s := New(t.TempDir())
	if err := s.Save(Prefs{RatePPS: 4096, HoldBack: true}); err != nil {
		t.Fatalf("Save: %v", err)
	}
	if got := s.Load().RatePPS; got != 4096 {
		t.Fatalf("round-trip rate = %d, want 4096", got)
	}
	if !s.Load().HoldBack {
		t.Fatal("round-trip lost HoldBack")
	}
}

func TestAnOlderFullUplinkChoiceNoLongerHoldsTheRateBack(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, fileName), []byte(`{"ratePps":1000000,"fullUplink":false}`), 0o600); err != nil {
		t.Fatal(err)
	}
	if New(dir).Load().HoldBack {
		t.Fatal("a saved fullUplink:false from an older version turned the limiter on")
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

// The device id is generated once, persisted, and stable across calls and reloads, so the api sees
// one device per install rather than a new one each run.
func TestDeviceIDGeneratesPersistsAndIsStable(t *testing.T) {
	dir := t.TempDir()
	first, err := New(dir).DeviceID()
	if err != nil {
		t.Fatalf("DeviceID: %v", err)
	}
	if first == "" {
		t.Fatal("DeviceID returned empty")
	}
	again, err := New(dir).DeviceID()
	if err != nil {
		t.Fatalf("DeviceID again: %v", err)
	}
	if again != first {
		t.Fatalf("DeviceID changed across reloads: %q then %q", first, again)
	}
	if got := New(dir).Load().DeviceID; got != first {
		t.Fatalf("stored DeviceID = %q, want %q", got, first)
	}
}

// Saving a rate after the device id exists must keep the id, the way the runner writes it.
func TestSavingARateKeepsTheDeviceID(t *testing.T) {
	dir := t.TempDir()
	id, err := New(dir).DeviceID()
	if err != nil {
		t.Fatalf("DeviceID: %v", err)
	}
	s := New(dir)
	p := s.Load()
	p.RatePPS = 4096
	if err := s.Save(p); err != nil {
		t.Fatalf("Save: %v", err)
	}
	loaded := New(dir).Load()
	if loaded.DeviceID != id {
		t.Fatalf("DeviceID after a rate save = %q, want %q", loaded.DeviceID, id)
	}
	if loaded.RatePPS != 4096 {
		t.Fatalf("RatePPS after save = %d, want 4096", loaded.RatePPS)
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
