package runner

import (
	"context"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger/paint"

	"github.com/ESA-Blueshell/website/services/pinger-app/internal/auth"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/oauth"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/report"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/tokenstore"
)

// A member who closes the first sign-in tab leaves that login waiting on its redirect for good. Signing
// in again must still open a fresh login and resume reporting, without restarting the app.
func TestSigningInAgainAfterAnAbandonedLoginResumesReporting(t *testing.T) {
	reports := make(chan string, 8)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		if req.URL.Path == "/pinger/report" {
			reports <- req.Header.Get("Authorization")
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	abandoned := make(chan struct{})
	var attempts atomic.Int32
	login := func(ctx context.Context) (oauth.Token, error) {
		if attempts.Add(1) == 1 {
			close(abandoned)
			<-ctx.Done()
			return oauth.Token{}, ctx.Err()
		}
		return oauth.Token{Access: "fresh", Refresh: "rt", Expiry: time.Now().Add(time.Hour)}, nil
	}
	store := &memStore{set: tokenstore.Set{Access: "at", Refresh: "rt", AccessExpiry: time.Now().Add(time.Hour)}}
	authn := auth.New(store, &failingRefresher{}, login)
	r := &Runner{auth: authn, poster: report.NewPoster(srv.URL, authn, "device"), wake: make(chan struct{}, 1)}
	r.signedIn.Store(true)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	sender := paint.NewSender(nil, nil, paint.Window{}, func() paint.Settings { return paint.Settings{} })
	go r.reportLoop(ctx, sender)

	r.SignOut()
	go r.SignIn(ctx)
	<-abandoned

	signedIn := make(chan struct{})
	go func() { r.SignIn(ctx); close(signedIn) }()
	select {
	case <-signedIn:
	case <-time.After(2 * time.Second):
		t.Fatal("a second sign-in queued behind the abandoned login")
	}
	if !r.signedIn.Load() {
		t.Fatal("the member is not signed in after a successful login")
	}

	select {
	case bearer := <-reports:
		if bearer != "Bearer fresh" {
			t.Fatalf("report sent with %q, want the fresh token", bearer)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("reporting did not resume after signing in again")
	}
}
