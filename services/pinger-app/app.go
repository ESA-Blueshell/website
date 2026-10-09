package main

import (
	"context"

	"github.com/wailsapp/wails/v2/pkg/runtime"

	"github.com/ESA-Blueshell/website/services/pinger-app/internal/bandwidth"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/prefs"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/runner"
)

// App is the Wails binding the frontend calls. It owns the runner and starts it when the window
// comes up, so the first run signs the member in and begins painting and reporting at once.
type App struct {
	runner *runner.Runner
	ctx    context.Context
}

// NewApp builds the app around a runner for the given api base URL.
func NewApp(r *runner.Runner) *App {
	return &App{runner: r}
}

// startup is wired as Wails' OnStartup. It keeps the app context for the bound methods and launches
// the paint-and-report loop, which Wails cancels when the window closes.
func (a *App) startup(ctx context.Context) {
	a.ctx = ctx
	a.runner.OnChange = func() { runtime.EventsEmit(ctx, "status") }
	go func() { _ = a.runner.Run(ctx) }()
}

// Status is bound to the frontend, which polls it for the signed-in state and the live counters.
func (a *App) Status() runner.Status {
	return a.runner.Status()
}

// SignIn is bound to the sign-in button; it opens the browser login, which is the only thing that
// does. It runs in a goroutine so the browser flow does not block the UI call.
func (a *App) SignIn() {
	go a.runner.SignIn(a.ctx)
}

// SignOut is bound to the sign-out button; it drops the tokens and stops reporting.
func (a *App) SignOut() {
	a.runner.SignOut()
}

// OpenLeaderboard is bound to the leaderboard button; it opens the public SNTPings page.
func (a *App) OpenLeaderboard() error {
	return a.runner.OpenLeaderboard()
}

// Controls is the fixed shape the rate slider needs: the valid range, the home-safe default, the
// current rate and the bytes-per-packet the live bandwidth estimate multiplies by. The frontend
// reads it once to build the slider and compute the estimate without a round trip per drag.
type Controls struct {
	MinRate        int  `json:"minRate"`
	MaxRate        int  `json:"maxRate"`
	DefaultRate    int  `json:"defaultRate"`
	Rate           int  `json:"rate"`
	BytesPerPacket int  `json:"bytesPerPacket"`
	FullUplink     bool `json:"fullUplink"`
}

// Controls is bound to the frontend so it can render the slider and the estimate.
func (a *App) Controls() Controls {
	return Controls{
		MinRate:        prefs.MinRatePPS,
		MaxRate:        prefs.MaxRatePPS,
		DefaultRate:    prefs.DefaultRatePPS,
		Rate:           a.runner.Rate(),
		BytesPerPacket: bandwidth.BytesPerPacket,
		FullUplink:     a.runner.Status().FullUplink,
	}
}

// SetRate is bound to the slider; it clamps and persists the chosen rate and returns the rate that
// took effect so the UI can snap to it.
func (a *App) SetRate(pps int) int {
	return a.runner.SetRate(pps)
}

// SetFullUplink is bound to the uplink toggle; it returns the choice that took effect.
func (a *App) SetFullUplink(on bool) bool {
	return a.runner.SetFullUplink(on)
}

// OptInState is bound to the leaderboard toggle; it reads the member's current opt-in from the
// server so the toggle reflects the real state on load.
func (a *App) OptInState() (bool, error) {
	return a.runner.OptInState(a.ctx)
}

// OptIn is bound to the leaderboard toggle; it adds or removes the member from the public board and
// returns the resulting state.
func (a *App) OptIn(optedIn bool) (bool, error) {
	return a.runner.SetOptIn(a.ctx, optedIn)
}
