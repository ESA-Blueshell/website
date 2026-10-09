package paint

import (
	"context"
	"time"
)

// pacer holds one worker to its share of the rate on an absolute schedule, so the time a sleep
// oversleeps is made up on the next chunk rather than lost. Workers pace alone: a shared limiter
// is a mutex every worker takes per chunk.
type pacer struct {
	next  time.Time
	timer *time.Timer
}

// chunkSize is how many packets a worker sends per wake at share packets a second.
func chunkSize(share float64) int {
	return max(1, min(chunkMax, int(share*chunkSpan.Seconds())))
}

// take waits until n more packets fit the schedule at share packets a second, and books them. It
// reports false when ctx ends first.
func (p *pacer) take(ctx context.Context, n int, share float64) bool {
	now := time.Now()
	if floor := now.Add(-paceSlack); p.next.Before(floor) {
		p.next = floor
	}
	if wait := p.next.Sub(now); wait > 0 {
		if p.timer == nil {
			p.timer = time.NewTimer(wait)
		} else {
			p.timer.Reset(wait)
		}
		select {
		case <-ctx.Done():
			p.timer.Stop()
			return false
		case <-p.timer.C:
		}
	}
	p.next = p.next.Add(time.Duration(float64(n) / share * float64(time.Second)))
	return true
}
