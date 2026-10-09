package runner

import (
	"errors"
	"testing"

	"github.com/ESA-Blueshell/website/services/pinger-app/internal/headroom"
)

func stubOpen(t *testing.T, notice string, err error) {
	t.Helper()
	prev := openSockets
	openSockets = func() ([]socket, bool, string, error) { return nil, false, notice, err }
	t.Cleanup(func() { openSockets = prev })
}

// The member only reads the status line, so a notice that costs rate has to land there.
func TestOpenPutsTheSocketNoticeInTheStatus(t *testing.T) {
	stubOpen(t, "exempt the pings", nil)
	r := &Runner{headroom: headroom.New()}

	if _, _, err := r.open(); err != nil {
		t.Fatal(err)
	}
	if got := r.Status().Message; got != "exempt the pings" {
		t.Fatalf("message %q", got)
	}
}

func TestOpenReportsASocketItCannotOpen(t *testing.T) {
	stubOpen(t, "", errors.New("no IPv6"))
	r := &Runner{headroom: headroom.New()}

	if _, _, err := r.open(); err == nil {
		t.Fatal("open succeeded without a socket")
	}
	if got := r.Status().Message; got != "cannot open socket: no IPv6" {
		t.Fatalf("message %q", got)
	}
}
