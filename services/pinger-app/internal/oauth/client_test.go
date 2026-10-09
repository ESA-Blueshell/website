package oauth

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"
	"time"
)

func TestAuthorizeURLCarriesPKCEAndRedirect(t *testing.T) {
	c := NewClient("https://esa-blueshell.nl/api", "pinger-app")
	raw := c.AuthorizeURL("http://127.0.0.1:55123/login/oauth2/code/pinger-app", "chal", "st")
	u, err := url.Parse(raw)
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	q := u.Query()
	for k, want := range map[string]string{
		"response_type":         "code",
		"client_id":             "pinger-app",
		"redirect_uri":          "http://127.0.0.1:55123/login/oauth2/code/pinger-app",
		"scope":                 "openid",
		"state":                 "st",
		"code_challenge":        "chal",
		"code_challenge_method": "S256",
	} {
		if got := q.Get(k); got != want {
			t.Errorf("%s = %q, want %q", k, got, want)
		}
	}
	if u.Scheme+"://"+u.Host+u.Path != "https://esa-blueshell.nl/api/oauth2/authorize" {
		t.Errorf("authorize endpoint = %q", u.Scheme+"://"+u.Host+u.Path)
	}
}

func TestExchangeReturnsTokenWithExpiry(t *testing.T) {
	var gotForm url.Values
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		r.ParseForm()
		gotForm = r.PostForm
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"access_token":"at","refresh_token":"rt","expires_in":900,"token_type":"Bearer"}`))
	}))
	defer srv.Close()

	fixed := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	c := NewClient(srv.URL, "pinger-app")
	c.now = func() time.Time { return fixed }

	tok, err := c.Exchange(context.Background(), "the-code", "http://127.0.0.1:1/cb", "verifier")
	if err != nil {
		t.Fatalf("Exchange: %v", err)
	}
	if tok.Access != "at" || tok.Refresh != "rt" {
		t.Fatalf("tokens = %+v", tok)
	}
	if !tok.Expiry.Equal(fixed.Add(900 * time.Second)) {
		t.Fatalf("expiry = %v, want %v", tok.Expiry, fixed.Add(900*time.Second))
	}
	if gotForm.Get("grant_type") != "authorization_code" ||
		gotForm.Get("code") != "the-code" ||
		gotForm.Get("code_verifier") != "verifier" ||
		gotForm.Get("client_id") != "pinger-app" {
		t.Fatalf("exchange form was wrong: %v", gotForm)
	}
}

func TestRefreshMapsInvalidGrant(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte(`{"error":"invalid_grant"}`))
	}))
	defer srv.Close()

	c := NewClient(srv.URL, "pinger-app")
	_, err := c.Refresh(context.Background(), "stale")
	if !errors.Is(err, ErrInvalidGrant) {
		t.Fatalf("err = %v, want ErrInvalidGrant", err)
	}
}

func TestRefreshTreatsALoginRedirectAsARefusal(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/login" {
			w.Header().Set("Content-Type", "text/html")
			w.Write([]byte("<html>sign in</html>"))
			return
		}
		http.Redirect(w, r, "/login?redirect=%2F", http.StatusFound)
	}))
	defer srv.Close()

	c := NewClient(srv.URL, "pinger-app")
	_, err := c.Refresh(context.Background(), "stale")
	if !errors.Is(err, ErrInvalidGrant) {
		t.Fatalf("err = %v, want ErrInvalidGrant", err)
	}
}

func TestRefreshTreatsAnUnknownClientAsARefusal(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusUnauthorized)
		w.Write([]byte(`{"error":"invalid_client"}`))
	}))
	defer srv.Close()

	c := NewClient(srv.URL, "pinger-app")
	_, err := c.Refresh(context.Background(), "stale")
	if !errors.Is(err, ErrInvalidGrant) {
		t.Fatalf("err = %v, want ErrInvalidGrant", err)
	}
}

func TestRefreshSendsRefreshGrant(t *testing.T) {
	var gotForm url.Values
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		r.ParseForm()
		gotForm = r.PostForm
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"access_token":"new","refresh_token":"rotated","expires_in":900}`))
	}))
	defer srv.Close()

	c := NewClient(srv.URL, "pinger-app")
	tok, err := c.Refresh(context.Background(), "old-refresh")
	if err != nil {
		t.Fatalf("Refresh: %v", err)
	}
	if tok.Access != "new" || tok.Refresh != "rotated" {
		t.Fatalf("rotated tokens not returned: %+v", tok)
	}
	if gotForm.Get("grant_type") != "refresh_token" || gotForm.Get("refresh_token") != "old-refresh" {
		t.Fatalf("refresh form was wrong: %v", gotForm)
	}
}
