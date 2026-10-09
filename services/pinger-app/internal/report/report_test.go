package report

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

func TestBuildMapsSnapshot(t *testing.T) {
	r := Build(paint.Stats{
		State:     paint.Running,
		ActualPPS: 1500,
		Sent:      42000,
		Errors:    3,
	})
	if !r.Online || r.PPS != 1500 || r.Sent != 42000 || r.Errors != 3 {
		t.Fatalf("report = %+v", r)
	}
}

func TestBuildOfflineWhenNotRunning(t *testing.T) {
	if Build(paint.Stats{State: paint.Idle}).Online {
		t.Fatal("an idle sender must report offline")
	}
}

func TestBuildReportsTheTopRateInFull(t *testing.T) {
	r := Build(paint.Stats{State: paint.Running, ActualPPS: 2_192_982})
	if r.PPS != 2_192_982 {
		t.Fatalf("pps = %d, want 2192982", r.PPS)
	}
}

func TestBuildCapsPPSAtServerLimit(t *testing.T) {
	r := Build(paint.Stats{State: paint.Running, ActualPPS: 90_000_000})
	if r.PPS != maxPPS {
		t.Fatalf("pps = %d, want the cap %d", r.PPS, maxPPS)
	}
}

type staticToken string

func (s staticToken) Current(context.Context) (string, error) { return string(s), nil }

func TestPostSendsBearerAndBody(t *testing.T) {
	var gotAuth string
	var gotBody Report
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotAuth = r.Header.Get("Authorization")
		body, _ := io.ReadAll(r.Body)
		json.Unmarshal(body, &gotBody)
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	p := NewPoster(srv.URL, staticToken("the-token"), "dev-123")
	want := Report{DeviceID: "dev-123", Online: true, PPS: 100, Sent: 2000, Errors: 1}
	if err := p.Post(context.Background(), want); err != nil {
		t.Fatalf("Post: %v", err)
	}
	if gotAuth != "Bearer the-token" {
		t.Fatalf("auth header = %q", gotAuth)
	}
	if gotBody != want {
		t.Fatalf("server received %+v, want %+v", gotBody, want)
	}
}

func TestPostMapsUnauthorized(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer srv.Close()

	p := NewPoster(srv.URL, staticToken("dud"), "dev-123")
	if err := p.Post(context.Background(), Report{}); !errors.Is(err, ErrUnauthorized) {
		t.Fatalf("err = %v, want ErrUnauthorized", err)
	}
}

type failingToken struct{ err error }

func (f failingToken) Current(context.Context) (string, error) { return "", f.err }

func TestPostSurfacesTokenError(t *testing.T) {
	boom := errors.New("no token")
	p := NewPoster("http://127.0.0.1:1", failingToken{err: boom}, "dev-123")
	if err := p.Post(context.Background(), Report{}); !errors.Is(err, boom) {
		t.Fatalf("err = %v, want the token error", err)
	}
}
