// Package settings keeps the sender's settings in Valkey, so a restart resumes where an admin left it.
package settings

import (
	"context"
	"fmt"
	"strconv"
	"sync/atomic"
	"time"

	"github.com/valkey-io/valkey-go"

	"github.com/ESA-Blueshell/website/services/pinger/internal/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/internal/paint"
)

const (
	keyPrefix = "pinger:prefix"
	keyRate   = "pinger:rate_pps"
	keyPaused = "pinger:paused"

	DefaultRatePPS = 50_000
)

type Store struct {
	client valkey.Client
}

func Open(addr string) (*Store, error) {
	client, err := valkey.NewClient(valkey.ClientOption{InitAddress: []string{addr}, DisableCache: true})
	if err != nil {
		return nil, fmt.Errorf("valkey %s: %w", addr, err)
	}
	return &Store{client: client}, nil
}

func (s *Store) Close() { s.client.Close() }

// Load fills a missing key with its default: no prefix, the default rate, not paused.
func (s *Store) Load(ctx context.Context) (paint.Settings, error) {
	values, err := s.client.Do(ctx, s.client.B().Mget().Key(keyPrefix, keyRate, keyPaused).Build()).ToArray()
	if err != nil {
		return paint.Settings{}, err
	}
	out := paint.Settings{RatePPS: DefaultRatePPS}
	if v, err := values[0].ToString(); err == nil && v != "" {
		if out.Prefix, err = canvas.ParsePrefix(v); err != nil {
			return paint.Settings{}, err
		}
	}
	if v, err := values[1].ToString(); err == nil {
		if out.RatePPS, err = strconv.Atoi(v); err != nil {
			return paint.Settings{}, fmt.Errorf("%s: %w", keyRate, err)
		}
	}
	if v, err := values[2].ToString(); err == nil {
		out.Paused = v == "1"
	}
	return out, nil
}

func (s *Store) Save(ctx context.Context, v paint.Settings) error {
	prefix := ""
	if !v.Prefix.IsZero() {
		prefix = v.Prefix.String()
	}
	paused := "0"
	if v.Paused {
		paused = "1"
	}
	return s.client.Do(ctx, s.client.B().Mset().KeyValue().
		KeyValue(keyPrefix, prefix).
		KeyValue(keyRate, strconv.Itoa(v.RatePPS)).
		KeyValue(keyPaused, paused).
		Build()).Error()
}

// Watcher polls the store and hands the sender the latest settings it could read. Until the first
// read succeeds it holds the zero Settings, which has no prefix, so the sender stays idle.
type Watcher struct {
	store    *Store
	interval time.Duration
	current  atomic.Pointer[paint.Settings]
}

func NewWatcher(store *Store, interval time.Duration) *Watcher {
	w := &Watcher{store: store, interval: interval}
	w.current.Store(&paint.Settings{})
	return w
}

func (w *Watcher) Current() paint.Settings { return *w.current.Load() }

// Save stores v and makes it current without waiting for the next poll.
func (w *Watcher) Save(ctx context.Context, v paint.Settings) error {
	if err := w.store.Save(ctx, v); err != nil {
		return err
	}
	w.current.Store(&v)
	return nil
}

func (w *Watcher) Run(ctx context.Context) {
	tick := time.NewTicker(w.interval)
	defer tick.Stop()
	for {
		if v, err := w.store.Load(ctx); err == nil {
			w.current.Store(&v)
		}
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
		}
	}
}
