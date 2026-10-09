// Package prefs keeps the member's local control choices between runs. Only the send rate and the
// uplink headroom live here; the leaderboard opt-in is the server's and is read and written there. The file sits in the
// app config directory next to the token store.
package prefs

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
)

// fileName is the preferences file inside the config directory.
const fileName = "prefs.json"

// Rate bounds for the member's own machine. The maximum is the pings per second that fill roughly a
// 1 Gbit/s uplink at ~57 bytes per packet — the physical ceiling on fast campus broadband — so a
// member can give as much as their connection allows. The SNTPings event may rate-limit a prefix
// separately; that is the event's concern, not a local cap.
const (
	MinRatePPS = 1
	MaxRatePPS = 2_192_982
)

// DefaultRatePPS is the send rate a fresh install starts at. It is far below the cap and gentle on
// a home uplink: at the estimate's bytes-per-packet it is well under a tenth of a megabit, so the
// app does not saturate a connection before the member has touched the slider.
const DefaultRatePPS = 200

// Prefs are the member's local choices and this install's stable identity.
type Prefs struct {
	RatePPS int `json:"ratePps"`
	// DeviceID is this install's stable id, generated once and kept, so the api keeps this device's
	// send counter apart from the member's other devices and accrues each exactly once.
	DeviceID string `json:"deviceId"`
	// FullUplink lets the pings fill the whole uplink, even when that stalls the member's other
	// traffic. Off by default, so a missing field keeps the connection usable.
	FullUplink bool `json:"fullUplink"`
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

// DeviceID returns this install's stable device id, generating and persisting one on first use. It
// returns the id even when persisting it fails, so reporting always carries an id; the error says
// the id is not yet saved and will differ next run. A rand failure returns an empty id and the
// error, which the caller handles.
func (s *Store) DeviceID() (string, error) {
	p := s.Load()
	if p.DeviceID != "" {
		return p.DeviceID, nil
	}
	id, err := newDeviceID()
	if err != nil {
		return "", err
	}
	p.DeviceID = id
	return id, s.Save(p)
}

// newDeviceID is a random 128-bit id as hex, unique per install and never a secret.
func newDeviceID() (string, error) {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
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
