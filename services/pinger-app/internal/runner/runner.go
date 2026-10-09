// Package runner wires the member's identity to the shared ping engine: it signs in, polls the
// api-owned paint job, paints the canvas and reports the member's status every ten seconds on their
// bearer. The paint, canvas and apipaint packages are the pinger's own; this package only glues
// them to the member's token and a status line for the UI.
package runner

import (
	"context"
	"errors"
	"log/slog"
	"os"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/net/icmp"

	"github.com/ESA-Blueshell/website/services/pinger/apipaint"
	"github.com/ESA-Blueshell/website/services/pinger/paint"

	"github.com/ESA-Blueshell/website/services/pinger-app/internal/auth"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/config"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/headroom"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/leaderboard"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/oauth"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/prefs"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/report"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/tokenstore"
)

// reportInterval is how often the app reports its status; the ticket asks for about ten seconds.
const reportInterval = 10 * time.Second

// paintPollInterval is how often the paint job is re-read, matching the pinger service.
const paintPollInterval = 5 * time.Second

// Status is the snapshot the UI renders. It carries no token, only what is safe to show.
type Status struct {
	SignedIn bool `json:"signedIn"`
	// Account is the subject the server resolved the token to, shown so the member sees who is signed in.
	Account string `json:"account"`
	State   string `json:"state"`
	PPS     uint64 `json:"pps"`
	Sent    uint64 `json:"sent"`
	Errors  uint64 `json:"errors"`
	Message string `json:"message"`
	// Rate is this app's chosen send rate in pps, so the UI shows the target next to the live pps.
	Rate int `json:"rate"`
	// HeldAt is the rate the sender is held to so the member's connection stays usable, or zero
	// when it sends at Rate.
	HeldAt int `json:"heldAt"`
	// FullUplink is the member's choice to let the pings fill the whole uplink.
	FullUplink bool `json:"fullUplink"`
	// The paint window in unix milliseconds, for the UI's countdown. Zero start = always open.
	EventStart int64 `json:"eventStart"`
	EventEnd   int64 `json:"eventEnd"`
}

// Runner holds the authenticated engine. One Run spans the app's lifetime.
type Runner struct {
	base   string
	auth   *auth.Authenticator
	poster *report.Poster
	prefs  *prefs.Store
	board  *leaderboard.Client

	// window is the paint window, fixed at construction; the UI reads it for the countdown.
	window         paint.Window
	leaderboardURL string

	sender   atomic.Pointer[paint.Sender]
	signedIn atomic.Bool
	message  atomic.Pointer[string]
	account  atomic.Pointer[string]
	// rate is this app's own send rate in pps, clamped to the server range. It overrides the paint
	// job's rate so the member sets how hard their own machine pings, independent of SiteCie's.
	rate atomic.Int64
	// headroom lowers rate while the pings queue up the member's uplink; it never raises it.
	headroom *headroom.Controller
}

// New builds a runner for a base URL, loading the token store and local preferences and wiring the
// OAuth client, the report poster and the leaderboard client. It does not sign in or send; Run does
// that.
func New(base string) (*Runner, error) {
	dir, err := config.Dir()
	if err != nil {
		return nil, err
	}
	store := tokenstore.New(dir)
	client := oauth.NewClient(base, config.ClientID)
	login := oauth.BrowserLogin{Client: client, Open: openBrowser}
	authn := auth.New(store, client, login.Run)
	prefStore := prefs.New(dir)
	// This install's stable device id, so the api keeps this device's send counter apart from the
	// member's other devices. A failure to persist it is logged, not fatal: reporting still carries
	// an id, it just may differ on the next run.
	deviceID, err := prefStore.DeviceID()
	if err != nil {
		slog.Warn("device id", "err", err)
		deviceID = "device-unknown"
	}

	// Local testing: BLUESHELL_PINGER_ANYTIME ignores the event dates so the app paints right now
	// against a local stack, instead of waiting for the real SNTPings window.
	window := paint.EventWindow()
	if os.Getenv("BLUESHELL_PINGER_ANYTIME") != "" {
		window = paint.Window{Start: time.Time{}, End: time.Date(9999, 1, 1, 0, 0, 0, 0, time.UTC)}
	}

	r := &Runner{
		base:           base,
		auth:           authn,
		poster:         report.NewPoster(base, authn, deviceID),
		prefs:          prefStore,
		board:          leaderboard.NewClient(base, authn),
		window:         window,
		leaderboardURL: strings.TrimSuffix(base, "/api") + "/sntpings",
		headroom:       headroom.New(),
	}
	saved := prefStore.Load()
	r.rate.Store(int64(saved.RatePPS))
	r.headroom.SetEnabled(!saved.FullUplink)
	r.setMessage("starting")
	return r, nil
}

// Status is a safe snapshot for the UI.
func (r *Runner) Status() Status {
	st := Status{SignedIn: r.signedIn.Load(), Account: r.loadAccount(), Message: r.loadMessage(), Rate: int(r.rate.Load())}
	st.FullUplink = !r.headroom.Enabled()
	if !r.window.Start.IsZero() {
		st.EventStart = r.window.Start.UnixMilli()
	}
	st.EventEnd = r.window.End.UnixMilli()
	if s := r.sender.Load(); s != nil {
		snap := s.Snapshot()
		st.State = string(snap.State)
		st.PPS = snap.ActualPPS
		st.Sent = snap.Sent
		st.Errors = snap.Errors
		if snap.State == paint.Running {
			st.HeldAt = r.headroom.Held(st.Rate)
		}
	}
	return st
}

// SignIn runs the interactive browser login and, on success, marks the member signed in so the
// report loop posts. It blocks on the browser flow, so callers run it in a goroutine; it is the only
// thing that opens the browser.
func (r *Runner) SignIn(ctx context.Context) {
	if _, err := r.auth.Token(ctx); err != nil {
		slog.Warn("sign in", "err", err)
		return
	}
	r.signedIn.Store(true)
}

// SignOut drops the tokens and clears the signed-in state, so the app stops reporting until the
// member signs in again. It backs the UI's Sign out button.
func (r *Runner) SignOut() {
	r.signedIn.Store(false)
	r.setAccount("")
	if err := r.auth.SignOut(); err != nil {
		slog.Warn("sign out", "err", err)
	}
}

// OpenLeaderboard opens the public SNTPings leaderboard page in the member's browser.
func (r *Runner) OpenLeaderboard() error {
	return openBrowser(r.leaderboardURL)
}

// Rate is this app's current send rate in pps.
func (r *Runner) Rate() int { return int(r.rate.Load()) }

// applyRate keeps the api's prefix and box but sets the rate from this app's own choice, so the
// member paints the right place at a rate they control rather than SiteCie's cluster rate. The
// headroom ceiling may hold that choice lower, never higher.
func (r *Runner) applyRate(cur paint.Settings) paint.Settings {
	cur.RatePPS = r.headroom.Limit(int(r.rate.Load()))
	return cur
}

// SetFullUplink lets the pings fill the whole uplink, or holds them back so the member's other
// traffic keeps flowing, and persists the choice. It returns the choice that took effect.
func (r *Runner) SetFullUplink(on bool) bool {
	r.headroom.SetEnabled(!on)
	saved := r.prefs.Load()
	saved.FullUplink = on
	if err := r.prefs.Save(saved); err != nil {
		slog.Warn("save full uplink", "err", err)
	}
	return on
}

// SetRate sets this app's send rate, clamps it to the server's valid range, persists it so a
// restart keeps the choice, and returns the rate that took effect. The sender picks it up on its
// next settings read, so the live pps follows within a second.
func (r *Runner) SetRate(pps int) int {
	clamped := prefs.Clamp(pps)
	r.rate.Store(int64(clamped))
	// Load first so the saved device id is kept rather than cleared by a rate-only write.
	saved := r.prefs.Load()
	saved.RatePPS = clamped
	if err := r.prefs.Save(saved); err != nil {
		slog.Warn("save rate", "err", err)
	}
	return clamped
}

// OptInState reads whether the member currently appears on the public leaderboard. It is the
// server's flag, so the UI shows what the server returns rather than a local guess.
func (r *Runner) OptInState(ctx context.Context) (bool, error) {
	return r.board.State(ctx)
}

// SetOptIn adds or removes the member from the public leaderboard and returns the resulting state.
func (r *Runner) SetOptIn(ctx context.Context, optedIn bool) (bool, error) {
	return r.board.Set(ctx, optedIn)
}

// Run signs the member in, then paints and reports until ctx ends. It blocks, so callers run it in
// a goroutine. A sign-in failure is reported through the status line rather than crashing the app.
func (r *Runner) Run(ctx context.Context) error {
	// Resume a prior session silently: a still-valid token or refresh signs the member back in
	// without a browser. Only an explicit Sign in opens the browser, so the app never logs in on
	// its own.
	if _, err := r.auth.Current(ctx); err == nil {
		r.signedIn.Store(true)
	}

	conn, datagram, err := openICMP()
	if err != nil {
		r.setMessage("cannot open socket: " + err.Error())
		return err
	}
	defer conn.Close()
	markLowPriority(conn)

	// Drain the socket: every ping can draw a reply or an error message back, and against a contained
	// local test target (a route to loopback) they arrive for every packet. If nothing reads them the
	// receive buffer fills and the next WriteTo blocks, which freezes the sender. A reader discards
	// them continuously. Harmless in production, where few of the real addresses ever answer.
	go drainSocket(ctx, conn)

	var poller *apipaint.Poller
	settings := func() paint.Settings {
		cur := paint.Settings{}
		if poller != nil {
			cur = poller.Current()
		}
		return r.applyRate(cur)
	}
	sender := paint.NewSender(conn, nil, r.window, settings)
	if datagram {
		sender.UseDatagramAddresses()
	}
	r.sender.Store(sender)
	poller = apipaint.NewPoller(apipaint.NewClient(r.base), paintPollInterval, sender, nil)

	var wg sync.WaitGroup
	if probe, err := headroom.NewTCPProbe(r.base); err == nil {
		wg.Go(func() { r.headroom.Run(ctx, probe.RTT, func() headroom.Load { return r.load(sender) }) })
	} else {
		r.headroom.SetEnabled(false)
		slog.Warn("headroom probe", "err", err)
	}
	wg.Add(3)
	go func() { defer wg.Done(); poller.Run(ctx) }()
	go func() { defer wg.Done(); r.reportLoop(ctx, sender) }()
	go func() { defer wg.Done(); sender.Run(ctx) }()
	wg.Wait()
	return ctx.Err()
}

// load is what the headroom controller needs from the sender for one step.
func (r *Runner) load(sender *paint.Sender) headroom.Load {
	snap := sender.Snapshot()
	return headroom.Load{Running: snap.State == paint.Running, ActualPPS: snap.ActualPPS, Want: int(r.rate.Load())}
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
			// Report only while signed in. Posting never opens a browser: the poster uses the
			// non-interactive token, so a signed-out app just waits for the member to sign in.
			if !r.signedIn.Load() {
				continue
			}
			rep := report.Build(sender.Snapshot())
			err := r.poster.Post(ctx, rep)
			switch {
			case err == nil:
				if r.loadAccount() == "" {
					if who, werr := r.poster.Whoami(ctx); werr == nil && who != "" {
						r.setAccount(who)
					}
				}
			case errors.Is(err, report.ErrUnauthorized), errors.Is(err, auth.ErrSignInRequired):
				// The token lapsed and no refresh renews it: sign out and wait for the member to
				// sign in again rather than popping a browser mid-report.
				r.signedIn.Store(false)
				r.setAccount("")
			case errors.Is(err, context.Canceled):
				return
			default:
				slog.Warn("post report", "err", err)
			}
		}
	}
}

// drainSocket reads and discards whatever comes back on the ICMP socket, so a flood of replies or
// ICMP error messages from a contained local target cannot fill the receive buffer and block sends.
func drainSocket(ctx context.Context, c *icmp.PacketConn) {
	buf := make([]byte, 1500)
	for ctx.Err() == nil {
		_ = c.SetReadDeadline(time.Now().Add(time.Second))
		if _, _, err := c.ReadFrom(buf); err != nil {
			continue
		}
	}
}

func (r *Runner) setMessage(m string) { r.message.Store(&m) }

func (r *Runner) setAccount(a string) { r.account.Store(&a) }

func (r *Runner) loadAccount() string {
	if a := r.account.Load(); a != nil {
		return *a
	}
	return ""
}

func (r *Runner) loadMessage() string {
	if m := r.message.Load(); m != nil {
		return *m
	}
	return ""
}
