package oauth

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/http"
	"time"
)

// redirectPath is the loopback callback path the authorization server registered for this client.
// The server allows any port on 127.0.0.1 (RFC 8252), so the app binds an ephemeral one and only
// the path has to match.
const redirectPath = "/login/oauth2/code/pinger-app"

// Loopback is a one-shot HTTP server on 127.0.0.1 that catches the authorization redirect and hands
// back the code. It binds an ephemeral port so nothing has to be reserved up front.
type Loopback struct {
	listener net.Listener
	server   *http.Server
	result   chan callback
	state    string
}

type callback struct {
	code string
	err  error
}

// NewLoopback binds an ephemeral port on 127.0.0.1 and prepares to receive the redirect. state is
// the value sent to the authorize endpoint; a callback carrying any other state is refused, which
// closes the door on a cross-site callback.
func NewLoopback(state string) (*Loopback, error) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return nil, err
	}
	lb := &Loopback{
		listener: ln,
		result:   make(chan callback, 1),
		state:    state,
	}
	mux := http.NewServeMux()
	mux.HandleFunc(redirectPath, lb.handle)
	lb.server = &http.Server{Handler: mux, ReadHeaderTimeout: 10 * time.Second}
	go lb.server.Serve(ln) //nolint:errcheck // Serve always returns a non-nil error at shutdown.
	return lb, nil
}

// RedirectURI is the exact redirect_uri to send to the authorize endpoint and later to the token
// exchange; both must carry the same value.
func (lb *Loopback) RedirectURI() string {
	return fmt.Sprintf("http://%s%s", lb.listener.Addr().String(), redirectPath)
}

// signedInPage and signInFailedPage are the on-brand pages the browser shows after the OAuth
// redirect, styled like the Blueshell site so the member knows it worked and can close the tab.
const brandPage = `<!doctype html><html lang="en"><head><meta charset="utf-8">` +
	`<meta name="viewport" content="width=device-width, initial-scale=1"><title>%s — Blueshell Pinger</title><style>` +
	`:root{color-scheme:dark}*{box-sizing:border-box}` +
	`body{margin:0;min-height:100vh;display:grid;place-items:center;background:#0b0f1a;color:#e6edf3;` +
	`font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;` +
	`background-image:radial-gradient(1200px 600px at 50%% -10%%,rgba(31,111,235,.18),transparent)}` +
	`.card{max-width:26rem;padding:2.75rem 2rem;text-align:center}` +
	`.mark{display:inline-block;font-weight:800;letter-spacing:.06em;font-size:.95rem;color:#fff;background:#1f6feb;` +
	`padding:.4rem .75rem;transform:skewX(-10deg);margin-bottom:1.75rem}.mark span{display:inline-block;transform:skewX(10deg)}` +
	`.badge{width:3.25rem;height:3.25rem;margin:0 auto 1.1rem;border-radius:999px;display:grid;place-items:center;` +
	`font-size:1.7rem;font-weight:700;background:%s;color:#fff}` +
	`h1{font-size:1.55rem;margin:.25rem 0 .6rem}p{margin:0;opacity:.68;line-height:1.55;font-size:.95rem}` +
	`</style></head><body><div class="card"><div class="mark"><span>BLUESHELL</span></div>` +
	`<div class="badge">%s</div><h1>%s</h1><p>%s</p></div>%s</body></html>`

// closeScript closes the tab once the member is signed in. A tab the browser did not open from a
// script often refuses window.close(); reassigning the window to itself first clears that block in
// most browsers, and the visible copy covers the rest where it still refuses.
const closeScript = `<script>setTimeout(function(){window.open("","_self");window.close()},400)</script>`

var signedInPage = fmt.Sprintf(brandPage, "Signed in", "#1f6feb", "✓", "You're signed in",
	"This tab closes itself; return to Blueshell Pinger.", closeScript)

var signInFailedPage = fmt.Sprintf(brandPage, "Sign-in failed", "#da3633", "✕", "Sign-in failed",
	"You can close this window and try again from Blueshell Pinger.", "")

// handle reads the code or error from the redirect and reports it once, then shows a branded page the
// member can close.
func (lb *Loopback) handle(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	var cb callback
	switch {
	case q.Get("state") != lb.state:
		cb.err = errors.New("oauth: callback state did not match")
	case q.Get("error") != "":
		cb.err = fmt.Errorf("oauth: authorization failed: %s", q.Get("error"))
	case q.Get("code") == "":
		cb.err = errors.New("oauth: callback carried no code")
	default:
		cb.code = q.Get("code")
	}

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	if cb.err != nil {
		w.WriteHeader(http.StatusBadRequest)
		fmt.Fprint(w, signInFailedPage)
	} else {
		fmt.Fprint(w, signedInPage)
	}

	select {
	case lb.result <- cb:
	default:
	}
}

// Wait blocks until the redirect arrives or ctx ends, and returns the authorization code.
func (lb *Loopback) Wait(ctx context.Context) (string, error) {
	select {
	case <-ctx.Done():
		return "", ctx.Err()
	case cb := <-lb.result:
		return cb.code, cb.err
	}
}

// Close shuts the loopback server down. Safe to call once the code is in hand.
func (lb *Loopback) Close() error {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	return lb.server.Shutdown(ctx)
}
