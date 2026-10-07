// Package prefs keeps the member's local control choices between runs. Only the send rate lives
// here; the leaderboard opt-in is the server's and is read and written there. The file sits in the
// app config directory next to the token store.
package prefs

import (
	"encoding/json"
	"os"
	"path/filepath"

	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

// fileName is the preferences file inside the config directory.
const fileName = "prefs.json"

// Rate bounds mirror the server's valid range, so a stored or typed rate can never ask the sender
// for a rate the event would ban.
const (
	MinRatePPS = 1
	MaxRatePPS = paint.MaxRatePPS
)

// DefaultRatePPS is the send rate a fresh install starts at. It is far below the cap and gentle on
// a home uplink: at the estimate's bytes-per-packet it is well under a tenth of a megabit, so the
// app does not saturate a connection before the member has touched the slider.
const DefaultRatePPS = 200

// Prefs are the member's local choices.
type Prefs struct {
	RatePPS int `json:"ratePps"`
}

// Clamp holds a rate inside the server's valid range.
func Clamp(pps int) int {
	switch {
	case pps < MinRatePPS:
		return MinRatePPS
	case pps > MaxRatePPS:
		return MaxRatePPS
	default:
		return pps
	}
}

// Store reads and writes the preferences at a fixed path.
type Store struct {
	path string
}

// New points a store at dir/prefs.json.
func New(dir string) *Store {
	return &Store{path: filepath.Join(dir, fileName)}
}

// Load reads the stored preferences. A missing or unreadable file falls back to the defaults rather
// than failing, so a first run or a corrupt file still starts at a home-safe rate. The rate is
// clamped on the way out so an edited file cannot smuggle in an out-of-range value.
func (s *Store) Load() Prefs {
	p := Prefs{RatePPS: DefaultRatePPS}
	data, err := os.ReadFile(s.path)
	if err != nil {
		return p
	}
	if err := json.Unmarshal(data, &p); err != nil {
		return Prefs{RatePPS: DefaultRatePPS}
	}
	p.RatePPS = Clamp(p.RatePPS)
	return p
}

// Save writes the preferences atomically with 0600 permissions, matching the token store so the
// app's state stays private to the user. The temp file is renamed over the target.
func (s *Store) Save(p Prefs) error {
	p.RatePPS = Clamp(p.RatePPS)
	data, err := json.Marshal(p)
	if err != nil {
		return err
	}
	tmp, err := os.CreateTemp(filepath.Dir(s.path), fileName+".*")
	if err != nil {
		return err
	}
	tmpName := tmp.Name()
	defer os.Remove(tmpName)
	if err := tmp.Chmod(0o600); err != nil {
		tmp.Close()
		return err
	}
	if _, err := tmp.Write(data); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	return os.Rename(tmpName, s.path)
}
