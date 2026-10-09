package apipaint

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"time"
)

// ErrStreamMissing is an api that serves no paint stream; the poller polls it instead.
var ErrStreamMissing = errors.New("paint stream: not found")

// Streamer is a Source that can also push the descriptor as it changes.
type Streamer interface {
	// Stream holds one connection open and hands apply each descriptor with the time it arrived,
	// until the connection drops or stays silent past the idle limit. connected is called once the
	// api accepts the connection. apply is never called concurrently.
	Stream(ctx context.Context, connected func(), apply func(Descriptor, time.Time)) error
}

// streamIdle is how long the paint stream may stay silent before it is taken for dead; the api
// sends a heartbeat every 15 s.
const streamIdle = 45 * time.Second

func (c *Client) Stream(ctx context.Context, connected func(), apply func(Descriptor, time.Time)) error {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.base+"/pinger/paint/stream", nil)
	if err != nil {
		return err
	}
	req.Header.Set("Accept", "text/event-stream")
	watchdog := time.AfterFunc(c.idle, cancel)
	defer watchdog.Stop()
	resp, err := c.streamHC.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode == http.StatusNotFound {
		return ErrStreamMissing
	}
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("paint stream: status %d", resp.StatusCode)
	}
	connected()
	return readEvents(resp.Body, func() { watchdog.Reset(c.idle) }, func(data []byte) {
		at := time.Now()
		var d Descriptor
		if err := json.Unmarshal(data, &d); err != nil {
			slog.Warn("paint stream event", "err", err)
			return
		}
		apply(d, at)
	})
}
