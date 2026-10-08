package oauth

import (
	"crypto/sha256"
	"encoding/base64"
	"testing"
)

func TestNewPKCEChallengeMatchesVerifier(t *testing.T) {
	p, err := NewPKCE()
	if err != nil {
		t.Fatalf("NewPKCE: %v", err)
	}
	if n := len(p.Verifier); n < 43 || n > 128 {
		t.Fatalf("verifier length %d outside RFC 7636 range 43..128", n)
	}
	sum := sha256.Sum256([]byte(p.Verifier))
	want := base64.RawURLEncoding.EncodeToString(sum[:])
	if p.Challenge != want {
		t.Fatalf("challenge %q is not S256 of the verifier %q", p.Challenge, want)
	}
}

func TestNewPKCEDrawsFreshVerifiers(t *testing.T) {
	a, err := NewPKCE()
	if err != nil {
		t.Fatalf("NewPKCE: %v", err)
	}
	b, err := NewPKCE()
	if err != nil {
		t.Fatalf("NewPKCE: %v", err)
	}
	if a.Verifier == b.Verifier {
		t.Fatal("two verifiers were identical; they must be drawn fresh each time")
	}
}
