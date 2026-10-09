// Package headroom keeps the member's connection usable while the app pings. A flood of pings fills
// the router's upload queue, and every TCP handshake and ACK the member's other traffic needs then
// waits behind it, so downloads and the app's own api calls stall too. The controller measures that
// queue directly: it times a TCP handshake to the api, compares it with the quietest handshake seen
// lately, and lowers a ceiling on the send rate whenever the difference grows past a small target.
// It only ever lowers the member's chosen rate, never raises it.
package headroom

import (
	"context"
	"errors"
	"sync"
	"time"
)

// ErrLost marks a probe that got no answer in time. It counts as a full queue, because a queue
// that drops or delays a handshake this long is exactly what the controller backs away from.
var ErrLost = errors.New("headroom: probe lost")

const (
	// Interval is how often the controller probes and steps.
	Interval = 500 * time.Millisecond
	// lostRTT is what a lost probe counts as, well past any target.
	lostRTT = 1500 * time.Millisecond
	// target is the queueing delay the controller tolerates before backing off. Calls and games
	// stay comfortable under it; a saturated home uplink queues for seconds.
	target = 60 * time.Millisecond
	// window is how many recent probes the delay is the minimum of, so one slow handshake on a
	// noisy Wi-Fi link does not count as a queue.
	window = 3
	// A queue is judged once per cooldown, which outlasts the window. It is cut again only when it
	// has not shrunk by draining since the last judgement: a queue already draining needs no
	// deeper cut, and cutting it anyway lands the rate far below what the link carries.
	cooldown = 2 * time.Second
	draining = 0.85
	// The quietest handshake is kept per baseSpan over baseBuckets spans, so a lasting route
	// change ages out of the baseline rather than reading as a queue forever.
	baseSpan    = 30 * time.Second
	baseBuckets = 6
	// A run of unanswered probes this long means the api is unreachable rather than queued
	// behind the pings, and the controller stops capping rather than throttle blind: giveUp before
	// any probe has answered, unreachable after. A queue drains well within unreachable at the floor.
	giveUp      = 6
	unreachable = 60

	startPPS = 1000
	floorPPS = 100
	backoff  = 0.7
	// fastGrowth is per step away from the last knee; gentleGrowth is per step near it.
	fastGrowth   = 1.25
	gentleGrowth = 1.04
)

// Load is what the sender is doing when a probe lands.
type Load struct {
	Running   bool
	ActualPPS uint64
	// Want is the rate the member chose; the ceiling never rises above it.
	Want int
}

type bucket struct {
	start time.Time
	min   time.Duration
}

// Controller holds the ceiling. Observe feeds it probe results; Limit applies it to a rate.
type Controller struct {
	mu       sync.Mutex
	ceiling  float64
	knee     float64
	recent   []time.Duration
	base     []bucket
	judged   time.Time
	judgedAt time.Duration
	silent   int
	disabled bool
}

// New starts the ceiling low, so the first seconds of a run ramp up rather than flood.
func New() *Controller {
	return &Controller{ceiling: startPPS}
}

// SetEnabled turns the cap on or off; off sends at the member's rate however the queue looks.
func (c *Controller) SetEnabled(on bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.disabled = !on
}

// Enabled reports whether the cap is on.
func (c *Controller) Enabled() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return !c.disabled
}

// Limit is the rate to send at for a chosen rate: the chosen rate, or the ceiling when it is lower.
func (c *Controller) Limit(want int) int {
	c.mu.Lock()
	defer c.mu.Unlock()
	if !c.capping() || want <= 0 {
		return want
	}
	return min(want, int(c.ceiling))
}

// Held is the ceiling when it is holding a chosen rate back, and zero when it is not.
func (c *Controller) Held(want int) int {
	if got := c.Limit(want); got < want {
		return got
	}
	return 0
}

func (c *Controller) capping() bool {
	switch {
	case c.disabled:
		return false
	case len(c.base) == 0:
		return c.silent < giveUp
	default:
		return c.silent < unreachable
	}
}

// Observe steps the controller on one probe: rtt when err is nil, a full queue when err is
// ErrLost, and nothing but a miss for any other error.
func (c *Controller) Observe(now time.Time, rtt time.Duration, err error, load Load) {
	c.mu.Lock()
	defer c.mu.Unlock()
	switch {
	case err == nil:
		c.silent = 0
		c.noteBase(now, rtt)
		c.push(rtt)
	case errors.Is(err, ErrLost):
		c.silent++
		c.push(lostRTT)
	default:
		c.silent++
		return
	}
	if len(c.base) == 0 || !load.Running || load.Want <= 0 {
		return
	}
	if c.ceiling > float64(load.Want) {
		c.ceiling = float64(load.Want)
	}

	if q := c.queue(); q > target {
		if now.Sub(c.judged) < cooldown {
			return
		}
		if c.judgedAt == 0 || float64(q) > draining*float64(c.judgedAt) {
			from := c.ceiling
			if load.ActualPPS > 0 && float64(load.ActualPPS) < from {
				from = float64(load.ActualPPS)
			}
			c.knee = from
			c.ceiling = max(floorPPS, from*backoff)
		}
		c.judged, c.judgedAt = now, q
		return
	}
	c.judgedAt = 0

	// A ceiling the sender is not reaching (a slow machine, a pass between images) is not being
	// tested, and growing it would only overshoot further once the sender catches up.
	if float64(load.ActualPPS) < c.ceiling/2 {
		return
	}
	growth := gentleGrowth
	if c.knee == 0 || c.ceiling < 0.6*c.knee || c.ceiling > 1.25*c.knee {
		growth = fastGrowth
	}
	c.ceiling = min(c.ceiling*growth+50, float64(load.Want))
}

func (c *Controller) push(rtt time.Duration) {
	c.recent = append(c.recent, rtt)
	if len(c.recent) > window {
		c.recent = c.recent[len(c.recent)-window:]
	}
}

func (c *Controller) noteBase(now time.Time, rtt time.Duration) {
	if n := len(c.base); n == 0 || now.Sub(c.base[n-1].start) >= baseSpan {
		c.base = append(c.base, bucket{start: now, min: rtt})
		if len(c.base) > baseBuckets {
			c.base = c.base[len(c.base)-baseBuckets:]
		}
		return
	}
	last := &c.base[len(c.base)-1]
	last.min = min(last.min, rtt)
}

// queue is the recent handshake time above the quietest one seen lately.
func (c *Controller) queue() time.Duration {
	recent := c.recent[0]
	for _, r := range c.recent[1:] {
		recent = min(recent, r)
	}
	base := c.base[0].min
	for _, b := range c.base[1:] {
		base = min(base, b.min)
	}
	return recent - base
}

// Run probes every Interval until ctx ends, stepping the controller on each result.
func (c *Controller) Run(ctx context.Context, probe func(context.Context) (time.Duration, error), load func() Load) {
	tick := time.NewTicker(Interval)
	defer tick.Stop()
	for {
		rtt, err := probe(ctx)
		if ctx.Err() != nil {
			return
		}
		c.Observe(time.Now(), rtt, err, load())
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
		}
	}
}
