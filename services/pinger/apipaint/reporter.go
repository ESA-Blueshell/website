package apipaint

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"time"
)

// ReportStats is the point-in-time sample the reporter sends. It is the sender's own counters,
// read each tick; the api accrues the durable SiteCie tally from the growing Sent counter.
type ReportStats struct {
	Online bool
	PPS    int
	Sent   uint64
	Errors uint64
}

// reportBody is the JSON the api's POST /pinger/report accepts.
type reportBody struct {
	Online bool  `json:"online"`
	PPS    int   `json:"pps"`
	Sent   int64 `json:"sent"`
	Errors int   `json:"errors"`
}

// Reporter POSTs the cluster painter's contribution to the api as SiteCie, authenticating with the
// service token. It samples the sender's counters on each tick rather than holding its own, so the
// api sees the same totals the watch page does.
type Reporter struct {
	base     string
	token    string
	interval time.Duration
	stats    func() ReportStats
	hc       *http.Client
}

// NewReporter wires a reporter. token is the service token read from the environment; it is a
// secret and is only ever set on the request header, never logged.
func NewReporter(base, token string, interval time.Duration, stats func() ReportStats) *Reporter {
	return &Reporter{
		base:     trimSlash(base),
		token:    token,
		interval: interval,
		stats:    stats,
		hc:       &http.Client{Timeout: 10 * time.Second},
	}
}

func trimSlash(s string) string {
	for len(s) > 0 && s[len(s)-1] == '/' {
		s = s[:len(s)-1]
	}
	return s
}

// Run reports once on start and then on every tick until ctx ends. With no token the painter has
// no SiteCie credential, so it reports nothing rather than hammering the api with 401s.
func (r *Reporter) Run(ctx context.Context) {
	if r.token == "" {
		slog.Warn("pinger report disabled: no service token set")
		return
	}
	tick := time.NewTicker(r.interval)
	defer tick.Stop()
	for {
		if err := r.post(ctx); err != nil {
			slog.Warn("post pinger report", "err", err)
		}
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
		}
	}
}

func (r *Reporter) post(ctx context.Context) error {
	st := r.stats()
	body, err := json.Marshal(reportBody{Online: st.Online, PPS: st.PPS, Sent: int64(st.Sent), Errors: int(st.Errors)})
	if err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, r.base+"/pinger/report", bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Pinger-Service-Token", r.token)
	resp, err := r.hc.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	_, _ = io.Copy(io.Discard, resp.Body)
	if resp.StatusCode != http.StatusNoContent {
		return fmt.Errorf("pinger report: status %d", resp.StatusCode)
	}
	return nil
}
