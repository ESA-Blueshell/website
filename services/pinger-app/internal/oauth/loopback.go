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

// handle reads the code or error from the redirect and reports it once, then shows a plain page the
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
		fmt.Fprint(w, "<!doctype html><title>Sign-in failed</title><p>Sign-in failed. You can close this window and try again.")
	} else {
		fmt.Fprint(w, "<!doctype html><title>Signed in</title><p>You are signed in. You can close this window and return to the app.")
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
