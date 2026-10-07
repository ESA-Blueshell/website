// Package report turns the sender's running counters into the status the api accrues, and posts it
// on the member's bearer. The server accepts {online, pps, sent, errors}; it rejects an
// unauthenticated post, which the caller handles by refreshing or logging in again.
package report

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

// ErrUnauthorized means the server refused the post for want of a valid token. The caller refreshes
// or re-logs rather than treating it as a hard failure.
var ErrUnauthorized = errors.New("report: unauthorized")

// maxPPS mirrors the server's cap, so a stray sample cannot be read as a real rate.
const maxPPS = paint.MaxRatePPS

// Report is the body POST /pinger/report accepts. The field names match the api DTO exactly.
type Report struct {
	Online bool   `json:"online"`
	PPS    int    `json:"pps"`
	Sent   uint64 `json:"sent"`
	Errors int    `json:"errors"`
}

// Build reads a report off the sender's snapshot. Online is whether it is sending now; the counts
// are this session's cumulative tallies, which the server turns into the member's durable total.
func Build(s paint.Stats) Report {
	pps := int(min(s.ActualPPS, uint64(maxPPS)))
	errCount := int(min(s.Errors, uint64(maxInt)))
	return Report{
		Online: s.State == paint.Running,
		PPS:    pps,
		Sent:   s.Sent,
		Errors: errCount,
	}
}

const maxInt = int(^uint(0) >> 1)

// TokenSource hands back a valid access token, refreshing or logging in as needed. The poster asks
// for one before each post so a token that expired between posts is renewed first.
type TokenSource interface {
	Token(ctx context.Context) (string, error)
}

// Poster sends reports to one api base URL on tokens from a source.
type Poster struct {
	Base   string
	HTTP   *http.Client
	Tokens TokenSource
}

// NewPoster wires a poster for an api base URL such as https://esa-blueshell.nl/api.
func NewPoster(base string, tokens TokenSource) *Poster {
	return &Poster{
		Base:   strings.TrimSuffix(base, "/"),
		HTTP:   &http.Client{Timeout: 10 * time.Second},
		Tokens: tokens,
	}
}

// Post sends one report. A 401 comes back as ErrUnauthorized so the caller can re-authenticate.
func (p *Poster) Post(ctx context.Context, r Report) error {
	token, err := p.Tokens.Token(ctx)
	if err != nil {
		return err
	}
	body, err := json.Marshal(r)
	if err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, p.Base+"/pinger/report", bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := p.client().Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode == http.StatusUnauthorized {
		return ErrUnauthorized
	}
	if resp.StatusCode/100 != 2 {
		return fmt.Errorf("report: status %d", resp.StatusCode)
	}
	return nil
}

func (p *Poster) client() *http.Client {
	if p.HTTP != nil {
		return p.HTTP
	}
	return http.DefaultClient
}
