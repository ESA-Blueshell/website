package auth

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger-app/internal/oauth"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/tokenstore"
)

// fakeStore is an in-memory token store.
type fakeStore struct {
	set   tokenstore.Set
	empty bool
	saved int
}

func (f *fakeStore) Load() (tokenstore.Set, error) {
	if f.empty {
		return tokenstore.Set{}, tokenstore.ErrNoToken
	}
	return f.set, nil
}

func (f *fakeStore) Save(s tokenstore.Set) error {
	f.set, f.empty = s, false
	f.saved++
	return nil
}

// fakeRefresher records calls and returns a scripted result.
type fakeRefresher struct {
	tok    oauth.Token
	err    error
	called int
}

func (f *fakeRefresher) Refresh(context.Context, string) (oauth.Token, error) {
	f.called++
	return f.tok, f.err
}

var fixedNow = time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)

func newAt(a *Authenticator) { a.now = func() time.Time { return fixedNow } }

func TestTokenUsesValidAccessWithoutRefreshOrLogin(t *testing.T) {
	store := &fakeStore{set: tokenstore.Set{Access: "good", AccessExpiry: fixedNow.Add(time.Hour)}}
	ref := &fakeRefresher{}
	loginCalled := false
	a := New(store, ref, func(context.Context) (oauth.Token, error) {
		loginCalled = true
		return oauth.Token{}, nil
	})
	newAt(a)

	got, err := a.Token(context.Background())
	if err != nil {
		t.Fatalf("Token: %v", err)
	}
	if got != "good" {
		t.Fatalf("token = %q, want the stored access", got)
	}
	if ref.called != 0 || loginCalled {
		t.Fatal("a valid access token must not trigger refresh or login")
	}
}

func TestTokenRefreshesWhenAccessExpired(t *testing.T) {
	store := &fakeStore{set: tokenstore.Set{
		Access:       "stale",
		Refresh:      "rt",
		AccessExpiry: fixedNow.Add(-time.Minute),
	}}
	ref := &fakeRefresher{tok: oauth.Token{Access: "fresh", Refresh: "rotated", Expiry: fixedNow.Add(time.Hour)}}
	a := New(store, ref, func(context.Context) (oauth.Token, error) {
		t.Fatal("login must not run while a refresh succeeds")
		return oauth.Token{}, nil
	})
	newAt(a)

	got, err := a.Token(context.Background())
	if err != nil {
		t.Fatalf("Token: %v", err)
	}
	if got != "fresh" {
		t.Fatalf("token = %q, want the refreshed access", got)
	}
	if ref.called != 1 {
		t.Fatalf("refresh called %d times, want 1", ref.called)
	}
	if store.set.Refresh != "rotated" {
		t.Fatalf("rotated refresh token not persisted: %q", store.set.Refresh)
	}
}

func TestTokenLogsInWhenRefreshRevoked(t *testing.T) {
	store := &fakeStore{set: tokenstore.Set{
		Access:       "stale",
		Refresh:      "revoked",
		AccessExpiry: fixedNow.Add(-time.Minute),
	}}
	ref := &fakeRefresher{err: oauth.ErrInvalidGrant}
	login := func(context.Context) (oauth.Token, error) {
		return oauth.Token{Access: "relogged", Refresh: "new", Expiry: fixedNow.Add(time.Hour)}, nil
	}
	a := New(store, ref, login)
	newAt(a)

	got, err := a.Token(context.Background())
	if err != nil {
		t.Fatalf("Token: %v", err)
	}
	if got != "relogged" {
		t.Fatalf("token = %q, want the re-logged access", got)
	}
	if ref.called != 1 {
		t.Fatalf("refresh called %d times, want 1", ref.called)
	}
}

func TestTokenDoesNotLoginOnTransientRefreshError(t *testing.T) {
	store := &fakeStore{set: tokenstore.Set{
		Access:       "stale",
		Refresh:      "rt",
		AccessExpiry: fixedNow.Add(-time.Minute),
	}}
	boom := errors.New("network down")
	ref := &fakeRefresher{err: boom}
	a := New(store, ref, func(context.Context) (oauth.Token, error) {
		t.Fatal("a transient refresh error must not open a browser")
		return oauth.Token{}, nil
	})
	newAt(a)

	if _, err := a.Token(context.Background()); !errors.Is(err, boom) {
		t.Fatalf("err = %v, want the transient error surfaced", err)
	}
}

func TestTokenLogsInWhenNothingStored(t *testing.T) {
	store := &fakeStore{empty: true}
	ref := &fakeRefresher{}
	a := New(store, ref, func(context.Context) (oauth.Token, error) {
		return oauth.Token{Access: "first", Refresh: "rt", Expiry: fixedNow.Add(time.Hour)}, nil
	})
	newAt(a)

	got, err := a.Token(context.Background())
	if err != nil {
		t.Fatalf("Token: %v", err)
	}
	if got != "first" {
		t.Fatalf("token = %q, want the fresh login access", got)
	}
	if ref.called != 0 {
		t.Fatal("no stored refresh token means refresh must not be attempted")
	}
}

func TestCurrentKeepsUnexpiredAccessWhenRefreshFailsTransiently(t *testing.T) {
	store := &fakeStore{set: tokenstore.Set{
		Access:       "still-good",
		Refresh:      "rt",
		AccessExpiry: fixedNow.Add(30 * time.Second),
	}}
	a := New(store, &fakeRefresher{err: errors.New("api restarting")}, nil)
	newAt(a)

	got, err := a.Current(context.Background())
	if err != nil {
		t.Fatalf("Current: %v", err)
	}
	if got != "still-good" {
		t.Fatalf("token = %q, want the unexpired access kept", got)
	}
}

func TestCurrentKeepsUnexpiredAccessWhenRefreshRevoked(t *testing.T) {
	store := &fakeStore{set: tokenstore.Set{
		Access:       "still-good",
		Refresh:      "forgotten",
		AccessExpiry: fixedNow.Add(30 * time.Second),
	}}
	a := New(store, &fakeRefresher{err: oauth.ErrInvalidGrant}, nil)
	newAt(a)

	got, err := a.Current(context.Background())
	if err != nil {
		t.Fatalf("Current: %v", err)
	}
	if got != "still-good" {
		t.Fatalf("token = %q, want the unexpired access kept", got)
	}
}

func TestCurrentRequiresSignInOnceAccessExpiredAndRefreshRevoked(t *testing.T) {
	store := &fakeStore{set: tokenstore.Set{
		Access:       "lapsed",
		Refresh:      "forgotten",
		AccessExpiry: fixedNow.Add(-time.Second),
	}}
	a := New(store, &fakeRefresher{err: oauth.ErrInvalidGrant}, nil)
	newAt(a)

	if _, err := a.Current(context.Background()); !errors.Is(err, ErrSignInRequired) {
		t.Fatalf("err = %v, want ErrSignInRequired", err)
	}
}

func TestRefreshWithoutRotatedTokenKeepsTheStoredOne(t *testing.T) {
	store := &fakeStore{set: tokenstore.Set{Access: "stale", Refresh: "rt", AccessExpiry: fixedNow.Add(-time.Minute)}}
	a := New(store, &fakeRefresher{tok: oauth.Token{Access: "fresh", Expiry: fixedNow.Add(time.Hour)}}, nil)
	newAt(a)

	if _, err := a.Current(context.Background()); err != nil {
		t.Fatalf("Current: %v", err)
	}
	if store.set.Refresh != "rt" {
		t.Fatalf("refresh = %q, want the stored one kept", store.set.Refresh)
	}
}

func TestRefusedRenewsThroughTheRefreshToken(t *testing.T) {
	store := &fakeStore{set: tokenstore.Set{Access: "refused", Refresh: "rt", AccessExpiry: fixedNow.Add(time.Hour)}}
	ref := &fakeRefresher{tok: oauth.Token{Access: "fresh", Refresh: "rotated", Expiry: fixedNow.Add(time.Hour)}}
	a := New(store, ref, nil)
	newAt(a)

	if err := a.Refused(context.Background()); err != nil {
		t.Fatalf("Refused: %v", err)
	}
	if store.set.Access != "fresh" || store.set.Refresh != "rotated" {
		t.Fatalf("renewed tokens not persisted: %+v", store.set)
	}
}

func TestRefusedKeepsTheAccessWhenRefreshFails(t *testing.T) {
	for _, rerr := range []error{oauth.ErrInvalidGrant, errors.New("status 503")} {
		store := &fakeStore{set: tokenstore.Set{Access: "at", Refresh: "rt", AccessExpiry: fixedNow.Add(time.Hour)}}
		a := New(store, &fakeRefresher{err: rerr}, nil)
		newAt(a)

		if err := a.Refused(context.Background()); !errors.Is(err, rerr) {
			t.Fatalf("err = %v, want %v", err, rerr)
		}
		if store.set.Access != "at" || store.set.Refresh != "rt" {
			t.Fatalf("a failed renewal must keep the stored tokens: %+v", store.set)
		}
	}
}

func TestRefusedWithoutRefreshTokenRequiresNothing(t *testing.T) {
	store := &fakeStore{set: tokenstore.Set{Access: "at", AccessExpiry: fixedNow.Add(time.Hour)}}
	ref := &fakeRefresher{}
	a := New(store, ref, nil)
	newAt(a)

	if err := a.Refused(context.Background()); err != nil {
		t.Fatalf("Refused: %v", err)
	}
	if ref.called != 0 || store.set.Access != "at" {
		t.Fatalf("no refresh token means nothing to renew: called=%d set=%+v", ref.called, store.set)
	}
}

func TestRefusedWithNothingStored(t *testing.T) {
	a := New(&fakeStore{empty: true}, &fakeRefresher{}, nil)
	newAt(a)

	if err := a.Refused(context.Background()); err != nil {
		t.Fatalf("Refused: %v", err)
	}
}

func TestHasSession(t *testing.T) {
	cases := map[string]struct {
		store *fakeStore
		want  bool
	}{
		"nothing stored": {&fakeStore{empty: true}, false},
		"signed out":     {&fakeStore{}, false},
		"access only":    {&fakeStore{set: tokenstore.Set{Access: "at"}}, true},
		"refresh only":   {&fakeStore{set: tokenstore.Set{Refresh: "rt"}}, true},
	}
	for name, c := range cases {
		if got := New(c.store, nil, nil).HasSession(); got != c.want {
			t.Errorf("%s: HasSession = %v, want %v", name, got, c.want)
		}
	}
}
