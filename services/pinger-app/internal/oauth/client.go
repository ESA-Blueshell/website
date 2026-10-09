package oauth

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// ErrInvalidGrant is returned when the token endpoint rejects the code or refresh token. For a
// refresh it means the refresh token expired or was revoked, so the caller must log in again.
var ErrInvalidGrant = errors.New("oauth: invalid grant")

// Token is one token-endpoint response. Expiry is computed from expires_in at receipt. The fields
// hold secrets and are never logged.
type Token struct {
	Access  string
	Refresh string
	Expiry  time.Time
}

// tokenResponse is the JSON the token endpoint returns.
type tokenResponse struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	ExpiresIn    int    `json:"expires_in"`
	TokenType    string `json:"token_type"`
	Error        string `json:"error"`
}

// Client talks to one authorization server, identified by its issuer base URL. The authorize and
// token endpoints hang off that base (Spring Authorization Server defaults).
type Client struct {
	Base     string
	ClientID string
	HTTP     *http.Client
	now      func() time.Time
}

// NewClient builds a client for an issuer base URL such as https://esa-blueshell.nl/api.
func NewClient(base, clientID string) *Client {
	return &Client{
		Base:     strings.TrimSuffix(base, "/"),
		ClientID: clientID,
		HTTP:     &http.Client{Timeout: 20 * time.Second, CheckRedirect: noRedirect},
		now:      time.Now,
	}
}

// noRedirect keeps a token call on the token endpoint: the api answers a client it cannot
// authenticate with a redirect to its login page, which must read as a refusal, not as HTML.
func noRedirect(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }

func (c *Client) httpClient() *http.Client {
	if c.HTTP != nil {
		return c.HTTP
	}
	return http.DefaultClient
}

func (c *Client) clock() time.Time {
	if c.now != nil {
		return c.now()
	}
	return time.Now()
}

// AuthorizeURL is the browser URL that asks the member to sign in and consent, carrying the PKCE
// challenge and the loopback redirect. The state ties the callback back to this request.
func (c *Client) AuthorizeURL(redirectURI, challenge, state string) string {
	q := url.Values{}
	q.Set("response_type", "code")
	q.Set("client_id", c.ClientID)
	q.Set("redirect_uri", redirectURI)
	q.Set("scope", "openid")
	q.Set("state", state)
	q.Set("code_challenge", challenge)
	q.Set("code_challenge_method", "S256")
	return c.Base + "/oauth2/authorize?" + q.Encode()
}

// Exchange trades the authorization code for tokens, proving possession of the verifier.
func (c *Client) Exchange(ctx context.Context, code, redirectURI, verifier string) (Token, error) {
	form := url.Values{}
	form.Set("grant_type", "authorization_code")
	form.Set("code", code)
	form.Set("redirect_uri", redirectURI)
	form.Set("client_id", c.ClientID)
	form.Set("code_verifier", verifier)
	return c.token(ctx, form)
}

// Refresh exchanges a refresh token for a fresh access token. The server rotates refresh tokens, so
// the returned token carries the new refresh value the caller must persist.
func (c *Client) Refresh(ctx context.Context, refresh string) (Token, error) {
	form := url.Values{}
	form.Set("grant_type", "refresh_token")
	form.Set("refresh_token", refresh)
	form.Set("client_id", c.ClientID)
	return c.token(ctx, form)
}

func (c *Client) token(ctx context.Context, form url.Values) (Token, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.Base+"/oauth2/token", strings.NewReader(form.Encode()))
	if err != nil {
		return Token{}, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Accept", "application/json")

	resp, err := c.httpClient().Do(req)
	if err != nil {
		return Token{}, err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return Token{}, err
	}

	var tr tokenResponse
	// A non-2xx with no JSON body still needs a clear error; decode best-effort.
	_ = json.Unmarshal(body, &tr)
	if resp.StatusCode != http.StatusOK {
		if refused(resp.StatusCode, tr.Error) {
			return Token{}, ErrInvalidGrant
		}
		return Token{}, fmt.Errorf("oauth token: status %d", resp.StatusCode)
	}
	if tr.AccessToken == "" {
		return Token{}, errors.New("oauth token: response had no access_token")
	}
	return Token{
		Access:  tr.AccessToken,
		Refresh: tr.RefreshToken,
		Expiry:  c.clock().Add(time.Duration(tr.ExpiresIn) * time.Second),
	}, nil
}

// refused reports whether the token endpoint turned the grant or this client down for good, so only
// a fresh browser login renews it. A 5xx or a network error is not a refusal and is retried.
func refused(status int, code string) bool {
	switch code {
	case "invalid_grant", "invalid_client", "unauthorized_client":
		return true
	}
	return status >= 300 && status < 400
}
