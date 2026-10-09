package apipaint

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"math"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
)

// Share is the slice of the pixels the api asks this device to paint, as [From, To) of the unit
// interval, weighted by each device's rate. Devices is how many share the canvas.
type Share struct {
	From    float64 `json:"from"`
	To      float64 `json:"to"`
	Devices int     `json:"devices"`
}

// Everything is the share a device paints whenever it cannot learn its own: a failed or missing
// share must never stop the painting.
var Everything = Share{From: 0, To: 1}

// shareEpsilon is how far a bound must move before the sender is handed a new pixel set; the api
// recomputes shares from live rates, and every tiny drift would restart the sender's pass.
const shareEpsilon = 1e-3

// golden spreads consecutive pixel indices evenly over the unit interval, so every share covers the
// whole image rather than one band of it. The api assumes the same rule; change one, change the
// other.
const golden = 0.6180339887498949

// Keeps reports whether pixel i, counted in the order the poller places the descriptor's
// placements, belongs to share s.
func Keeps(i int, s Share) bool {
	x := float64(i+1) * golden
	f := x - math.Floor(x)
	return f >= s.From && f < s.To
}

// ShareFilter sits between the poller and the sender and passes on only this device's share of the
// placed pixels. It re-applies when the pixels change or the share moves by more than shareEpsilon.
type ShareFilter struct {
	inner PixelSink

	mu     sync.Mutex
	all    []canvas.Pixel
	placed bool
	share  Share
}

func NewShareFilter(inner PixelSink) *ShareFilter {
	return &ShareFilter{inner: inner, share: Everything}
}

func (f *ShareFilter) SetPixels(pixels []canvas.Pixel) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.all, f.placed = pixels, true
	f.apply()
}

func (f *ShareFilter) SetShare(s Share) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if math.Abs(s.From-f.share.From) <= shareEpsilon && math.Abs(s.To-f.share.To) <= shareEpsilon {
		return
	}
	slog.Info("paint share", "from", s.From, "to", s.To, "devices", s.Devices)
	f.share = s
	if f.placed {
		f.apply()
	}
}

// apply hands the inner sink a fresh slice, since the sender keeps the one it is given.
func (f *ShareFilter) apply() {
	if (f.share.From <= 0 && f.share.To >= 1) || f.all == nil {
		f.inner.SetPixels(f.all)
		return
	}
	kept := make([]canvas.Pixel, 0, int(float64(len(f.all))*(f.share.To-f.share.From))+1)
	for i, px := range f.all {
		if Keeps(i, f.share) {
			kept = append(kept, px)
		}
	}
	f.inner.SetPixels(kept)
}

// Authorizer sets this client's credential on a request, the same one it reports with. It returns
// false when there is none, and the device then paints everything without asking.
type Authorizer func(ctx context.Context, req *http.Request) bool

// ServiceToken authorizes as SiteCie with the service token. An empty token has no credential.
func ServiceToken(token string) Authorizer {
	return func(_ context.Context, req *http.Request) bool {
		if token == "" {
			return false
		}
		req.Header.Set("X-Pinger-Service-Token", token)
		return true
	}
}

// Bearer authorizes with a member's access token. A source that fails, as it does while signed
// out, has no credential.
func Bearer(token func(ctx context.Context) (string, error)) Authorizer {
	return func(ctx context.Context, req *http.Request) bool {
		tok, err := token(ctx)
		if err != nil || tok == "" {
			return false
		}
		req.Header.Set("Authorization", "Bearer "+tok)
		return true
	}
}

// ShareStream holds the api's share stream open and applies each share as it arrives. Across a
// drop it keeps the last share for grace, then paints everything until the stream returns. An api
// without the stream answers 404, and the plain share read stands in for it.
type ShareStream struct {
	base      string
	deviceID  string
	authorize Authorizer
	hc        *http.Client

	backoffMin, backoffMax time.Duration
	grace                  time.Duration
	// idle is how long the stream may stay silent before it is taken for dead; the api sends a
	// heartbeat every 15 s.
	idle time.Duration

	mu    sync.Mutex
	gen   uint64
	apply func(Share)
}

func NewShareStream(base, deviceID string, authorize Authorizer) *ShareStream {
	return &ShareStream{
		base:      strings.TrimSuffix(base, "/"),
		deviceID:  deviceID,
		authorize: authorize,
		// No overall timeout: the stream body stays open for good, and idle catches a dead one.
		hc:         &http.Client{Transport: forwardedHTTPS{next: http.DefaultTransport}},
		backoffMin: 500 * time.Millisecond,
		backoffMax: 5 * time.Second,
		grace:      15 * time.Second,
		idle:       45 * time.Second,
	}
}

var errStreamMissing = errors.New("share stream: not found")

// Run streams shares into apply until ctx ends. apply is never called concurrently.
func (s *ShareStream) Run(ctx context.Context, apply func(Share)) {
	s.apply = apply
	backoff := s.backoffMin
	for ctx.Err() == nil {
		err := s.stream(ctx, func() { backoff = s.backoffMin })
		switch {
		case ctx.Err() != nil:
		case errors.Is(err, errNoCredential):
			s.set(Everything)
			backoff = s.backoffMax
		case errors.Is(err, errStreamMissing):
			share, gerr := s.fetch(ctx)
			if gerr != nil {
				slog.Warn("read paint share", "err", gerr)
				share = Everything
			}
			s.set(share)
			backoff = s.backoffMax
		default:
			slog.Warn("paint share stream", "err", err)
			s.expireAfter(s.grace)
		}
		select {
		case <-ctx.Done():
		case <-time.After(backoff):
		}
		backoff = min(backoff*2, s.backoffMax)
	}
}

var errNoCredential = errors.New("share: no credential")

// set applies a share and disarms any pending fall back to everything.
func (s *ShareStream) set(share Share) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.gen++
	s.apply(share)
}

// expireAfter falls back to everything after d unless a share lands first. Each drop arms one;
// only the oldest still pending can fire, since any apply moves gen on.
func (s *ShareStream) expireAfter(d time.Duration) {
	s.mu.Lock()
	gen := s.gen
	s.mu.Unlock()
	time.AfterFunc(d, func() {
		s.mu.Lock()
		defer s.mu.Unlock()
		if s.gen == gen {
			s.gen++
			s.apply(Everything)
		}
	})
}

func (s *ShareStream) request(ctx context.Context, path string) (*http.Request, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, s.base+path+"?deviceId="+url.QueryEscape(s.deviceID), nil)
	if err != nil {
		return nil, err
	}
	if !s.authorize(ctx, req) {
		return nil, errNoCredential
	}
	return req, nil
}

// stream reads one connection until it drops; connected resets the reconnect backoff.
func (s *ShareStream) stream(ctx context.Context, connected func()) error {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	req, err := s.request(ctx, "/pinger/report/share/stream")
	if err != nil {
		return err
	}
	req.Header.Set("Accept", "text/event-stream")
	watchdog := time.AfterFunc(s.idle, cancel)
	defer watchdog.Stop()
	resp, err := s.hc.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode == http.StatusNotFound {
		return errStreamMissing
	}
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("share stream: status %d", resp.StatusCode)
	}
	connected()

	sc := bufio.NewScanner(resp.Body)
	var data []byte
	for sc.Scan() {
		watchdog.Reset(s.idle)
		line := sc.Bytes()
		switch {
		case len(line) == 0:
			if data == nil {
				continue
			}
			share, perr := parseShare(data)
			if perr != nil {
				slog.Warn("paint share event", "err", perr)
				share = Everything
			}
			s.set(share)
			data = nil
		case bytes.HasPrefix(line, []byte("data:")):
			if data != nil {
				data = append(data, '\n')
			}
			data = append(data, bytes.TrimPrefix(bytes.TrimPrefix(line, []byte("data:")), []byte(" "))...)
		}
	}
	if err := sc.Err(); err != nil {
		return err
	}
	return io.ErrUnexpectedEOF
}

// fetch reads the share once, for an api that serves no stream.
func (s *ShareStream) fetch(ctx context.Context) (Share, error) {
	ctx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	req, err := s.request(ctx, "/pinger/report/share")
	if err != nil {
		return Share{}, err
	}
	req.Header.Set("Accept", "application/json")
	resp, err := s.hc.Do(req)
	if err != nil {
		return Share{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return Share{}, fmt.Errorf("paint share: status %d", resp.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 4<<10))
	if err != nil {
		return Share{}, err
	}
	return parseShare(body)
}

func parseShare(body []byte) (Share, error) {
	var raw struct {
		From *float64 `json:"from"`
		To   *float64 `json:"to"`
		Devs int      `json:"devices"`
	}
	if err := json.Unmarshal(body, &raw); err != nil {
		return Share{}, fmt.Errorf("paint share: %w", err)
	}
	if raw.From == nil || raw.To == nil {
		return Share{}, errors.New("paint share: missing bounds")
	}
	s := Share{From: *raw.From, To: *raw.To, Devices: raw.Devs}
	if !(s.From >= 0 && s.From < s.To && s.To <= 1) {
		return Share{}, fmt.Errorf("paint share: bounds [%v, %v)", s.From, s.To)
	}
	return s, nil
}
