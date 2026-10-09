// Package auth decides, on each request for a token, whether the stored access token still serves,
// whether a silent refresh renews it, or whether the member must sign in through the browser again.
// It is the seam the report loop calls; the browser flow itself is injected so the decision can be
// tested without a browser.
package auth

import (
	"context"
	"errors"
	"sync"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger-app/internal/oauth"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/tokenstore"
)

// skew renews an access token this long before it expires, so one never lapses mid-request.
const skew = 60 * time.Second

// ErrSignInRequired means there is no valid token and no usable refresh, so only an interactive
// browser login would renew it. Current returns this instead of opening a browser, so the report
// loop never pops a login the member did not ask for.
var ErrSignInRequired = errors.New("auth: sign-in required")

// LoginFunc runs the interactive browser login and returns fresh tokens. It is injected so the
// refresh decision is testable without opening a browser.
type LoginFunc func(ctx context.Context) (oauth.Token, error)

// Refresher exchanges a refresh token for fresh tokens. *oauth.Client is one.
type Refresher interface {
	Refresh(ctx context.Context, refresh string) (oauth.Token, error)
}

// store is the slice of tokenstore the authenticator needs, narrowed so a fake stands in for tests.
type store interface {
	Load() (tokenstore.Set, error)
	Save(tokenstore.Set) error
}

// Authenticator serves a valid access token, refreshing or re-logging as needed, and persists every
// token it obtains so a restart resumes without a browser login while the refresh token is valid.
type Authenticator struct {
	store   store
	refresh Refresher
	login   LoginFunc
	now     func() time.Time
	mu      sync.Mutex
	// cancelLogin abandons the browser login still waiting on its redirect, and attempt numbers
	// that login so a superseded one does not clear its successor's cancel.
	cancelLogin context.CancelFunc
	attempt     uint64
}

// New wires an authenticator over a token store, a refresher and the interactive login.
func New(st store, refresh Refresher, login LoginFunc) *Authenticator {
	return &Authenticator{store: st, refresh: refresh, login: login, now: time.Now}
}

// Current returns a valid access token without ever opening the browser, from the stored token or a
// silent refresh. It returns ErrSignInRequired when only an interactive login would renew it, so the
// report loop stays signed out rather than popping a browser. Serialized with the interactive login.
func (a *Authenticator) Current(ctx context.Context) (string, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	return a.current(ctx)
}

// current serves a token from the store or a silent refresh, assuming the lock is held. It never
// logs in: a missing or rejected refresh yields ErrSignInRequired.
func (a *Authenticator) current(ctx context.Context) (string, error) {
	set, err := a.store.Load()
	if err != nil && !errors.Is(err, tokenstore.ErrNoToken) {
		return "", err
	}
	if set.AccessValid(a.now(), skew) {
		return set.Access, nil
	}
	if set.HasRefresh() {
		tok, rerr := a.refresh.Refresh(ctx, set.Refresh)
		if rerr == nil {
			return a.persist(tok, set.Refresh)
		}
		// The skew asks for a refresh before the access token lapses; until it does, it still
		// serves whatever the refresh said.
		if set.AccessValid(a.now(), 0) {
			return set.Access, nil
		}
		// Only an expired or revoked refresh sends the member back to the browser. A transient
		// network error surfaces so the caller retries rather than signing out needlessly.
		if !errors.Is(rerr, oauth.ErrInvalidGrant) {
			return "", rerr
		}
	}
	return "", ErrSignInRequired
}

// Token returns a valid access token, opening the browser login when no token or refresh will
// serve. It backs the explicit sign-in action; the report loop uses Current so it never logs in on
// its own. The lock is not held across the browser flow: a member who closes that tab leaves the
// login waiting for good, so a later Token abandons it and opens a fresh one instead of queueing
// behind it.
func (a *Authenticator) Token(ctx context.Context) (string, error) {
	a.mu.Lock()
	tok, err := a.current(ctx)
	if err == nil || !errors.Is(err, ErrSignInRequired) {
		a.mu.Unlock()
		return tok, err
	}
	if a.cancelLogin != nil {
		a.cancelLogin()
	}
	loginCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	a.attempt++
	attempt := a.attempt
	a.cancelLogin = cancel
	a.mu.Unlock()

	logged, lerr := a.login(loginCtx)

	a.mu.Lock()
	defer a.mu.Unlock()
	if a.attempt == attempt {
		a.cancelLogin = nil
	}
	if lerr != nil {
		return "", lerr
	}
	return a.persist(logged, "")
}

// SignOut drops both tokens, so the member is signed out until they sign in again. The next Current
// returns ErrSignInRequired.
func (a *Authenticator) SignOut() error {
	a.mu.Lock()
	defer a.mu.Unlock()
	return a.store.Save(tokenstore.Set{})
}

// HasSession reports whether a sign-in is stored, without asking the server whether it still holds.
func (a *Authenticator) HasSession() bool {
	a.mu.Lock()
	defer a.mu.Unlock()
	set, err := a.store.Load()
	return err == nil && (set.Access != "" || set.HasRefresh())
}

// Refused renews through the refresh token after the server refused the stored access token. A
// failed renewal keeps that token: the api refuses a good token while its signing keys load after a
// restart, and dropping it then would sign the member out over a blip. The token is dropped only
// once it expires and no refresh renews it.
func (a *Authenticator) Refused(ctx context.Context) error {
	a.mu.Lock()
	defer a.mu.Unlock()
	set, err := a.store.Load()
	if errors.Is(err, tokenstore.ErrNoToken) {
		return nil
	}
	if err != nil {
		return err
	}
	if !set.HasRefresh() {
		return nil
	}
	tok, err := a.refresh.Refresh(ctx, set.Refresh)
	if err != nil {
		return err
	}
	_, err = a.persist(tok, set.Refresh)
	return err
}

// persist saves the obtained tokens and returns the access token, so every renewal is durable and
// a restart resumes from it. A response without a refresh token keeps the one it was renewed with.
func (a *Authenticator) persist(tok oauth.Token, previousRefresh string) (string, error) {
	refresh := tok.Refresh
	if refresh == "" {
		refresh = previousRefresh
	}
	if err := a.store.Save(tokenstore.Set{
		Access:       tok.Access,
		Refresh:      refresh,
		AccessExpiry: tok.Expiry,
	}); err != nil {
		return "", err
	}
	return tok.Access, nil
}
