package runner

import (
	"errors"
	"net/netip"
	"strings"
	"syscall"
	"testing"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger/paint"

	"github.com/ESA-Blueshell/website/services/pinger-app/internal/headroom"
)

func TestReachesIPv6OnlyFromAGlobalSource(t *testing.T) {
	cases := []struct {
		src  string
		err  error
		want bool
	}{
		{"2a02:a46c:1234::5", nil, true},
		{"2001:0:4136:e378::1", nil, true}, // Teredo
		{"fe80::1%eth0", nil, false},
		{"fd12:3456::1", nil, false},
		{"::1", nil, false},
		{"::ffff:192.168.1.2", nil, false},
		{"", syscall.ENETUNREACH, false},
		{"2a02:a46c:1234::5", errors.New("connect failed"), false},
	}
	for _, c := range cases {
		var src netip.Addr
		if c.src != "" {
			src = netip.MustParseAddr(c.src)
		}
		if got := reachesIPv6(src, c.err); got != c.want {
			t.Errorf("reachesIPv6(%q, %v) = %v, want %v", c.src, c.err, got, c.want)
		}
	}
}

type fakeProbe struct {
	src   netip.Addr
	err   error
	calls int
}

func (p *fakeProbe) source() (netip.Addr, error) { p.calls++; return p.src, p.err }

func noIPv6Probe() *fakeProbe { return &fakeProbe{err: syscall.ENETUNREACH} }

func TestIPv6WatchLooksAgainOnlyWhenDueOrForced(t *testing.T) {
	p := &fakeProbe{src: netip.MustParseAddr("2a02::1")}
	v := &ipv6Watch{probe: p.source}
	t0 := time.Unix(0, 0)
	if v.missing(t0, false) {
		t.Fatal("missing with a global source")
	}
	v.missing(t0.Add(ipv6Recheck-time.Second), false)
	if p.calls != 1 {
		t.Fatalf("probed %d times before the recheck was due", p.calls)
	}
	p.src, p.err = netip.Addr{}, syscall.ENETUNREACH
	if !v.missing(t0.Add(ipv6Recheck-time.Second), true) {
		t.Fatal("a forced look kept the old verdict")
	}
	p.src, p.err = netip.MustParseAddr("2a02::1"), nil
	if v.missing(t0.Add(2*ipv6Recheck), false) {
		t.Fatal("a due recheck kept the old verdict")
	}
}

// Before the event and while it waits on the paint job the app has no sends to judge, so the
// route check alone says the network cannot reach the canvas.
func TestWatchSaysNoIPv6BeforeAnythingIsSent(t *testing.T) {
	for _, state := range []paint.State{paint.Closed, paint.Idle, paint.Running} {
		r := &Runner{headroom: headroom.New()}
		snap := paint.Stats{State: state}
		w := watchFor(r, &snap, []*fakeSock{{}}, nil)
		w.v6 = &ipv6Watch{probe: noIPv6Probe().source}
		w.step(time.Unix(0, 0))
		if got := r.Status().Message; !strings.Contains(got, noIPv6Message) {
			t.Errorf("%s: status %q", state, got)
		}
	}
}

// Moving to the slower helper API only costs rate once the member finds a network with IPv6.
func TestWatchKeepsThePathWhenTheNetworkHasNoIPv6(t *testing.T) {
	r := &Runner{headroom: headroom.New()}
	opens := 0
	w := watchFor(r, nil, []*fakeSock{{err: winErr("Icmp6SendEcho2", 11050)}}, func() (opened, error) {
		opens++
		return opened{socks: []socket{&fakeSock{}}, path: pathEcho}, nil
	})
	w.v6 = &ipv6Watch{probe: noIPv6Probe().source}
	snap := running(0, 0)
	w.snapshot = func() paint.Stats { return snap }
	t0 := time.Unix(0, 0)
	for s := 0; s <= 10; s++ {
		_, _ = w.socks[0].WriteTo([]byte{1}, dst)
		snap.Errors++
		w.step(t0.Add(time.Duration(s) * time.Second))
	}
	if opens != 0 {
		t.Fatalf("fell back %d times on a network without IPv6", opens)
	}
	if got := r.Status().Message; !strings.Contains(got, noIPv6Message) {
		t.Fatalf("status %q", got)
	}
}

// A ULA network behind NAT66 does reach the canvas; sends that land outrank the route check.
func TestWatchTrustsSendsThatLandOverTheRouteCheck(t *testing.T) {
	r := &Runner{headroom: headroom.New()}
	w := watchFor(r, nil, []*fakeSock{{}}, nil)
	w.v6 = &ipv6Watch{probe: (&fakeProbe{src: netip.MustParseAddr("fd00::2")}).source}
	snap := running(0, 0)
	w.snapshot = func() paint.Stats { return snap }
	t0 := time.Unix(0, 0)
	for s := 0; s <= 10; s++ {
		snap.Sent += 100
		w.step(t0.Add(time.Duration(s) * time.Second))
	}
	if got := r.Status().Message; got != "" {
		t.Fatalf("status %q while sends land", got)
	}
}

// A network that drops IPv6 mid-event is looked at again as soon as the sends stall.
func TestWatchLooksAgainWhenTheSendsStall(t *testing.T) {
	r := &Runner{headroom: headroom.New()}
	p := &fakeProbe{src: netip.MustParseAddr("2a02::1")}
	w := watchFor(r, nil, []*fakeSock{{}}, nil)
	w.v6 = &ipv6Watch{probe: p.source}
	snap := running(0, 0)
	w.snapshot = func() paint.Stats { return snap }
	t0 := time.Unix(0, 0)
	w.step(t0)
	snap.Sent = 10
	w.step(t0.Add(time.Second))
	p.src, p.err = netip.Addr{}, syscall.ENETUNREACH
	w.step(t0.Add(time.Second + stallAfter))
	if got := r.Status().Message; !strings.Contains(got, noIPv6Message) {
		t.Fatalf("status %q", got)
	}
}

// Every code Windows and the Unix stacks use for a missing IPv6 route says the same thing.
func TestStallMessageSaysNoIPv6ForEveryNoRouteCode(t *testing.T) {
	errs := []error{
		winErr("Icmp6SendEcho2", 11002),
		winErr("Icmp6SendEcho2", 11003),
		winErr("Icmp6SendEcho2", 11012),
		winErr("Icmp6SendEcho2", 1231),
		winErr("Icmp6SendEcho2", 1232),
		winErr("wsasendto", 10051),
		winErr("wsasendto", 10065),
		winErr("wsasendto", 10049),
		winErr("sendto", syscall.ENETUNREACH),
		winErr("sendto", syscall.EHOSTUNREACH),
		winErr("sendto", syscall.EADDRNOTAVAIL),
	}
	for _, err := range errs {
		for _, path := range []string{pathRaw, pathEcho} {
			got := stallMessage(path, err)
			if !strings.HasPrefix(got, noIPv6Message) || !strings.Contains(got, err.Error()) {
				t.Errorf("%v over %s: %q", err, path, got)
			}
			if strings.Contains(got, "administrator") {
				t.Errorf("%v over %s suggests administrator, which does not help: %q", err, path, got)
			}
		}
	}
}
