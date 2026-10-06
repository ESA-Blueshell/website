package paint

import "time"

const (
	backoffFloor = time.Millisecond
	backoffCap   = time.Second
)

// Backoff spaces out retries after a local send error such as a full socket buffer.
// The event never replies, so a local error is the only signal there is to slow down on.
type Backoff struct {
	next time.Duration
}

func (b *Backoff) Next() time.Duration {
	if b.next == 0 {
		b.next = backoffFloor
	}
	d := b.next
	b.next = min(b.next*2, backoffCap)
	return d
}

func (b *Backoff) Reset() { b.next = 0 }
