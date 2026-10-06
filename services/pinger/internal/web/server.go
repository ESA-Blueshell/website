// Package web serves the progress page members watch and the form admins steer the sender with.
package web

import (
	"context"
	"embed"
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

// MaxRatePPS bounds what the form accepts: the event bans prefixes that ping excessively hard.
const MaxRatePPS = 200_000

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
	return mux
}

type view struct {
	Stats       paint.Stats
	Settings    paint.Settings
	Prefix      string
	PassPercent int
	LastErrorAt string
	ShowForm    bool
}

func (s *server) view(r *http.Request) view {
	st := s.stats.Snapshot()
	cfg := s.settings.Current()
	v := view{Stats: st, Settings: cfg}
	if !cfg.Prefix.IsZero() {
		v.Prefix = cfg.Prefix.String()
	}
	if st.PassTotal > 0 {
		v.PassPercent = st.PassDone * 100 / st.PassTotal
	}
	if !st.LastErrorAt.IsZero() {
		v.LastErrorAt = st.LastErrorAt.In(s.ams).Format("Mon 15:04:05")
	}
	// The form is only shown on the header's say-so; saving it asks the api.
	v.ShowForm = slices.Contains(strings.Split(r.Header.Get("X-User-Groups"), ","), "ADMIN")
	return v
}

func (s *server) index(w http.ResponseWriter, r *http.Request) {
	s.render(w, "page", s.view(r))
}

func (s *server) statsFragment(w http.ResponseWriter, r *http.Request) {
	s.render(w, "stats", s.view(r))
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
	if err != nil || rate < 1 || rate > MaxRatePPS {
		return out, "rate must be a whole number from 1 to " + thousands(MaxRatePPS)
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
	}
	for i := len(s) - 3; i > 0; i -= 3 {
		s = s[:i] + "," + s[i:]
	}
	return s
}
