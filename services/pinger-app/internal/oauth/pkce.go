// Package oauth runs the authorization_code + PKCE loopback flow (RFC 8252) against the site and
// exchanges and refreshes the member's tokens. It never logs a token value.
package oauth

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
)

// PKCE is a verifier and its S256 challenge for one authorization request. The verifier is the
// secret kept on this machine; only the challenge travels to the authorization endpoint.
type PKCE struct {
	Verifier  string
	Challenge string
}

// NewPKCE draws a fresh verifier and derives its S256 challenge. The verifier is 43 characters of
// base64url (32 random bytes), within the 43..128 range RFC 7636 allows.
func NewPKCE() (PKCE, error) {
	buf := make([]byte, 32)
	if _, err := rand.Read(buf); err != nil {
		return PKCE{}, err
	}
	verifier := base64.RawURLEncoding.EncodeToString(buf)
	return PKCE{Verifier: verifier, Challenge: challenge(verifier)}, nil
}

// challenge is base64url(SHA256(verifier)), the S256 transform the server recomputes.
func challenge(verifier string) string {
	sum := sha256.Sum256([]byte(verifier))
	return base64.RawURLEncoding.EncodeToString(sum[:])
}

// randomState returns an unguessable state value that ties the callback to this request.
func randomState() (string, error) {
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(buf), nil
}
