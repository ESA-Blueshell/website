package leaderboard

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

type fakeTokens struct {
	token string
	err   error
}

func (f fakeTokens) Token(context.Context) (string, error) { return f.token, f.err }

func TestStateReadsServerFlag(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet || r.URL.Path != "/pinger/leaderboard/opt-in" {
			t.Errorf("unexpected request %s %s", r.Method, r.URL.Path)
		}
		if got := r.Header.Get("Authorization"); got != "Bearer header.payload.sig" {
			t.Errorf("missing bearer, got %q", got)
		}
		_ = json.NewEncoder(w).Encode(map[string]bool{"optedIn": true})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, fakeTokens{token: "header.payload.sig"})
	got, err := c.State(context.Background())
	if err != nil {
		t.Fatalf("State: %v", err)
	}
	if !got {
		t.Fatalf("State = false, want true")
	}
}

func TestSetSendsChoiceAndReturnsResult(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPut {
			t.Errorf("method = %s, want PUT", r.Method)
		}
		body, _ := io.ReadAll(r.Body)
		var req map[string]bool
		if err := json.Unmarshal(body, &req); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		if !req["optedIn"] {
			t.Errorf("request optedIn = false, want true")
		}
		// Echo the member's choice back as the server's resulting state.
		_ = json.NewEncoder(w).Encode(map[string]bool{"optedIn": req["optedIn"]})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, fakeTokens{token: "t"})
	got, err := c.Set(context.Background(), true)
	if err != nil {
		t.Fatalf("Set: %v", err)
	}
	if !got {
		t.Fatalf("Set returned false, want the server's true")
	}
}

func TestUnauthorizedMapsToSentinel(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer srv.Close()

	c := NewClient(srv.URL, fakeTokens{token: "t"})
	if _, err := c.State(context.Background()); !errors.Is(err, ErrUnauthorized) {
		t.Fatalf("err = %v, want ErrUnauthorized", err)
	}
}

func TestTokenErrorIsReturned(t *testing.T) {
	c := NewClient("http://example.invalid", fakeTokens{err: errors.New("no token")})
	if _, err := c.State(context.Background()); err == nil || !strings.Contains(err.Error(), "no token") {
		t.Fatalf("err = %v, want the token error", err)
	}
}
