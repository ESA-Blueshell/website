package main

import (
	"context"

	"github.com/ESA-Blueshell/website/services/pinger-app/internal/runner"
)

// App is the Wails binding the frontend calls. It owns the runner and starts it when the window
// comes up, so the first run signs the member in and begins painting and reporting at once.
type App struct {
	runner *runner.Runner
}

// NewApp builds the app around a runner for the given api base URL.
func NewApp(r *runner.Runner) *App {
	return &App{runner: r}
}

// startup is wired as Wails' OnStartup. It launches the paint-and-report loop on the app context,
// which Wails cancels when the window closes.
func (a *App) startup(ctx context.Context) {
	go func() { _ = a.runner.Run(ctx) }()
}

// Status is bound to the frontend, which polls it for the signed-in state and the live counters.
func (a *App) Status() runner.Status {
	return a.runner.Status()
}

// SignInAgain is bound to the sign-in button; it forces a token renewal, opening the browser when
// no valid refresh remains.
func (a *App) SignInAgain() {
	a.runner.SignInAgain()
}
