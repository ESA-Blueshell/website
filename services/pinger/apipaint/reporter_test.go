package apipaint

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"
)

func TestReporterPostsTheContributionAsSiteCie(t *testing.T) {
	var (
		mu     sync.Mutex
		token  string
		body   reportBody
		posted bool
	)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		defer mu.Unlock()
		token = r.Header.Get("X-Pinger-Service-Token")
		_ = json.NewDecoder(r.Body).Decode(&body)
		posted = true
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	r := NewReporter(srv.URL+"/", "test-token", time.Hour, func() ReportStats {
		return ReportStats{Online: true, PPS: 1200, Sent: 9000, Errors: 3}
	})
	if err := r.post(context.Background()); err != nil {
		t.Fatalf("post: %v", err)
	}

	mu.Lock()
	defer mu.Unlock()
	if !posted {
		t.Fatal("the api was not called")
	}
	if token != "test-token" {
		t.Fatalf("token header %q", token)
	}
	if !body.Online || body.PPS != 1200 || body.Sent != 9000 || body.Errors != 3 {
		t.Fatalf("payload %+v", body)
	}
}

func TestReporterDoesNotRunWithoutAToken(t *testing.T) {
	var called bool
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		called = true
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	r := NewReporter(srv.URL, "", time.Millisecond, func() ReportStats { return ReportStats{} })
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Millisecond)
	defer cancel()
	r.Run(ctx)

	if called {
		t.Fatal("reported without a token")
	}
}

func TestReporterReportsAnErrorOnABadStatus(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer srv.Close()

	r := NewReporter(srv.URL, "test-token", time.Hour, func() ReportStats { return ReportStats{} })
	if err := r.post(context.Background()); err == nil {
		t.Fatal("a 401 was not reported as an error")
	}
}
