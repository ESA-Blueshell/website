package headroom

import (
	"context"
	"errors"
	"net"
	"net/url"
	"sync"
	"time"
)

// TCPProbe times a bare TCP handshake to the api. The SYN waits in the same upload queue as the
// pings, so the handshake time rises with that queue; no TLS or request follows, so the api does
// no work for it.
type TCPProbe struct {
	host string
	port string
	dial func(ctx context.Context, network, addr string) (net.Conn, error)
	look func(ctx context.Context, host string) ([]string, error)

	mu   sync.Mutex
	addr string
}

// NewTCPProbe probes the host and port of an api base URL such as https://esa-blueshell.nl/api.
func NewTCPProbe(base string) (*TCPProbe, error) {
	u, err := url.Parse(base)
	if err != nil {
		return nil, err
	}
	if u.Hostname() == "" {
		return nil, errors.New("headroom: base URL has no host")
	}
	port := u.Port()
	if port == "" {
		port = "443"
		if u.Scheme == "http" {
			port = "80"
		}
	}
	var d net.Dialer
	return &TCPProbe{host: u.Hostname(), port: port, dial: d.DialContext, look: net.DefaultResolver.LookupHost}, nil
}

// RTT is one handshake's duration. A handshake that does not finish within lostRTT is ErrLost.
// The address is resolved once and kept, so a slow DNS answer does not read as a queue; a failed
// handshake drops it so the next probe resolves again.
func (p *TCPProbe) RTT(ctx context.Context) (time.Duration, error) {
	addr, err := p.resolve(ctx)
	if err != nil {
		return 0, err
	}
	ctx, cancel := context.WithTimeout(ctx, lostRTT)
	defer cancel()
	start := time.Now()
	conn, err := p.dial(ctx, "tcp", addr)
	elapsed := time.Since(start)
	if err != nil {
		var ne net.Error
		if errors.Is(err, context.DeadlineExceeded) || (errors.As(err, &ne) && ne.Timeout()) {
			return 0, ErrLost
		}
		p.forget()
		return 0, err
	}
	_ = conn.Close()
	return elapsed, nil
}

func (p *TCPProbe) resolve(ctx context.Context) (string, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.addr != "" {
		return p.addr, nil
	}
	ips, err := p.look(ctx, p.host)
	if err != nil {
		return "", err
	}
	if len(ips) == 0 {
		return "", errors.New("headroom: no address for " + p.host)
	}
	p.addr = net.JoinHostPort(ips[0], p.port)
	return p.addr, nil
}

func (p *TCPProbe) forget() {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.addr = ""
}
