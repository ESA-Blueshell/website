package oauth

import (
	"context"
	"fmt"
)

// BrowserLogin runs the full interactive flow: it starts a loopback listener, opens the system
// browser at the authorize endpoint, waits for the redirect and exchanges the code for tokens. The
// browser opener is injected so the oauth package pulls in no process-spawning dependency.
type BrowserLogin struct {
	Client *Client
	// Open launches the system browser at url. When it returns an error the url is still printed,
	// so a member on a headless setup can open it by hand.
	Open func(url string) error
}

// Run performs one login and returns the member's tokens.
func (b *BrowserLogin) Run(ctx context.Context) (Token, error) {
	pkce, err := NewPKCE()
	if err != nil {
		return Token{}, err
	}
	state, err := randomState()
	if err != nil {
		return Token{}, err
	}
	lb, err := NewLoopback(state)
	if err != nil {
		return Token{}, err
	}
	defer lb.Close()

	redirect := lb.RedirectURI()
	authURL := b.Client.AuthorizeURL(redirect, pkce.Challenge, state)
	if b.Open != nil {
		if err := b.Open(authURL); err != nil {
			fmt.Printf("Open this URL to sign in:\n%s\n", authURL)
		}
	} else {
		fmt.Printf("Open this URL to sign in:\n%s\n", authURL)
	}

	code, err := lb.Wait(ctx)
	if err != nil {
		return Token{}, err
	}
	return b.Client.Exchange(ctx, code, redirect, pkce.Verifier)
}
