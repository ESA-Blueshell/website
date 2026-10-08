// Package settings keeps the sender's running totals in Valkey, so a restart resumes the counts.
// The prefix, rate and image are the api's now; only the telemetry lives here.
package settings

import (
	"context"
	"fmt"
	"strconv"

	"github.com/valkey-io/valkey-go"
)

const keyStats = "pinger:stats"

// Stats is the running total the sender keeps across restarts.
type Stats struct {
	Sent, Passes, Errors uint64
}

type Store struct {
	client valkey.Client
}

func Open(addr string) (*Store, error) {
	// ForceSingleClient: our Valkey is one node, not a cluster. It skips the cluster probe and the
	// per-slot routing.
	client, err := valkey.NewClient(valkey.ClientOption{InitAddress: []string{addr}, DisableCache: true, ForceSingleClient: true})
	if err != nil {
		return nil, fmt.Errorf("valkey %s: %w", addr, err)
	}
	return &Store{client: client}, nil
}

func (s *Store) Close() { s.client.Close() }

// LoadStats reads the saved totals; a missing hash reads as zeroes.
func (s *Store) LoadStats(ctx context.Context) (Stats, error) {
	m, err := s.client.Do(ctx, s.client.B().Hgetall().Key(keyStats).Build()).AsStrMap()
	if err != nil {
		return Stats{}, err
	}
	n := func(k string) uint64 { v, _ := strconv.ParseUint(m[k], 10, 64); return v }
	return Stats{Sent: n("sent"), Passes: n("passes"), Errors: n("errors")}, nil
}

func (s *Store) SaveStats(ctx context.Context, st Stats) error {
	return s.client.Do(ctx, s.client.B().Hset().Key(keyStats).
		FieldValue().
		FieldValue("sent", strconv.FormatUint(st.Sent, 10)).
		FieldValue("passes", strconv.FormatUint(st.Passes, 10)).
		FieldValue("errors", strconv.FormatUint(st.Errors, 10)).
		Build()).Error()
}
