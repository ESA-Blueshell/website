// Package web serves the progress page members watch and the form admins steer the sender with.
package web

import (
	"bytes"
	"context"
	"embed"
	"fmt"
	"html/template"
	"io/fs"
	"log/slog"
	"net/http"
	"slices"
	"strconv"
	"strings"
	"time"

	"github.com/coder/websocket"

	"github.com/ESA-Blueshell/website/services/pinger/internal/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/internal/paint"
)

// liveInterval is how often the socket pushes a fresh live region. The sender samples its rate
// once a second, so a faster push would send the same numbers; the bar glides between pushes.
const liveInterval = time.Second

// socketMaxAge bounds a socket's life. Traefik's forward-auth gates the upgrade, not the open
// socket, so this is what re-gates a viewer whose membership lapsed: the socket closes, the page
// reconnects, and forward-auth runs again. The feed is read-only event telemetry, so a short
// window of staleness is harmless; this keeps it short.
const socketMaxAge = 15 * time.Minute

//go:embed templates/*.html
var templateFS embed.FS

//go:embed static
var staticFS embed.FS

type StatsSource interface {
	Snapshot() paint.Stats
}

type SettingsStore interface {
	Current() paint.Settings
	Save(ctx context.Context, v paint.Settings) error
}

type server struct {
	stats    StatsSource
	settings SettingsStore
	auth     Auth
	preview  []byte
	page     *template.Template
	ams      *time.Location
}

func NewServer(stats StatsSource, settings SettingsStore, auth Auth, preview []byte) http.Handler {
	ams, err := time.LoadLocation("Europe/Amsterdam")
	if err != nil {
		panic(err)
	}
	s := &server{
		stats:    stats,
		settings: settings,
		auth:     auth,
		preview:  preview,
		page:     template.Must(template.New("").Funcs(template.FuncMap{"thousands": thousands, "compact": compact}).ParseFS(templateFS, "templates/*.html")),
		ams:      ams,
	}
	static, _ := fs.Sub(staticFS, "static")

	mux := http.NewServeMux()
	mux.HandleFunc("GET /{$}", s.index)
	mux.HandleFunc("GET /stats", s.statsFragment)
	mux.HandleFunc("GET /ws", s.socket)
	mux.HandleFunc("POST /settings", s.saveSettings)
	mux.HandleFunc("GET /preview.png", s.previewImage)
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusNoContent) })
	mux.Handle("GET /static/", http.StripPrefix("/static/", http.FileServerFS(static)))
	// The api's session cookie is SameSite=None, so a form on another site would arrive signed in.
	return http.NewCrossOriginProtection().Handler(mux)
}

type preset struct {
	Label string
	Value int
	On    bool
}

type countdown struct{ Days, Hours, Minutes, Seconds string }

type view struct {
	Stats       paint.Stats
	Settings    paint.Settings
	Prefix      string
	PrefixLabel string
	PassPercent int
	LastErrorAt string
	ShowForm    bool
	MaxRatePPS  int

	State       string
	IsRunning   bool
	IsIdle      bool
	IsClosed    bool
	ShowNumbers bool
	ShowErrors  bool
	ChipLabel   string
	ChipClass   string
	Failing     bool
	ErrorTitle  string

	PassEta    string
	RatePoints string
	RateArea   string
	RateCount  int
	RateLastY  string
	RateCeil   uint64
	CapPercent int
	Countdown  countdown

	Presets []preset
}

func (s *server) view(r *http.Request) view {
	st := s.stats.Snapshot()
	cfg := s.settings.Current()
	state := string(st.State)
	v := view{
		Stats:       st,
		Settings:    cfg,
		Prefix:      cfg.Prefix.String(),
		MaxRatePPS:  paint.MaxRatePPS,
		State:       state,
		IsRunning:   state == string(paint.Running),
		IsIdle:      state == string(paint.Idle),
		IsClosed:    state == string(paint.Closed),
		ShowNumbers: state != string(paint.Closed),
		ShowForm:    slices.Contains(strings.Split(r.Header.Get("X-User-Groups"), ","), "ADMIN"),
	}
	if st.PassTotal > 0 {
		v.PassPercent = st.PassDone * 100 / st.PassTotal
	}
	if !st.LastErrorAt.IsZero() {
		v.LastErrorAt = st.LastErrorAt.In(s.ams).Format("Mon 15:04:05")
	}
	v.ShowErrors = st.Errors > 0 && v.ShowNumbers
	if st.Errors == 1 {
		v.ErrorTitle = "1 send error"
	} else {
		v.ErrorTitle = thousands(st.Errors) + " send errors"
	}

	v.PrefixLabel = "No prefix"
	if v.Prefix != "" {
		v.PrefixLabel = v.Prefix
	}
	v.Failing = st.Failing && v.IsRunning
	v.ChipLabel, v.ChipClass = chip(state)
	if v.Failing {
		v.ChipLabel, v.ChipClass = "Sending is failing", "state-chip state-chip--failing"
	}

	v.PassEta = passEta(st.PassTotal, st.ActualPPS)
	v.RatePoints, v.RateArea, v.CapPercent, v.RateLastY, v.RateCeil = rateLine(st.Rates, cfg.RatePPS)
	v.RateCount = len(st.Rates)

	v.Countdown = s.countdown()
	v.Presets = presets(cfg.RatePPS)
	return v
}

func chip(state string) (label, class string) {
	switch state {
	case string(paint.Running):
		return "Painting", "state-chip state-chip--running"
	case string(paint.Paused):
		return "Paused", "state-chip state-chip--paused"
	case string(paint.Closed):
		return "Opens Fri 9 Oct, 18:00", "state-chip state-chip--closed"
	default:
		return "Waiting for the prefix", "state-chip state-chip--idle"
	}
}

var presetRates = []int{10_000, 25_000, 50_000, 100_000}

func presets(current int) []preset {
	out := make([]preset, len(presetRates))
	for i, n := range presetRates {
		out[i] = preset{Label: thousands(n), Value: n, On: n == current}
	}
	return out
}

// rateLine turns the rate history into an SVG line across a 0..100 box, the polygon that fills
// under it, the cap's height, the latest point's height and the value at the top of the frame.
// The top of the frame is the cap and a half, so the cap sits at two thirds height as a fixed
// reference with room above it; a burst over the cap lifts the top past that instead of clipping.
// x runs 0 to 100 across the samples, y is inverted because SVG's origin is top-left.
func rateLine(rates []uint64, ratePPS int) (points, area string, capPercent int, lastY string, ceil uint64) {
	if len(rates) == 0 {
		return "", "", 0, "", 0
	}
	var peak uint64
	for _, r := range rates {
		if r > peak {
			peak = r
		}
	}
	top := float64(ratePPS) * 1.5
	if float64(peak) > top {
		top = float64(peak) * 1.02
	}
	if top <= 0 {
		top = 1
	}
	y := func(r uint64) float64 { return max(0, 100-float64(r)/top*100) }

	span := float64(len(rates) - 1)
	if span == 0 {
		span = 1
	}
	var b strings.Builder
	for i, r := range rates {
		if i > 0 {
			b.WriteByte(' ')
		}
		fmt.Fprintf(&b, "%.2f,%.2f", float64(i)/span*100, y(r))
	}
	points = b.String()
	area = "0,100 " + points + " 100,100"
	lastY = strconv.FormatFloat(y(rates[len(rates)-1]), 'f', 2, 64)
	return points, area, int(float64(ratePPS) / top * 100), lastY, uint64(top)
}

// passEta estimates how long one full paint of the logo takes at the current rate.
func passEta(total int, pps uint64) string {
	if pps == 0 || total <= 0 {
		return "—"
	}
	secs := int(uint64(total) / pps)
	if secs < 60 {
		return strconv.Itoa(secs) + "s"
	}
	return fmt.Sprintf("%dm %02ds", secs/60, secs%60)
}

func (s *server) countdown() countdown {
	left := time.Until(paint.EventWindow().Start)
	if left < 0 {
		left = 0
	}
	return countdown{
		Days:    fmt.Sprintf("%02d", int(left.Hours())/24),
		Hours:   fmt.Sprintf("%02d", int(left.Hours())%24),
		Minutes: fmt.Sprintf("%02d", int(left.Minutes())%60),
		Seconds: fmt.Sprintf("%02d", int(left.Seconds())%60),
	}
}

func (s *server) index(w http.ResponseWriter, r *http.Request) {
	s.render(w, "page", s.view(r))
}

func (s *server) statsFragment(w http.ResponseWriter, r *http.Request) {
	s.render(w, "live", s.view(r))
}

// socket pushes the live region over a WebSocket, once a second. The viewer's browser swaps it
// in and interpolates the pass bar between pushes. A failed write means the viewer left, so the
// loop ends. The default origin check rejects a socket opened from another site.
func (s *server) socket(w http.ResponseWriter, r *http.Request) {
	c, err := websocket.Accept(w, r, nil)
	if err != nil {
		return
	}
	defer c.CloseNow()
	ctx, cancel := context.WithTimeout(c.CloseRead(r.Context()), socketMaxAge)
	defer cancel()

	tick := time.NewTicker(liveInterval)
	defer tick.Stop()
	for {
		var buf bytes.Buffer
		if err := s.page.ExecuteTemplate(&buf, "live", s.view(r)); err != nil {
			slog.Error("render live", "err", err)
			return
		}
		if err := c.Write(ctx, websocket.MessageText, buf.Bytes()); err != nil {
			return
		}
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
		}
	}
}

func (s *server) render(w http.ResponseWriter, name string, v view) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	if err := s.page.ExecuteTemplate(w, name, v); err != nil {
		slog.Error("render", "template", name, "err", err)
	}
}

func (s *server) saveSettings(w http.ResponseWriter, r *http.Request) {
	admin, err := s.auth.IsAdmin(r)
	if err != nil {
		slog.Error("admin check", "err", err)
		http.Error(w, "could not check who you are", http.StatusBadGateway)
		return
	}
	if !admin {
		http.Error(w, "only admins change the settings", http.StatusForbidden)
		return
	}
	next, problem := parseSettings(r)
	if problem != "" {
		http.Error(w, problem, http.StatusBadRequest)
		return
	}
	if err := s.settings.Save(r.Context(), next); err != nil {
		slog.Error("save settings", "err", err)
		http.Error(w, "could not save the settings", http.StatusBadGateway)
		return
	}
	slog.Info("settings changed", "prefix", next.Prefix.String(), "rate", next.RatePPS, "paused", next.Paused)
	http.Redirect(w, r, "/", http.StatusSeeOther)
}

func parseSettings(r *http.Request) (paint.Settings, string) {
	var out paint.Settings
	if raw := strings.TrimSpace(r.FormValue("prefix")); raw != "" {
		p, err := canvas.ParsePrefix(raw)
		if err != nil {
			return out, err.Error()
		}
		out.Prefix = p
	}
	rate, err := strconv.Atoi(strings.TrimSpace(r.FormValue("rate")))
	if err != nil || rate < 1 || rate > paint.MaxRatePPS {
		return out, "rate must be a whole number from 1 to " + thousands(paint.MaxRatePPS)
	}
	out.RatePPS = rate
	out.Paused = r.FormValue("paused") != ""
	return out, ""
}

func (s *server) previewImage(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "image/png")
	w.Header().Set("Cache-Control", "public, max-age=3600")
	_, _ = w.Write(s.preview)
}

func thousands(n any) string {
	s := strconv.FormatUint(toUint64(n), 10)
	for i := len(s) - 3; i > 0; i -= 3 {
		s = s[:i] + "," + s[i:]
	}
	return s
}

// compact writes a big tally as 1.2M or 3.4B, so a running total never grows wider than its
// cell. Below a thousand it stays exact.
func compact(n any) string {
	v := toUint64(n)
	for _, u := range []struct {
		div    uint64
		suffix string
	}{{1e12, "T"}, {1e9, "B"}, {1e6, "M"}, {1e3, "K"}} {
		if v >= u.div {
			s := strconv.FormatFloat(float64(v)/float64(u.div), 'f', 1, 64)
			return strings.TrimSuffix(s, ".0") + u.suffix
		}
	}
	return strconv.FormatUint(v, 10)
}

func toUint64(n any) uint64 {
	switch v := n.(type) {
	case int:
		if v < 0 {
			return 0
		}
		return uint64(v)
	case uint64:
		return v
	default:
		return 0
	}
}
