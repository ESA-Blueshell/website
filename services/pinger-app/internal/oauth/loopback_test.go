package oauth

import (
	"context"
	"net/http"
	"strings"
	"testing"
	"time"
)

// getRedirect fires the callback request. It runs in a goroutine, so it reports nothing to t; a
// failure to reach the loopback surfaces as a timeout in the test's Wait.
func getRedirect(lb *Loopback, query string) {
	if resp, err := http.Get(lb.RedirectURI() + query); err == nil {
		resp.Body.Close()
	}
}

func TestLoopbackReturnsCode(t *testing.T) {
	lb, err := NewLoopback("state-123")
	if err != nil {
		t.Fatalf("NewLoopback: %v", err)
	}
	defer lb.Close()

	if !strings.HasSuffix(lb.RedirectURI(), redirectPath) {
		t.Fatalf("redirect URI %q does not end in the registered path", lb.RedirectURI())
	}

	go getRedirect(lb, "?state=state-123&code=the-code")

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	code, err := lb.Wait(ctx)
	if err != nil {
		t.Fatalf("Wait: %v", err)
	}
	if code != "the-code" {
		t.Fatalf("code = %q, want %q", code, "the-code")
	}
}

func TestLoopbackRefusesMismatchedState(t *testing.T) {
	lb, err := NewLoopback("expected")
	if err != nil {
		t.Fatalf("NewLoopback: %v", err)
	}
	defer lb.Close()

	go getRedirect(lb, "?state=attacker&code=the-code")

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	if _, err := lb.Wait(ctx); err == nil {
		t.Fatal("expected an error for a mismatched state, got nil")
	}
}

func TestLoopbackReportsAuthorizationError(t *testing.T) {
	lb, err := NewLoopback("state-123")
	if err != nil {
		t.Fatalf("NewLoopback: %v", err)
	}
	defer lb.Close()

	go getRedirect(lb, "?state=state-123&error=access_denied")

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	_, err = lb.Wait(ctx)
	if err == nil || !strings.Contains(err.Error(), "access_denied") {
		t.Fatalf("expected an access_denied error, got %v", err)
	}
}
