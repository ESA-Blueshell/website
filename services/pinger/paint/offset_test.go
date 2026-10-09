package paint

import (
	"net/netip"
	"testing"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
)

func twoPlacements() []canvas.Pixel {
	return []canvas.Pixel{
		{X: 10, Y: 20, A: 0xff, Placement: 0},
		{X: 30, Y: 40, A: 0xff, Placement: 1},
		{X: 3830, Y: 2150, A: 0xff, Placement: 1},
	}
}

func addressSet(p canvas.Prefix, px []canvas.Pixel) map[netip.Addr]bool {
	out := map[netip.Addr]bool{}
	for _, x := range px {
		out[p.Address(x)] = true
	}
	return out
}

func TestSenderAddsEachPlacementsOffsetToItsPixels(t *testing.T) {
	conn := &fakeConn{}
	box := &settingsBox{}
	p := prefix(t, "2001:db8::/64")
	box.set(Settings{Prefix: p, RatePPS: 100_000, Enabled: true})

	s := start(t, conn, nil, open, box)
	s.SetOffsets([]Offset{{X: 0, Y: 0}, {X: 5, Y: -3}})
	s.SetPixels(twoPlacements())
	eventually(t, func() bool { return s.Snapshot().Passes >= 3 })

	want := addressSet(p, []canvas.Pixel{{X: 10, Y: 20, A: 0xff}, {X: 35, Y: 37, A: 0xff}, {X: 3835, Y: 2147, A: 0xff}})
	for _, a := range conn.addresses() {
		if !want[a] {
			t.Fatalf("sent %s, want only %v", a, want)
		}
	}
	got := addressSet(p, nil)
	for _, a := range conn.addresses() {
		got[a] = true
	}
	if len(got) != len(want) {
		t.Fatalf("sent %d distinct addresses, want %d", len(got), len(want))
	}
}

func TestSenderSkipsPixelsAnOffsetPushesOffTheCanvas(t *testing.T) {
	conn := &fakeConn{}
	box := &settingsBox{}
	p := prefix(t, "2001:db8::/64")
	box.set(Settings{Prefix: p, RatePPS: 100_000, Enabled: true})

	s := start(t, conn, nil, open, box)
	s.SetOffsets([]Offset{{X: -11, Y: 0}, {X: 11, Y: 0}})
	s.SetPixels(twoPlacements())
	eventually(t, func() bool { return s.Snapshot().Passes >= 3 })

	want := p.Address(canvas.Pixel{X: 41, Y: 40, A: 0xff})
	for _, a := range conn.addresses() {
		if a != want {
			t.Fatalf("sent %s, want only %s", a, want)
		}
	}
	if s.Snapshot().Failing || s.Snapshot().Errors != 0 {
		t.Fatalf("skipped pixels count as failures: %+v", s.Snapshot())
	}
}

func TestSenderPaintsEveryPixelWhereItIsWithoutOffsets(t *testing.T) {
	conn := &fakeConn{}
	box := &settingsBox{}
	p := prefix(t, "2001:db8::/64")
	box.set(Settings{Prefix: p, RatePPS: 100_000, Enabled: true})
	px := twoPlacements()

	s := start(t, conn, px, open, box)
	s.SetOffsets([]Offset{{}, {}})
	eventually(t, func() bool { return s.Snapshot().Passes >= 2 })

	want := addressSet(p, px)
	got := addressSet(p, nil)
	for _, a := range conn.addresses() {
		got[a] = true
	}
	if len(got) != len(want) {
		t.Fatalf("sent %d distinct addresses, want %d", len(got), len(want))
	}
	for a := range got {
		if !want[a] {
			t.Fatalf("sent %s, not one of the pixels", a)
		}
	}
}

func TestSenderFollowsAMovingOffsetMidPass(t *testing.T) {
	conn := &fakeConn{}
	box := &settingsBox{}
	p := prefix(t, "2001:db8::/64")
	box.set(Settings{Prefix: p, RatePPS: 2000, Enabled: true})
	px := []canvas.Pixel{{X: 100, Y: 100, A: 0xff}}

	s := start(t, conn, px, open, box)
	eventually(t, func() bool { return len(conn.addresses()) > 0 })
	s.SetOffsets([]Offset{{X: 7, Y: 0}})
	moved := p.Address(canvas.Pixel{X: 107, Y: 100, A: 0xff})
	eventually(t, func() bool {
		sent := conn.addresses()
		return sent[len(sent)-1] == moved
	})
	if got := s.Snapshot().PassTotal; got != 1 {
		t.Fatalf("pass total %d after a move, want 1", got)
	}
}
