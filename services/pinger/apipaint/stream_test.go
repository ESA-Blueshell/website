package apipaint

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"image"
	"image/color"
	"image/png"
	"net/http"
	"net/http/httptest"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func event(w http.ResponseWriter, body string) {
	fmt.Fprintf(w, "data: %s\n\n", body)
	w.(http.Flusher).Flush()
}

func descJSON(rate int) string {
	return fmt.Sprintf(`{"prefix":"2001:db8::/64","ratePps":%d,"siteCieEnabled":true,"placements":[],"serverTime":"2026-10-09T12:00:00Z"}`, rate)
}

type collected struct {
	mu sync.Mutex
	ds []Descriptor
}

func (c *collected) add(d Descriptor, _ time.Time) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.ds = append(c.ds, d)
}

func (c *collected) rates() []int {
	c.mu.Lock()
	defer c.mu.Unlock()
	out := []int{}
	for _, d := range c.ds {
		out = append(out, d.RatePPS)
	}
	return out
}

func waitFor(t *testing.T, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(3 * time.Second)
	for !cond() {
		if time.Now().After(deadline) {
			t.Fatal("condition not met in 3s")
		}
		time.Sleep(5 * time.Millisecond)
	}
}

func TestStreamHandsOnTheConnectEventAndEveryChangeButNotTheHeartbeats(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/pinger/paint/stream" || r.Header.Get("X-Forwarded-Proto") != "https" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "text/event-stream")
		event(w, descJSON(1))
		fmt.Fprint(w, ":\n\n")
		w.(http.Flusher).Flush()
		event(w, descJSON(2))
		<-r.Context().Done()
	}))
	defer srv.Close()

	ctx, cancel := context.WithCancel(context.Background())
	got := &collected{}
	var connected atomic.Bool
	done := make(chan error)
	go func() { done <- NewClient(srv.URL).Stream(ctx, func() { connected.Store(true) }, got.add) }()

	waitFor(t, func() bool { return len(got.rates()) == 2 })
	cancel()
	<-done
	if r := got.rates(); r[0] != 1 || r[1] != 2 || !connected.Load() {
		t.Fatalf("rates %v, connected %v", r, connected.Load())
	}
	if want := time.Date(2026, 10, 9, 12, 0, 0, 0, time.UTC); !got.ds[0].ServerTime.Equal(want) {
		t.Fatalf("server time %v, want %v", got.ds[0].ServerTime, want)
	}
}

func TestStreamReportsAnApiWithoutOne(t *testing.T) {
	srv := httptest.NewServer(http.NotFoundHandler())
	defer srv.Close()

	err := NewClient(srv.URL).Stream(context.Background(), func() {}, func(Descriptor, time.Time) {})
	if !errors.Is(err, ErrStreamMissing) {
		t.Fatalf("err %v, want ErrStreamMissing", err)
	}
}

func TestStreamGivesUpOnASilentConnection(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/event-stream")
		w.(http.Flusher).Flush()
		<-r.Context().Done()
	}))
	defer srv.Close()
	c := NewClient(srv.URL)
	c.idle = 50 * time.Millisecond

	done := make(chan error)
	go func() { done <- c.Stream(context.Background(), func() {}, func(Descriptor, time.Time) {}) }()
	select {
	case err := <-done:
		if err == nil {
			t.Fatal("a silent stream ended without an error")
		}
	case <-time.After(2 * time.Second):
		t.Fatal("a silent stream was not dropped")
	}
}

func fastPoller(src Source, sink PixelSink) *Poller {
	p := NewPoller(src, 10*time.Millisecond, sink, nil)
	p.backoffMin, p.backoffMax = time.Millisecond, 5*time.Millisecond
	return p
}

func runPoller(t *testing.T, p *Poller) {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	go func() { p.Run(ctx); close(done) }()
	t.Cleanup(func() { cancel(); <-done })
}

func TestPollerReconnectsWhenTheStreamDrops(t *testing.T) {
	var conns atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/pinger/paint/stream" {
			http.NotFound(w, r)
			return
		}
		n := conns.Add(1)
		event(w, descJSON(int(n)))
		if n >= 2 {
			<-r.Context().Done()
		}
	}))
	// Closed after the poller stops, so a stream still open does not hold it up.
	t.Cleanup(srv.Close)

	p := fastPoller(NewClient(srv.URL), &pixelSink{})
	runPoller(t, p)

	waitFor(t, func() bool { return p.Current().RatePPS == 2 })
}

func TestPollerPollsAnApiWithoutAStream(t *testing.T) {
	var polls atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/pinger/paint" {
			http.NotFound(w, r)
			return
		}
		polls.Add(1)
		fmt.Fprint(w, `{"prefix":"2001:db8::/64","ratePps":77,"siteCieEnabled":true,"placements":[]}`)
	}))
	// Closed after the poller stops, so a stream still open does not hold it up.
	t.Cleanup(srv.Close)

	p := fastPoller(NewClient(srv.URL), &pixelSink{})
	runPoller(t, p)

	waitFor(t, func() bool { return p.Current().RatePPS == 77 && polls.Load() >= 3 })
}

func redPNG() []byte {
	img := image.NewNRGBA(image.Rect(0, 0, 10, 10))
	for y := range 10 {
		for x := range 10 {
			img.SetNRGBA(x, y, color.NRGBA{R: 255, A: 255})
		}
	}
	var buf bytes.Buffer
	_ = png.Encode(&buf, img)
	return buf.Bytes()
}

func TestPollerRetriesAStreamedImageThatFailedToLoad(t *testing.T) {
	var images atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/pinger/paint/stream":
			event(w, `{"prefix":"2001:db8::/64","ratePps":5,"placements":[{"imageUrl":"/a.png","originX":0,"originY":0,"width":10,"height":10,"motion":{"mode":"static","vx":0,"vy":0}}]}`)
			<-r.Context().Done()
		case "/a.png":
			if images.Add(1) == 1 {
				http.Error(w, "busy", http.StatusServiceUnavailable)
				return
			}
			_, _ = w.Write(redPNG())
		default:
			http.NotFound(w, r)
		}
	}))
	// Closed after the poller stops, so a stream still open does not hold it up.
	t.Cleanup(srv.Close)
	sink := &pixelSink{}

	runPoller(t, fastPoller(NewClient(srv.URL), sink))

	waitFor(t, func() bool { n, _ := sink.snapshot(); return n == 100 })
}
