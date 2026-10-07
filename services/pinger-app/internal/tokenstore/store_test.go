package tokenstore

import (
	"errors"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestSaveLoadRoundTrip(t *testing.T) {
	dir := t.TempDir()
	st := New(dir)

	want := Set{
		Access:       "at",
		Refresh:      "rt",
		AccessExpiry: time.Date(2026, 1, 1, 12, 0, 0, 0, time.UTC),
	}
	if err := st.Save(want); err != nil {
		t.Fatalf("Save: %v", err)
	}
	got, err := st.Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if got.Access != want.Access || got.Refresh != want.Refresh || !got.AccessExpiry.Equal(want.AccessExpiry) {
		t.Fatalf("round trip mismatch: got %+v want %+v", got, want)
	}
}

func TestSaveWritesPrivateFile(t *testing.T) {
	dir := t.TempDir()
	st := New(dir)
	if err := st.Save(Set{Access: "at"}); err != nil {
		t.Fatalf("Save: %v", err)
	}
	info, err := os.Stat(filepath.Join(dir, fileName))
	if err != nil {
		t.Fatalf("Stat: %v", err)
	}
	if perm := info.Mode().Perm(); perm != 0o600 {
		t.Fatalf("token file perm = %o, want 600", perm)
	}
}

func TestLoadMissingReturnsErrNoToken(t *testing.T) {
	st := New(t.TempDir())
	if _, err := st.Load(); !errors.Is(err, ErrNoToken) {
		t.Fatalf("err = %v, want ErrNoToken", err)
	}
}

func TestAccessValidHonoursSkew(t *testing.T) {
	now := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	s := Set{Access: "at", AccessExpiry: now.Add(30 * time.Second)}
	if s.AccessValid(now, time.Minute) {
		t.Fatal("a token expiring inside the skew window must count as invalid")
	}
	if !s.AccessValid(now, 10*time.Second) {
		t.Fatal("a token past the skew window must count as valid")
	}
	if (Set{AccessExpiry: now.Add(time.Hour)}).AccessValid(now, 0) {
		t.Fatal("an empty access token must count as invalid")
	}
}

func TestHasRefresh(t *testing.T) {
	if (Set{}).HasRefresh() {
		t.Fatal("empty set reports a refresh token")
	}
	if !(Set{Refresh: "rt"}).HasRefresh() {
		t.Fatal("set with a refresh token reports none")
	}
}

func TestClearRemovesFile(t *testing.T) {
	dir := t.TempDir()
	st := New(dir)
	if err := st.Save(Set{Access: "at"}); err != nil {
		t.Fatalf("Save: %v", err)
	}
	if err := st.Clear(); err != nil {
		t.Fatalf("Clear: %v", err)
	}
	if _, err := st.Load(); !errors.Is(err, ErrNoToken) {
		t.Fatalf("after Clear, Load err = %v, want ErrNoToken", err)
	}
	// Clearing an already-absent file is not an error.
	if err := st.Clear(); err != nil {
		t.Fatalf("second Clear: %v", err)
	}
}
