// Package config holds the few settings the desktop app needs: where the site is and where the
// app keeps its state. The base URL is the api origin, which is also the OAuth issuer.
package config

import (
	"os"
	"path/filepath"
	"strings"
)

// DefaultBaseURL is the production api origin. It doubles as the OAuth issuer, so the authorize and
// token endpoints hang off it directly.
const DefaultBaseURL = "https://esa-blueshell.nl/api"

// ClientID is the public desktop client the authorization server registered for this app.
const ClientID = "pinger-app"

// dirName is the app's own folder under the OS config directory.
const dirName = "blueshell-pinger"

// BaseURL is the api origin, overridable so a developer can point at a local stack. Trailing
// slashes are stripped so endpoints join cleanly.
func BaseURL() string {
	if v := strings.TrimSpace(os.Getenv("BLUESHELL_PINGER_SERVER")); v != "" {
		return strings.TrimSuffix(v, "/")
	}
	return DefaultBaseURL
}

// Dir is the app's config directory, created if missing. Tokens live here when the OS keychain is
// not used, so it must stay private to the user.
func Dir() (string, error) {
	base, err := os.UserConfigDir()
	if err != nil {
		return "", err
	}
	dir := filepath.Join(base, dirName)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return "", err
	}
	return dir, nil
}
