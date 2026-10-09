package apipaint

import (
	"math"
	"sync"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
)

// Motion is how a placement's box moves: "static" stays at its origin, "bounce" travels at VX, VY
// pixels a second and reflects off the canvas edges.
type Motion struct {
	Mode string  `json:"mode"`
	VX   float64 `json:"vx"`
	VY   float64 `json:"vy"`
}

// Reflect folds p into [0, l] as a point bouncing between 0 and l would travel. The api and the
// watch page place a moving box with the same rule; change one, change the others.
func Reflect(p, l float64) float64 {
	if l <= 0 {
		return 0
	}
	m := math.Mod(math.Mod(p, 2*l)+2*l, 2*l)
	if m <= l {
		return m
	}
	return 2*l - m
}

// PositionAt is the top-left of the placement's box at server time now. The origin is where the box
// stood at MotionEpoch.
func (p Placement) PositionAt(now time.Time) (x, y int) {
	if p.Motion.Mode != "bounce" {
		return p.OriginX, p.OriginY
	}
	s := now.Sub(p.MotionEpoch).Seconds()
	fx := Reflect(float64(p.OriginX)+p.Motion.VX*s, float64(canvas.Width-p.Width))
	fy := Reflect(float64(p.OriginY)+p.Motion.VY*s, float64(canvas.Height-p.Height))
	return int(math.Round(fx)), int(math.Round(fy))
}

const (
	// clockWeight is how much one sample moves the estimate: a sample is the api's time less this
	// host's at receipt, so each carries the network delay of its own response as noise.
	clockWeight = 0.25
	// clockJump is how far a sample may land from the estimate before it is taken as a reset clock
	// rather than noise.
	clockJump = 2 * time.Second
)

// serverClock estimates the api's clock from this host's, so every pinger bounces a box to the
// same place however wrong its own clock is.
type serverClock struct {
	mu     sync.Mutex
	offset time.Duration
	set    bool
}

// observe takes one sample: the api's time as it sent a descriptor, and this host's as it arrived.
// A zero server time is an api that sends none, and is ignored.
func (c *serverClock) observe(server, local time.Time) {
	if server.IsZero() {
		return
	}
	sample := server.Sub(local)
	c.mu.Lock()
	defer c.mu.Unlock()
	delta := sample - c.offset
	if !c.set || delta > clockJump || delta < -clockJump {
		c.offset, c.set = sample, true
		return
	}
	c.offset += time.Duration(clockWeight * float64(delta))
}

func (c *serverClock) now(local time.Time) time.Time {
	c.mu.Lock()
	defer c.mu.Unlock()
	return local.Add(c.offset)
}
