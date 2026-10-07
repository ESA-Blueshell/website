// Package runner wires the member's identity to the shared ping engine: it signs in, polls the
// api-owned paint job, paints the canvas and reports the member's status every ten seconds on their
// bearer. The paint, canvas and apipaint packages are the pinger's own; this package only glues
// them to the member's token and a status line for the UI.
package runner

import (
	"context"
	"errors"
	"log/slog"
	"sync"
	"sync/atomic"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger/apipaint"
	"github.com/ESA-Blueshell/website/services/pinger/paint"

	"github.com/ESA-Blueshell/website/services/pinger-app/internal/auth"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/config"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/oauth"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/report"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/tokenstore"
)

// reportInterval is how often the app reports its status; the ticket asks for about ten seconds.
const reportInterval = 10 * time.Second

// paintPollInterval is how often the paint job is re-read, matching the pinger service.
const paintPollInterval = 5 * time.Second

// Status is the snapshot the UI renders. It carries no token, only what is safe to show.
type Status struct {
	SignedIn bool   `json:"signedIn"`
	State    string `json:"state"`
	PPS      uint64 `json:"pps"`
	Sent     uint64 `json:"sent"`
	Errors   uint64 `json:"errors"`
	Message  string `json:"message"`
}

// Runner holds the authenticated engine. One Run spans the app's lifetime.
type Runner struct {
	base   string
	auth   *auth.Authenticator
	poster *report.Poster

	sender   atomic.Pointer[paint.Sender]
	signedIn atomic.Bool
	message  atomic.Pointer[string]
}

// New builds a runner for a base URL, loading the token store and wiring the OAuth client and the
// report poster. It does not sign in or send; Run does that.
func New(base string) (*Runner, error) {
	dir, err := config.Dir()
	if err != nil {
		return nil, err
	}
	store := tokenstore.New(dir)
	client := oauth.NewClient(base, config.ClientID)
	login := oauth.BrowserLogin{Client: client, Open: openBrowser}
	authn := auth.New(store, client, login.Run)

	r := &Runner{
		base:   base,
		auth:   authn,
		poster: report.NewPoster(base, authn),
	}
	r.setMessage("starting")
	return r, nil
}

// Status is a safe snapshot for the UI.
func (r *Runner) Status() Status {
	st := Status{SignedIn: r.signedIn.Load(), Message: r.loadMessage()}
	if s := r.sender.Load(); s != nil {
		snap := s.Snapshot()
		st.State = string(snap.State)
		st.PPS = snap.ActualPPS
		st.Sent = snap.Sent
		st.Errors = snap.Errors
	}
	return st
}

// SignInAgain forces the next report to renew the token, which signs in through the browser when no
// valid refresh remains. It backs the UI's sign-in button.
func (r *Runner) SignInAgain() {
	if err := r.auth.Invalidate(); err != nil {
		slog.Warn("invalidate token", "err", err)
	}
	r.setMessage("signing in")
}

// Run signs the member in, then paints and reports until ctx ends. It blocks, so callers run it in
// a goroutine. A sign-in failure is reported through the status line rather than crashing the app.
func (r *Runner) Run(ctx context.Context) error {
	// Prompt the browser login up front so the first run authenticates before it tries to report.
	if _, err := r.auth.Token(ctx); err != nil {
		r.setMessage("sign-in failed: " + err.Error())
		slog.Warn("initial sign-in", "err", err)
	} else {
		r.signedIn.Store(true)
		r.setMessage("signed in")
	}

	conn, datagram, err := openICMP()
	if err != nil {
		r.setMessage("cannot open socket: " + err.Error())
		return err
	}
	defer conn.Close()

	var poller *apipaint.Poller
	settings := func() paint.Settings {
		if poller == nil {
			return paint.Settings{}
		}
		return poller.Current()
	}
	sender := paint.NewSender(conn, nil, paint.EventWindow(), settings)
	if datagram {
		sender.UseDatagramAddresses()
	}
	r.sender.Store(sender)
	poller = apipaint.NewPoller(apipaint.NewClient(r.base), paintPollInterval, sender, nil)

	var wg sync.WaitGroup
	wg.Add(3)
	go func() { defer wg.Done(); poller.Run(ctx) }()
	go func() { defer wg.Done(); r.reportLoop(ctx, sender) }()
	go func() { defer wg.Done(); sender.Run(ctx) }()
	wg.Wait()
	return ctx.Err()
}

// reportLoop posts the member's status every reportInterval. A refusal renews the token and keeps
// going; it never ends the loop.
func (r *Runner) reportLoop(ctx context.Context, sender *paint.Sender) {
	tick := time.NewTicker(reportInterval)
	defer tick.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
			rep := report.Build(sender.Snapshot())
			err := r.poster.Post(ctx, rep)
			switch {
			case err == nil:
				r.signedIn.Store(true)
				r.setMessage("reporting")
			case errors.Is(err, report.ErrUnauthorized):
				// The server refused the post: drop the access token so the next tick renews it,
				// falling back to a browser login when no valid refresh remains.
				r.signedIn.Store(false)
				if ierr := r.auth.Invalidate(); ierr != nil {
					slog.Warn("invalidate token", "err", ierr)
				}
				r.setMessage("re-authenticating")
			case errors.Is(err, context.Canceled):
				return
			default:
				r.setMessage("report failed: " + err.Error())
				slog.Warn("post report", "err", err)
			}
		}
	}
}

func (r *Runner) setMessage(m string) { r.message.Store(&m) }

func (r *Runner) loadMessage() string {
	if m := r.message.Load(); m != nil {
		return *m
	}
	return ""
}
