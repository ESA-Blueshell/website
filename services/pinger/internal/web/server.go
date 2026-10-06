// Package web serves the progress page members watch and the form admins steer the sender with.
package web

import (
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

	"github.com/ESA-Blueshell/website/services/pinger/internal/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/internal/paint"
)

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
		page:     template.Must(template.New("").Funcs(template.FuncMap{"thousands": thousands}).ParseFS(templateFS, "templates/*.html")),
		ams:      ams,
	}
	static, _ := fs.Sub(staticFS, "static")

	mux := http.NewServeMux()
	mux.HandleFunc("GET /{$}", s.index)
	mux.HandleFunc("GET /stats", s.statsFragment)
	mux.HandleFunc("POST /settings", s.saveSettings)
	mux.HandleFunc("GET /preview.png", s.previewImage)
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusNoContent) })
	mux.Handle("GET /static/", http.StripPrefix("/static/", http.FileServerFS(static)))
	// The api's session cookie is SameSite=None, so a form on another site would arrive signed in.
	return http.NewCrossOriginProtection().Handler(mux)
}

type bar struct{ Height string }

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
	ErrorTitle  string
	Paused      bool
	PauseLabel  string

	SweepTop    int
	SweepBottom int
	Bars        []bar
	CapPercent  int
	Countdown   countdown

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
		Paused:      state == string(paint.Paused),
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
	v.ChipLabel, v.ChipClass = chip(state)
	v.PauseLabel = "Pause"
	if v.Paused {
		v.PauseLabel = "Resume"
	}

	lit := v.PassPercent
	if v.IsClosed || v.IsIdle {
		lit = 0
	}
	v.SweepTop = min(100, lit+4)
	v.SweepBottom = max(0, lit-4)
	v.Bars, v.CapPercent = bars(st.Rates, cfg.RatePPS)

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

// bars scales the rate history so the cap line sits at four fifths height, visible whether
// the sender is hitting the cap or idling below it.
func bars(rates []uint64, ratePPS int) ([]bar, int) {
	scale := float64(ratePPS) * 1.25
	for _, r := range rates {
		if float64(r) > scale {
			scale = float64(r)
		}
	}
	if scale <= 0 {
		scale = 1
	}
	out := make([]bar, len(rates))
	for i, r := range rates {
		out[i] = bar{Height: strconv.FormatFloat(float64(r)/scale*100, 'f', 1, 64)}
	}
	return out, int(float64(ratePPS) / scale * 100)
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
	var s string
	switch v := n.(type) {
	case int:
		s = strconv.Itoa(v)
	case uint64:
		s = strconv.FormatUint(v, 10)
	default:
		s = fmt.Sprint(v)
	}
	for i := len(s) - 3; i > 0; i -= 3 {
		s = s[:i] + "," + s[i:]
	}
	return s
}
