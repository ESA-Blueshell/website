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
}

// New wires an authenticator over a token store, a refresher and the interactive login.
func New(st store, refresh Refresher, login LoginFunc) *Authenticator {
	return &Authenticator{store: st, refresh: refresh, login: login, now: time.Now}
}

// Token returns a valid access token. It uses the stored one while it is fresh, refreshes silently
// when a refresh token is present, and only falls back to the browser when there is no token or the
// refresh is rejected. Calls are serialized so a burst of posts triggers at most one login.
func (a *Authenticator) Token(ctx context.Context) (string, error) {
	a.mu.Lock()
	defer a.mu.Unlock()

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
			return a.persist(tok)
		}
		// Only an expired or revoked refresh sends the member back to the browser. A transient
		// network error surfaces so the caller retries rather than opening a browser needlessly.
		if !errors.Is(rerr, oauth.ErrInvalidGrant) {
			return "", rerr
		}
	}
	tok, lerr := a.login(ctx)
	if lerr != nil {
		return "", lerr
	}
	return a.persist(tok)
}

// Invalidate drops the stored access token while keeping the refresh token, so the next Token call
// renews rather than reusing a token the server has refused. A revoked refresh then falls through
// to a browser login. It is the recovery path for a 401 on an access token that had not yet expired.
func (a *Authenticator) Invalidate() error {
	a.mu.Lock()
	defer a.mu.Unlock()
	set, err := a.store.Load()
	if errors.Is(err, tokenstore.ErrNoToken) {
		return nil
	}
	if err != nil {
		return err
	}
	set.Access = ""
	set.AccessExpiry = time.Time{}
	return a.store.Save(set)
}

// persist saves the obtained tokens and returns the access token, so every renewal is durable and
// a restart resumes from it.
func (a *Authenticator) persist(tok oauth.Token) (string, error) {
	if err := a.store.Save(tokenstore.Set{
		Access:       tok.Access,
		Refresh:      tok.Refresh,
		AccessExpiry: tok.Expiry,
	}); err != nil {
		return "", err
	}
	return tok.Access, nil
}
