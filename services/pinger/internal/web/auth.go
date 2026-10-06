package web

import (
	"fmt"
	"net/http"
	"slices"
	"strings"
	"time"
)

// Auth decides whether a request may change the sender's settings.
type Auth interface {
	IsAdmin(r *http.Request) (bool, error)
}

// APIAuth replays the caller's cookies to the api's forward-auth check. The pod runs on the host
// network, so any pod in the cluster can reach it with a forged X-User-Groups; the api's answer
// cannot be forged.
type APIAuth struct {
	endpoint string
	host     string
	client   *http.Client
}

func NewAPIAuth(apiBase, host string) *APIAuth {
	return &APIAuth{
		endpoint: strings.TrimSuffix(apiBase, "/") + "/oauth2/forward-auth",
		host:     host,
		client:   &http.Client{Timeout: 5 * time.Second},
	}
}

func (a *APIAuth) IsAdmin(r *http.Request) (bool, error) {
	req, err := http.NewRequestWithContext(r.Context(), http.MethodGet, a.endpoint, nil)
	if err != nil {
		return false, err
	}
	req.Header.Set("Cookie", r.Header.Get("Cookie"))
	req.Header.Set("X-Forwarded-Host", a.host)
	req.Header.Set("X-Forwarded-Proto", "https")
	req.Header.Set("X-Forwarded-Uri", "/settings")
	req.Header.Set("Accept", "application/json")
	resp, err := a.client.Do(req)
	if err != nil {
		return false, fmt.Errorf("forward-auth: %w", err)
	}
	defer resp.Body.Close()
	switch resp.StatusCode {
	case http.StatusOK:
		return slices.Contains(strings.Split(resp.Header.Get("X-User-Groups"), ","), "ADMIN"), nil
	case http.StatusUnauthorized, http.StatusForbidden:
		return false, nil
	}
	return false, fmt.Errorf("forward-auth: status %d", resp.StatusCode)
}
