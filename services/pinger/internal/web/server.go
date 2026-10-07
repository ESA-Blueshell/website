// Package web serves the public progress page onlookers watch the pinger through. The pinger is
// steered from the main site, so this holds no form and no admin check: it only shows state.
package web

import (
	"context"
	"embed"
	"encoding/json"
	"fmt"
	"html/template"
	"io/fs"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/coder/websocket"

	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

// liveInterval is how often the socket pushes the live state. It is a small JSON object, not
// HTML, so a few times a second is cheap and the page reflects the real state quickly.
const liveInterval = 250 * time.Millisecond

// socketMaxAge bounds a socket's life, so a viewer who leaves the tab open does not hold a feed
// forever. The feed is read-only event telemetry, so the page just reconnects when it closes.
const socketMaxAge = 15 * time.Minute

//go:embed templates/*.html
var templateFS embed.FS

//go:embed static
var staticFS embed.FS

type StatsSource interface {
	Snapshot() paint.Stats
}

type server struct {
	stats    StatsSource
	settings func() paint.Settings
	preview  func() []byte
	page     *template.Template
	ams      *time.Location
}

// NewServer serves the page. settings is the paint job the pinger currently reads from the api, and
// preview returns the PNG of the image as it lands, or nil when there is no image yet.
func NewServer(stats StatsSource, settings func() paint.Settings, preview func() []byte) http.Handler {
	ams, err := time.LoadLocation("Europe/Amsterdam")
	if err != nil {
		panic(err)
	}
	s := &server{
		stats:    stats,
		settings: settings,
		preview:  preview,
		page:     template.Must(template.New("").Funcs(template.FuncMap{"thousands": thousands, "compact": compact}).ParseFS(templateFS, "templates/*.html")),
		ams:      ams,
	}
	static, _ := fs.Sub(staticFS, "static")

	mux := http.NewServeMux()
	mux.HandleFunc("GET /{$}", s.index)
	mux.HandleFunc("GET /live.json", s.liveJSON)
	mux.HandleFunc("GET /ws", s.socket)
	mux.HandleFunc("GET /preview.png", s.previewImage)
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusNoContent) })
	// no-cache, not no-store: the browser keeps the file but revalidates each load, so a rebuilt
	// stylesheet or script is picked up on the next visit rather than served stale from cache.
	staticHandler := http.StripPrefix("/static/", http.FileServerFS(static))
	mux.Handle("GET /static/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-cache")
		staticHandler.ServeHTTP(w, r)
	}))
	return mux
}

type countdown struct {
	Days    string `json:"d"`
	Hours   string `json:"h"`
	Minutes string `json:"m"`
	Seconds string `json:"s"`
}

type view struct {
	Stats       paint.Stats
	Settings    paint.Settings
	Prefix      string
	PrefixLabel string
	PassPercent int
	LastErrorAt string

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
}

func (s *server) view() view {
	st := s.stats.Snapshot()
	cfg := s.settings()
	state := string(st.State)
	v := view{
		Stats:       st,
		Settings:    cfg,
		Prefix:      cfg.Prefix.String(),
		State:       state,
		IsRunning:   state == string(paint.Running),
		IsIdle:      state == string(paint.Idle),
		IsClosed:    state == string(paint.Closed),
		ShowNumbers: state != string(paint.Closed),
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
	return v
}

func chip(state string) (label, class string) {
	switch state {
	case string(paint.Running):
		return "Painting", "state-chip state-chip--running"
	case string(paint.Closed):
		return "Opens Fri 9 Oct, 18:00", "state-chip state-chip--closed"
	default:
		return "Waiting for the prefix", "state-chip state-chip--idle"
	}
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

func (s *server) index(w http.ResponseWriter, _ *http.Request) {
	s.render(w, "page", s.view())
}

// liveState is the live feed the socket pushes as JSON. Raw numbers drive the bar and the canvas,
// the formatted strings fill the text, and the flags toggle the sections. The page binds each
// field to a node, so the browser never re-parses HTML to show a new value.
type liveState struct {
	State        string    `json:"state"`
	Running      bool      `json:"running"`
	Idle         bool      `json:"idle"`
	Closed       bool      `json:"closed"`
	ShowNumbers  bool      `json:"showNumbers"`
	ShowErrors   bool      `json:"showErrors"`
	ChipLabel    string    `json:"chipLabel"`
	ChipClass    string    `json:"chipClass"`
	PPS          string    `json:"pps"`
	Sent         string    `json:"sent"`
	Passes       string    `json:"passes"`
	Errors       string    `json:"errors"`
	ErrorsWrong  bool      `json:"errorsWrong"`
	PassPercent  int       `json:"passPercent"`
	PassDoneTxt  string    `json:"passDoneTxt"`
	PassTotalTxt string    `json:"passTotalTxt"`
	PassDone     int       `json:"passDone"`
	PassTotal    int       `json:"passTotal"`
	PPSRaw       uint64    `json:"ppsRaw"`
	Cap          string    `json:"cap"`
	Eta          string    `json:"eta"`
	PrefixLabel  string    `json:"prefixLabel"`
	ErrorTitle   string    `json:"errorTitle"`
	LastErrorAt  string    `json:"lastErrorAt"`
	LastError    string    `json:"lastError"`
	Countdown    countdown `json:"countdown"`
	HasRate      bool      `json:"hasRate"`
	RateCount    int       `json:"rateCount"`
	RatePoints   string    `json:"ratePoints"`
	RateArea     string    `json:"rateArea"`
	RateCap      int       `json:"rateCap"`
	RateLastY    string    `json:"rateLastY"`
	RateCeil     string    `json:"rateCeil"`
}

func liveFrom(v view) liveState {
	return liveState{
		State: v.State, Running: v.IsRunning, Idle: v.IsIdle, Closed: v.IsClosed,
		ShowNumbers: v.ShowNumbers, ShowErrors: v.ShowErrors,
		ChipLabel: v.ChipLabel, ChipClass: v.ChipClass,
		PPS: thousands(v.Stats.ActualPPS), Sent: compact(v.Stats.Sent),
		Passes: compact(v.Stats.Passes), Errors: compact(v.Stats.Errors), ErrorsWrong: v.Stats.Errors > 0,
		PassPercent: v.PassPercent,
		PassDoneTxt: compact(v.Stats.PassDone), PassTotalTxt: compact(v.Stats.PassTotal),
		PassDone: v.Stats.PassDone, PassTotal: v.Stats.PassTotal, PPSRaw: v.Stats.ActualPPS,
		Cap: thousands(v.Settings.RatePPS), Eta: v.PassEta, PrefixLabel: v.PrefixLabel,
		ErrorTitle: v.ErrorTitle, LastErrorAt: v.LastErrorAt, LastError: v.Stats.LastError,
		Countdown: v.Countdown,
		HasRate:   v.RatePoints != "", RateCount: v.RateCount,
		RatePoints: v.RatePoints, RateArea: v.RateArea, RateCap: v.CapPercent,
		RateLastY: v.RateLastY, RateCeil: thousands(v.RateCeil),
	}
}

func (s *server) liveJSON(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	_ = json.NewEncoder(w).Encode(liveFrom(s.view()))
}

// socket pushes the live state as JSON a few times a second. A failed write means the viewer
// left, so the loop ends. The default origin check rejects a socket opened from another site.
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
		b, err := json.Marshal(liveFrom(s.view()))
		if err != nil {
			slog.Error("marshal live", "err", err)
			return
		}
		if err := c.Write(ctx, websocket.MessageText, b); err != nil {
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

func (s *server) previewImage(w http.ResponseWriter, r *http.Request) {
	png := s.preview()
	if png == nil {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", "image/png")
	w.Header().Set("Cache-Control", "no-cache")
	_, _ = w.Write(png)
}

func thousands(n any) string {
	s := strconv.FormatUint(toUint64(n), 10)
	for i := len(s) - 3; i > 0; i -= 3 {
		s = s[:i] + "," + s[i:]
	}
	return s
}

// compact writes a big tally with up to four comma-grouped digits and a unit suffix, so it stays
// readable yet shows progress: 9,999 then 10K, 1,235K, 4,132B. It picks the smallest unit that
// keeps the figure at four digits or fewer.
func compact(n any) string {
	v := toUint64(n)
	for _, u := range []struct {
		div    uint64
		suffix string
	}{{1, ""}, {1e3, "K"}, {1e6, "M"}, {1e9, "B"}, {1e12, "T"}} {
		q := (v + u.div/2) / u.div
		if q <= 9999 {
			return thousands(q) + u.suffix
		}
	}
	return thousands((v+5e11)/1e12) + "T"
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
