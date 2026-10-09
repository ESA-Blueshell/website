package headroom

import (
	"context"
	"errors"
	"testing"
	"time"
)

var t0 = time.Date(2026, 10, 9, 20, 0, 0, 0, time.UTC)

// link is a home uplink with a deep buffer: whatever is sent above capacity queues, and every
// handshake waits behind the queue.
type link struct {
	capacity float64
	buffer   float64
	queued   float64
}

func (l *link) send(pps float64, dt time.Duration) time.Duration {
	l.queued = min(l.buffer, max(0, l.queued+(pps-l.capacity)*dt.Seconds()))
	return 20*time.Millisecond + time.Duration(l.queued/l.capacity*float64(time.Second))
}

// drive runs the controller against the link for d, returning the mean rate and queueing delay over
// the last half.
func drive(c *Controller, l *link, want int, d time.Duration) (meanPPS float64, meanDelay time.Duration) {
	steps := int(d / Interval)
	var sumPPS float64
	var sumDelay time.Duration
	now := t0
	for i := range steps {
		pps := c.Limit(want)
		rtt := l.send(float64(pps), Interval)
		now = now.Add(Interval)
		c.Observe(now, rtt, nil, Load{Running: true, ActualPPS: uint64(pps), Want: want})
		if i >= steps/2 {
			sumPPS += float64(pps)
			sumDelay += rtt - 20*time.Millisecond
		}
	}
	n := steps - steps/2
	return sumPPS / float64(n), sumDelay / time.Duration(n)
}

func TestHoldsASaturatedUplinkBelowCapacity(t *testing.T) {
	l := &link{capacity: 20_000, buffer: 60_000}
	pps, delay := drive(New(), l, 2_000_000, 3*time.Minute)
	if pps < 0.6*l.capacity || pps > l.capacity {
		t.Errorf("mean rate %.0f pps, want between 60%% and 100%% of %.0f", pps, l.capacity)
	}
	if delay > 100*time.Millisecond {
		t.Errorf("mean queueing delay %v, want the queue kept short", delay)
	}
}

func TestReachesTheChosenRateWhenTheLinkHasRoom(t *testing.T) {
	c := New()
	l := &link{capacity: 1_000_000, buffer: 3_000_000}
	drive(c, l, 50_000, 30*time.Second)
	if got := c.Limit(50_000); got != 50_000 {
		t.Errorf("Limit = %d, want the chosen 50000", got)
	}
	if held := c.Held(50_000); held != 0 {
		t.Errorf("Held = %d, want 0 once the chosen rate is reached", held)
	}
}

func TestNeverExceedsTheChosenRate(t *testing.T) {
	c := New()
	for i := range 40 {
		c.Observe(t0.Add(time.Duration(i)*Interval), 20*time.Millisecond, nil, Load{Running: true, ActualPPS: 300, Want: 300})
		if got := c.Limit(300); got > 300 {
			t.Fatalf("Limit = %d, above the chosen 300", got)
		}
	}
	if got := c.Limit(100); got != 100 {
		t.Errorf("Limit = %d after lowering the rate, want 100", got)
	}
}

func TestStartsLowAndHoldsTheRateBack(t *testing.T) {
	c := New()
	if got := c.Limit(100_000); got != startPPS {
		t.Errorf("Limit = %d, want the start ceiling %d", got, startPPS)
	}
	if got := c.Held(100_000); got != startPPS {
		t.Errorf("Held = %d, want %d", got, startPPS)
	}
	if got := c.Limit(0); got != 0 {
		t.Errorf("Limit(0) = %d, want 0", got)
	}
}

func TestDisabledSendsAtTheChosenRate(t *testing.T) {
	c := New()
	c.SetEnabled(false)
	if c.Enabled() {
		t.Error("Enabled = true after SetEnabled(false)")
	}
	if got := c.Limit(100_000); got != 100_000 {
		t.Errorf("Limit = %d, want the chosen 100000 with the cap off", got)
	}
	c.SetEnabled(true)
	if got := c.Limit(100_000); got != startPPS {
		t.Errorf("Limit = %d, want the ceiling back once enabled", got)
	}
}

func TestIgnoresTheQueueWhileNotSending(t *testing.T) {
	c := New()
	c.Observe(t0, 20*time.Millisecond, nil, Load{Running: false, Want: 100_000})
	c.Observe(t0.Add(Interval), time.Second, nil, Load{Running: false, Want: 100_000})
	if got := c.Limit(100_000); got != startPPS {
		t.Errorf("Limit = %d, want the ceiling untouched while idle", got)
	}
}

func TestLostProbesCountAsAFullQueue(t *testing.T) {
	c := New()
	load := Load{Running: true, ActualPPS: startPPS, Want: 100_000}
	c.Observe(t0, 20*time.Millisecond, nil, load)
	before := c.Limit(100_000)
	for i := 1; i <= window; i++ {
		c.Observe(t0.Add(time.Duration(i)*cooldown), 0, ErrLost, load)
	}
	if got := c.Limit(100_000); got >= before {
		t.Errorf("Limit = %d, want it cut below %d after lost probes", got, before)
	}
}

func TestBacksOffNoFurtherThanTheFloor(t *testing.T) {
	c := New()
	c.Observe(t0, 20*time.Millisecond, nil, Load{Running: true, Want: 100_000})
	for i := 1; i < 50; i++ {
		c.Observe(t0.Add(time.Duration(i)*cooldown), time.Second, nil, Load{Running: true, ActualPPS: 50, Want: 100_000})
	}
	if got := c.Limit(100_000); got != floorPPS {
		t.Errorf("Limit = %d, want the floor %d", got, floorPPS)
	}
}

func TestStopsCappingWhenTheApiNeverAnswers(t *testing.T) {
	c := New()
	refused := errors.New("connection refused")
	for i := range giveUp {
		c.Observe(t0.Add(time.Duration(i)*Interval), 0, refused, Load{Running: true, Want: 100_000})
	}
	if got := c.Limit(100_000); got != 100_000 {
		t.Errorf("Limit = %d, want no cap without any answer", got)
	}
}

func TestStopsCappingWhenTheApiGoesAway(t *testing.T) {
	c := New()
	load := Load{Running: true, ActualPPS: startPPS, Want: 100_000}
	c.Observe(t0, 20*time.Millisecond, nil, load)
	for i := 1; i < unreachable; i++ {
		c.Observe(t0.Add(time.Duration(i)*Interval), 0, ErrLost, load)
	}
	if got := c.Limit(100_000); got == 100_000 {
		t.Fatal("cap lifted before the api counted as unreachable")
	}
	c.Observe(t0.Add(time.Minute), 0, ErrLost, load)
	if got := c.Limit(100_000); got != 100_000 {
		t.Errorf("Limit = %d, want no cap once the api is unreachable", got)
	}
}

func TestALastingRouteChangeAgesOutOfTheBaseline(t *testing.T) {
	c := New()
	c.Observe(t0, 10*time.Millisecond, nil, Load{})
	now := t0
	for range int(baseSpan*baseBuckets/Interval) + 1 {
		now = now.Add(Interval)
		c.Observe(now, 200*time.Millisecond, nil, Load{})
	}
	if q := c.queue(); q != 0 {
		t.Errorf("queue = %v, want the new route as the baseline", q)
	}
}

func TestRunProbesUntilCancelled(t *testing.T) {
	c := New()
	ctx, cancel := context.WithCancel(context.Background())
	probes := 0
	probe := func(context.Context) (time.Duration, error) {
		probes++
		if probes == 2 {
			cancel()
		}
		return 20 * time.Millisecond, nil
	}
	done := make(chan struct{})
	go func() {
		c.Run(ctx, probe, func() Load { return Load{} })
		close(done)
	}()
	select {
	case <-done:
	case <-time.After(5 * time.Second):
		t.Fatal("Run did not return after cancel")
	}
	if probes != 2 {
		t.Errorf("probes = %d, want 2", probes)
	}
	if len(c.base) != 1 {
		t.Errorf("base buckets = %d, want the first probe recorded", len(c.base))
	}
}
