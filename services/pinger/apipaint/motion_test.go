package apipaint

import (
	"testing"
	"time"
)

func TestReflectBouncesOffBothEdges(t *testing.T) {
	cases := []struct {
		p, l, want float64
	}{
		{0, 100, 0},
		{40, 100, 40},
		{100, 100, 100},
		{150, 100, 50},
		{200, 100, 0},
		{250, 100, 50},
		{-30, 100, 30},
		{-150, 100, 50},
		{-200, 100, 0},
		{1030, 100, 30},
		{70, 0, 0},
		{70, -20, 0},
	}
	for _, c := range cases {
		if got := Reflect(c.p, c.l); got != c.want {
			t.Errorf("Reflect(%v, %v) = %v, want %v", c.p, c.l, got, c.want)
		}
	}
}

var epoch = time.Date(2026, 10, 9, 12, 0, 0, 0, time.UTC)

func moving(vx, vy float64) Placement {
	return Placement{
		ImageURL: "/a.webp", OriginX: 100, OriginY: 200, Width: 840, Height: 160,
		Motion: Motion{Mode: "bounce", VX: vx, VY: vy}, MotionEpoch: epoch,
	}
}

func TestPositionFollowsTheBounceFromTheEpoch(t *testing.T) {
	cases := []struct {
		name   string
		p      Placement
		at     time.Duration
		wx, wy int
	}{
		{"at the epoch", moving(300, -50), 0, 100, 200},
		{"one second on", moving(300, -50), time.Second, 400, 150},
		{"past the right edge", moving(300, 0), 10 * time.Second, 2*3000 - 3100, 200},
		{"negative velocity past the top", moving(0, -50), 5 * time.Second, 100, 50},
		{"rounds to whole pixels", moving(0.4, 0.6), time.Second, 100, 201},
		{"before the epoch runs backwards", moving(100, 0), -time.Second, 0, 200},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			x, y := c.p.PositionAt(epoch.Add(c.at))
			if x != c.wx || y != c.wy {
				t.Fatalf("at %v: (%d, %d), want (%d, %d)", c.at, x, y, c.wx, c.wy)
			}
		})
	}
}

func TestStaticAndBoxFillingPlacementsStayAtTheirOrigin(t *testing.T) {
	static := moving(300, 300)
	static.Motion.Mode = "static"
	if x, y := static.PositionAt(epoch.Add(time.Minute)); x != 100 || y != 200 {
		t.Fatalf("static moved to (%d, %d)", x, y)
	}
	none := Placement{OriginX: 7, OriginY: 9, Width: 10, Height: 10}
	if x, y := none.PositionAt(epoch); x != 7 || y != 9 {
		t.Fatalf("placement without motion moved to (%d, %d)", x, y)
	}
	full := moving(300, 300)
	full.OriginX, full.OriginY, full.Width, full.Height = 0, 0, 3840, 2160
	if x, y := full.PositionAt(epoch.Add(time.Minute)); x != 0 || y != 0 {
		t.Fatalf("a canvas-filling box moved to (%d, %d)", x, y)
	}
}

func TestClockEstimatesTheServerOffsetAndSmoothsIt(t *testing.T) {
	var c serverClock
	local := time.Date(2026, 10, 9, 12, 0, 0, 0, time.UTC)
	if got := c.now(local); !got.Equal(local) {
		t.Fatalf("an unset clock reads %v, want local time %v", got, local)
	}

	c.observe(local.Add(2*time.Second), local)
	if got := c.now(local); !got.Equal(local.Add(2 * time.Second)) {
		t.Fatalf("first sample: %v, want local + 2s", got)
	}

	c.observe(local.Add(2*time.Second+400*time.Millisecond), local)
	got := c.now(local).Sub(local)
	if got <= 2*time.Second || got >= 2*time.Second+400*time.Millisecond {
		t.Fatalf("second sample moved the offset to %v, want it smoothed between 2s and 2.4s", got)
	}

	c.observe(local.Add(-time.Minute), local)
	if got := c.now(local).Sub(local); got != -time.Minute {
		t.Fatalf("a jump of a minute smoothed to %v, want it taken at once", got)
	}

	c.observe(time.Time{}, local)
	if got := c.now(local).Sub(local); got != -time.Minute {
		t.Fatalf("an event without a server time moved the offset to %v", got)
	}
}
