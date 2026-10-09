package runner

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger-app/internal/auth"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/oauth"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/report"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/tokenstore"
)

type memStore struct{ set tokenstore.Set }

func (m *memStore) Load() (tokenstore.Set, error) { return m.set, nil }
func (m *memStore) Save(s tokenstore.Set) error   { m.set = s; return nil }

type failingRefresher struct{ calls int }

func (f *failingRefresher) Refresh(context.Context, string) (oauth.Token, error) {
	f.calls++
	return oauth.Token{}, oauth.ErrInvalidGrant
}

func signedInRunner(t *testing.T) (*Runner, *memStore, *failingRefresher) {
	t.Helper()
	store := &memStore{set: tokenstore.Set{Access: "at", Refresh: "rt", AccessExpiry: time.Now().Add(time.Hour)}}
	ref := &failingRefresher{}
	r := &Runner{auth: auth.New(store, ref, nil)}
	r.signedIn.Store(true)
	r.setAccount("member")
	return r, store, ref
}

// The api refuses a good token while its signing keys load after a restart, so a refusal must not
// sign the member out or drop the token.
func TestRefusedReportKeepsTheMemberSignedIn(t *testing.T) {
	r, store, ref := signedInRunner(t)

	r.afterReport(context.Background(), report.ErrUnauthorized)

	if !r.signedIn.Load() {
		t.Fatal("a refused report signed the member out")
	}
	if ref.calls != 1 {
		t.Fatalf("refresh called %d times, want one renewal attempt", ref.calls)
	}
	if store.set.Access != "at" {
		t.Fatalf("the refused access token was dropped: %+v", store.set)
	}
}

func TestTransientReportErrorKeepsTheMemberSignedIn(t *testing.T) {
	r, _, _ := signedInRunner(t)

	r.afterReport(context.Background(), errors.New("report: status 503"))

	if !r.signedIn.Load() {
		t.Fatal("a transient error signed the member out")
	}
}

func TestLapsedSignInSignsTheMemberOut(t *testing.T) {
	r, _, _ := signedInRunner(t)

	r.afterReport(context.Background(), auth.ErrSignInRequired)

	if r.signedIn.Load() || r.loadAccount() != "" {
		t.Fatal("a lapsed sign-in must show the member signed out")
	}
}
