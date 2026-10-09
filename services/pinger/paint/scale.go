package paint

import (
	"math"
	"time"
)

const (
	scaleEvery = 250 * time.Millisecond
	// scaleHold is how long a worker count that did not add throughput stays the ceiling before
	// the scaler probes past it again.
	scaleHold = 30 * time.Second
)

// scaler picks how many workers send. More workers than the rate needs do not send faster: they
// queue on the same kernel locks and burn a core each while they wait, so it starts at one,
// doubles while the workers are saturated and behind, and backs off a doubling that added nothing.
type scaler struct {
	max          int
	active       int
	prevActive   int
	prevPPS      float64
	ceiling      int
	ceilingUntil time.Time
}

func newScaler(max int) *scaler { return &scaler{max: max, active: 1} }

// step takes one interval's throughput and the cores the workers spent inside their sends, and
// returns the worker count for the next interval.
func (c *scaler) step(now time.Time, pps, busyCores, ratePPS float64) int {
	if pps <= 0 || ratePPS <= 0 {
		c.prevActive = 0
		return c.active
	}
	if c.prevActive != 0 {
		grew := pps >= c.prevPPS*1.1
		if !grew {
			c.active, c.ceiling, c.ceilingUntil = c.prevActive, c.prevActive, now.Add(scaleHold)
		}
		c.prevActive = 0
		if !grew {
			return c.active
		}
	}
	ceiling := c.max
	if now.Before(c.ceilingUntil) {
		ceiling = c.ceiling
	}
	behind := pps < 0.95*ratePPS
	saturated := busyCores > 0.8*float64(c.active)
	switch {
	case behind && saturated && c.active < ceiling:
		c.prevActive, c.prevPPS = c.active, pps
		c.active = min(ceiling, c.active*2)
	case !behind:
		need := int(math.Ceil(busyCores * ratePPS / pps * 1.25))
		c.active = max(1, min(c.active, need))
	}
	return c.active
}
