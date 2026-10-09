// Package tokenstore keeps the member's tokens between runs. This skeleton writes them to a
// 0600 file in the app config directory; the OS keychain is the intended home and is left for a
// later slice. Token values are never logged.
package tokenstore

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"time"
)

// ErrNoToken means nothing has been stored yet, so the app must run the browser login.
var ErrNoToken = errors.New("tokenstore: no stored token")

// fileName is the token file inside the config directory.
const fileName = "tokens.json"

// Set is the stored material: the current access token with its expiry and the refresh token that
// renews it. AccessExpiry drives the refresh decision; the refresh token's own lifetime is not
// known to the client and is discovered only when a refresh is rejected.
type Set struct {
	Access       string    `json:"access"`
	Refresh      string    `json:"refresh"`
	AccessExpiry time.Time `json:"accessExpiry"`
}

// HasRefresh reports whether a refresh token is present to attempt a silent renewal.
func (s Set) HasRefresh() bool { return s.Refresh != "" }

// AccessValid reports whether the access token is still good for at least skew from now, so a token
// about to expire mid-request counts as invalid.
func (s Set) AccessValid(now time.Time, skew time.Duration) bool {
	return s.Access != "" && now.Add(skew).Before(s.AccessExpiry)
}

// Store reads and writes the token set at a fixed path.
type Store struct {
	path string
}

// New points a store at dir/tokens.json.
func New(dir string) *Store {
	return &Store{path: filepath.Join(dir, fileName)}
}

// Load reads the stored set, or ErrNoToken when nothing has been saved.
func (st *Store) Load() (Set, error) {
	data, err := os.ReadFile(st.path)
	if errors.Is(err, os.ErrNotExist) {
		return Set{}, ErrNoToken
	}
	if err != nil {
		return Set{}, err
	}
	var s Set
	if err := json.Unmarshal(data, &s); err != nil {
		return Set{}, err
	}
	return s, nil
}

// Save writes the set atomically with 0600 permissions, so the tokens never sit world-readable
// even briefly. The temp file is created 0600 and renamed over the target.
func (st *Store) Save(s Set) error {
	data, err := json.Marshal(s)
	if err != nil {
		return err
	}
	tmp, err := os.CreateTemp(filepath.Dir(st.path), fileName+".*")
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
	return os.Rename(tmpName, st.path)
}

// Clear removes the stored tokens, forcing a fresh browser login next time.
func (st *Store) Clear() error {
	err := os.Remove(st.path)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	return err
}
