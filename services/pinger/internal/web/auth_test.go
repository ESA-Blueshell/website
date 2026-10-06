package web

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

// fakeForwardAuth answers like the api's /oauth2/forward-auth: 200 with the caller's roles for a
// known session cookie, 401 otherwise.
func fakeForwardAuth(t *testing.T, sessions map[string]string) *httptest.Server {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/oauth2/forward-auth" || r.Header.Get("X-Forwarded-Host") != "pings.esa-blueshell.nl" {
			w.WriteHeader(http.StatusBadRequest)
			return
		}
		c, err := r.Cookie("SESSION")
		if err != nil || sessions[c.Value] == "" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		w.Header().Set("X-User-Groups", sessions[c.Value])
		w.WriteHeader(http.StatusOK)
	}))
	t.Cleanup(srv.Close)
	return srv
}

func requestWithSession(value string) *http.Request {
	r := httptest.NewRequest(http.MethodPost, "/settings", nil)
	if value != "" {
		r.AddCookie(&http.Cookie{Name: "SESSION", Value: value})
	}
	r.Header.Set("X-User-Groups", "ADMIN")
	return r
}

func TestAPIAuthAsksTheApiInsteadOfTrustingTheHeader(t *testing.T) {
	api := fakeForwardAuth(t, map[string]string{
		"admin":  "ADMIN,TREASURER,BOARD,COMMITTEE,MEMBER,GUEST,ANONYMOUS",
		"member": "MEMBER,GUEST,ANONYMOUS",
	})
	auth := NewAPIAuth(api.URL, "pings.esa-blueshell.nl")

	cases := map[string]bool{"admin": true, "member": false, "forged": false, "": false}
	for session, want := range cases {
		got, err := auth.IsAdmin(requestWithSession(session))
		if err != nil {
			t.Fatalf("%q: %v", session, err)
		}
		if got != want {
			t.Errorf("session %q: admin %v, want %v", session, got, want)
		}
	}
}

func TestAPIAuthReportsAnApiItCannotReach(t *testing.T) {
	auth := NewAPIAuth("http://127.0.0.1:1", "pings.esa-blueshell.nl")

	if _, err := auth.IsAdmin(requestWithSession("admin")); err == nil {
		t.Fatal("no error for an unreachable api")
	}
}
