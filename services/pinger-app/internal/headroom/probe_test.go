package headroom

import (
	"context"
	"errors"
	"net"
	"testing"
)

func TestNewTCPProbePicksThePort(t *testing.T) {
	cases := map[string]string{
		"https://esa-blueshell.nl/api": "443",
		"http://localhost/api":         "80",
		"http://localhost:8080/api":    "8080",
	}
	for base, port := range cases {
		p, err := NewTCPProbe(base)
		if err != nil {
			t.Fatalf("NewTCPProbe(%q): %v", base, err)
		}
		if p.port != port {
			t.Errorf("NewTCPProbe(%q).port = %q, want %q", base, p.port, port)
		}
	}
	for _, bad := range []string{"/api", "://"} {
		if _, err := NewTCPProbe(bad); err == nil {
			t.Errorf("NewTCPProbe(%q) = nil error, want one", bad)
		}
	}
}

func TestRTTTimesAHandshake(t *testing.T) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer ln.Close()
	p, err := NewTCPProbe("http://" + ln.Addr().String())
	if err != nil {
		t.Fatal(err)
	}
	rtt, err := p.RTT(context.Background())
	if err != nil {
		t.Fatalf("RTT: %v", err)
	}
	if rtt <= 0 || rtt >= lostRTT {
		t.Errorf("RTT = %v, want a short positive handshake", rtt)
	}
}

func TestRTTReadsATimeoutAsLost(t *testing.T) {
	p := &TCPProbe{host: "h", port: "443",
		look: func(context.Context, string) ([]string, error) { return []string{"192.0.2.1"}, nil },
		dial: func(context.Context, string, string) (net.Conn, error) { return nil, context.DeadlineExceeded },
	}
	if _, err := p.RTT(context.Background()); !errors.Is(err, ErrLost) {
		t.Errorf("RTT err = %v, want ErrLost", err)
	}
	if p.addr == "" {
		t.Error("a lost probe dropped the address; it should keep it")
	}
}

func TestRTTResolvesAgainAfterAFailedHandshake(t *testing.T) {
	lookups := 0
	refused := errors.New("connection refused")
	p := &TCPProbe{host: "h", port: "443",
		look: func(context.Context, string) ([]string, error) { lookups++; return []string{"192.0.2.1"}, nil },
		dial: func(context.Context, string, string) (net.Conn, error) { return nil, refused },
	}
	for range 2 {
		if _, err := p.RTT(context.Background()); !errors.Is(err, refused) {
			t.Fatalf("RTT err = %v, want the dial error", err)
		}
	}
	if lookups != 2 {
		t.Errorf("lookups = %d, want a fresh lookup after each failure", lookups)
	}
}

func TestRTTSurfacesALookupFailure(t *testing.T) {
	noHost := errors.New("no such host")
	p := &TCPProbe{host: "h", port: "443",
		look: func(context.Context, string) ([]string, error) { return nil, noHost },
	}
	if _, err := p.RTT(context.Background()); !errors.Is(err, noHost) {
		t.Errorf("RTT err = %v, want the lookup error", err)
	}
	p.look = func(context.Context, string) ([]string, error) { return nil, nil }
	if _, err := p.RTT(context.Background()); err == nil {
		t.Error("RTT err = nil for an empty lookup, want one")
	}
}
