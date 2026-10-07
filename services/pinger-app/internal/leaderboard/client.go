// Package leaderboard reads and sets the member's public-leaderboard opt-in on their bearer token.
// The flag is the server's, so the app never caches it: it reflects what GET returns and writes
// through PUT. GET /pinger/leaderboard/opt-in and PUT the same path both carry {"optedIn": bool}.
package leaderboard

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"
)

// ErrUnauthorized means the server refused for want of a valid token, so the caller signs in again
// rather than treating it as a hard failure.
var ErrUnauthorized = errors.New("leaderboard: unauthorized")

// TokenSource hands back a valid access token. *auth.Authenticator is one.
type TokenSource interface {
	Token(ctx context.Context) (string, error)
}

// optIn is the request and response body; both sides use the same shape.
type optIn struct {
	OptedIn bool `json:"optedIn"`
}

// Client talks to one api base URL on tokens from a source.
type Client struct {
	base   string
	hc     *http.Client
	tokens TokenSource
}

// NewClient wires a client for an api base URL such as https://esa-blueshell.nl/api.
func NewClient(base string, tokens TokenSource) *Client {
	return &Client{
		base:   strings.TrimSuffix(base, "/"),
		hc:     &http.Client{Timeout: 10 * time.Second},
		tokens: tokens,
	}
}

// State reads whether the member currently appears on the public leaderboard.
func (c *Client) State(ctx context.Context) (bool, error) {
	return c.do(ctx, http.MethodGet, nil)
}

// Set opts the member in or out and returns the server's resulting state, so the UI reflects what
// actually took effect rather than what it asked for.
func (c *Client) Set(ctx context.Context, optedIn bool) (bool, error) {
	return c.do(ctx, http.MethodPut, &optIn{OptedIn: optedIn})
}

func (c *Client) do(ctx context.Context, method string, body *optIn) (bool, error) {
	token, err := c.tokens.Token(ctx)
	if err != nil {
		return false, err
	}
	var reader *bytes.Reader
	if body != nil {
		data, merr := json.Marshal(body)
		if merr != nil {
			return false, merr
		}
		reader = bytes.NewReader(data)
	} else {
		reader = bytes.NewReader(nil)
	}
	req, err := http.NewRequestWithContext(ctx, method, c.base+"/pinger/leaderboard/opt-in", reader)
	if err != nil {
		return false, err
	}
	req.Header.Set("Accept", "application/json")
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := c.hc.Do(req)
	if err != nil {
		return false, err
	}
	defer resp.Body.Close()
	if resp.StatusCode == http.StatusUnauthorized {
		return false, ErrUnauthorized
	}
	if resp.StatusCode/100 != 2 {
		return false, fmt.Errorf("leaderboard: status %d", resp.StatusCode)
	}
	var out optIn
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		return false, fmt.Errorf("leaderboard: %w", err)
	}
	return out.OptedIn, nil
}
