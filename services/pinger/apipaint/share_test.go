package apipaint

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
)

func TestKeepsIsDeterministic(t *testing.T) {
	s := Share{From: 0.2, To: 0.55}
	for i := range 1000 {
		if Keeps(i, s) != Keeps(i, s) {
			t.Fatalf("pixel %d flipped", i)
		}
	}
}

func TestAdjacentSharesPartitionThePixels(t *testing.T) {
	bounds := []float64{0, 0.1, 0.35, 0.36, 0.8, 1}
	const n = 20000
	for i := range n {
		owners := 0
		for k := 0; k+1 < len(bounds); k++ {
			if Keeps(i, Share{From: bounds[k], To: bounds[k+1]}) {
				owners++
			}
		}
		if owners != 1 {
			t.Fatalf("pixel %d has %d owners", i, owners)
		}
	}
}

func TestShareCountsAreProportional(t *testing.T) {
	const n = 100000
	for _, s := range []Share{{From: 0, To: 0.5}, {From: 0.5, To: 0.75}, {From: 0.9, To: 1}, {From: 0.123, To: 0.124}} {
		kept := 0
		for i := range n {
			if Keeps(i, s) {
				kept++
			}
		}
		want := (s.To - s.From) * n
		if diff := float64(kept) - want; diff > 0.01*n || diff < -0.01*n {
			t.Fatalf("share %+v kept %d, want about %.0f", s, kept, want)
		}
	}
}

func TestEverythingKeepsEveryPixel(t *testing.T) {
	for i := range 10000 {
		if !Keeps(i, Everything) {
			t.Fatalf("pixel %d dropped", i)
		}
	}
}

type recordingSink struct {
	mu    sync.Mutex
	calls [][]canvas.Pixel
}

func (s *recordingSink) SetPixels(px []canvas.Pixel) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.calls = append(s.calls, px)
}

func (s *recordingSink) count() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return len(s.calls)
}

func (s *recordingSink) last() []canvas.Pixel {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.calls[len(s.calls)-1]
}

func pixels(n int) []canvas.Pixel {
	out := make([]canvas.Pixel, n)
	for i := range out {
		out[i] = canvas.Pixel{X: uint16(i % canvas.Width), Y: uint16(i / canvas.Width)}
	}
	return out
}

func TestFilterKeepsOnlyTheShareOfThePixels(t *testing.T) {
	inner := &recordingSink{}
	f := NewShareFilter(inner)
	all := pixels(1000)
	f.SetPixels(all)
	if got := len(inner.last()); got != 1000 {
		t.Fatalf("before any share the sink got %d pixels, want all", got)
	}
	share := Share{From: 0.25, To: 0.5}
	f.SetShare(share)
	got := inner.last()
	var want []canvas.Pixel
	for i, px := range all {
		if Keeps(i, share) {
			want = append(want, px)
		}
	}
	if len(got) != len(want) {
		t.Fatalf("kept %d, want %d", len(got), len(want))
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("pixel %d is %+v, want %+v", i, got[i], want[i])
		}
	}
}

func TestFilterReappliesOnlyOnARealChange(t *testing.T) {
	inner := &recordingSink{}
	f := NewShareFilter(inner)
	f.SetShare(Share{From: 0, To: 0.5})
	if inner.count() != 0 {
		t.Fatal("a share before any pixels reached the sink")
	}
	f.SetPixels(pixels(100))
	if inner.count() != 1 {
		t.Fatalf("calls %d after pixels", inner.count())
	}
	f.SetShare(Share{From: 0, To: 0.5 + 1e-9, Devices: 3})
	if inner.count() != 1 {
		t.Fatal("a share within epsilon re-applied")
	}
	f.SetShare(Share{From: 0, To: 0.6})
	if inner.count() != 2 {
		t.Fatal("a moved share did not re-apply")
	}
	f.SetPixels(pixels(200))
	if inner.count() != 3 {
		t.Fatal("new pixels did not re-apply")
	}
}

func TestReadShareRejectsMalformedAnswers(t *testing.T) {
	for _, body := range []string{`{"from":0.5,"to":0.5}`, `{"from":-0.1,"to":0.5}`, `{"from":0,"to":1.5}`, `{"from":0.6,"to":0.2}`, `nope`, `{}`} {
		if _, err := parseShare([]byte(body)); err == nil {
			t.Fatalf("%s parsed", body)
		}
	}
	s, err := parseShare([]byte(`{"from":0.25,"to":0.5,"devices":4}`))
	if err != nil || s != (Share{From: 0.25, To: 0.5, Devices: 4}) {
		t.Fatalf("parsed %+v, %v", s, err)
	}
}

// fastStream is a stream with the timings shrunk so a test runs in milliseconds.
func fastStream(base string, authorize Authorizer) *ShareStream {
	s := NewShareStream(base, "dev-1", authorize)
	s.backoffMin, s.backoffMax = 5*time.Millisecond, 20*time.Millisecond
	s.grace = 150 * time.Millisecond
	s.idle = 200 * time.Millisecond
	return s
}

type shareLog struct {
	mu     sync.Mutex
	shares []Share
}

func (l *shareLog) apply(s Share) {
	l.mu.Lock()
	defer l.mu.Unlock()
	l.shares = append(l.shares, s)
}

func (l *shareLog) waitFor(t *testing.T, want Share) {
	t.Helper()
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		l.mu.Lock()
		n := len(l.shares)
		var last Share
		if n > 0 {
			last = l.shares[n-1]
		}
		l.mu.Unlock()
		if n > 0 && last == want {
			return
		}
		time.Sleep(2 * time.Millisecond)
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	t.Fatalf("never applied %+v; applied %+v", want, l.shares)
}

func TestStreamAppliesEachEventWithTheCredential(t *testing.T) {
	var (
		mu                   sync.Mutex
		token, device, proto string
	)
	next := make(chan string, 4)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/pinger/report/share/stream" {
			http.NotFound(w, r)
			return
		}
		mu.Lock()
		token, device, proto = r.Header.Get("Authorization"), r.URL.Query().Get("deviceId"), r.Header.Get("X-Forwarded-Proto")
		mu.Unlock()
		w.Header().Set("Content-Type", "text/event-stream")
		fmt.Fprint(w, "data: {\"from\":0,\"to\":0.5,\"devices\":2}\n\n")
		w.(http.Flusher).Flush()
		for {
			select {
			case <-r.Context().Done():
				return
			case ev := <-next:
				fmt.Fprint(w, ev)
				w.(http.Flusher).Flush()
			}
		}
	}))
	defer srv.Close()

	log := &shareLog{}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	s := fastStream(srv.URL+"/", Bearer(func(context.Context) (string, error) { return "tok", nil }))
	go s.Run(ctx, log.apply)

	log.waitFor(t, Share{From: 0, To: 0.5, Devices: 2})
	next <- ": heartbeat\n\n"
	next <- "event: share\ndata: {\"from\":0.5,\n" + "data: \"to\":1,\"devices\":2}\n\n"
	log.waitFor(t, Share{From: 0.5, To: 1, Devices: 2})

	mu.Lock()
	defer mu.Unlock()
	if token != "Bearer tok" || device != "dev-1" || proto != "https" {
		t.Fatalf("auth %q device %q proto %q", token, device, proto)
	}
}

func TestStreamFallsBackToEverythingWithoutACredential(t *testing.T) {
	var hits atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(http.ResponseWriter, *http.Request) { hits.Add(1) }))
	defer srv.Close()
	log := &shareLog{}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go fastStream(srv.URL, ServiceToken("")).Run(ctx, log.apply)
	log.waitFor(t, Everything)
	if hits.Load() != 0 {
		t.Fatal("asked the api without a credential")
	}
	go fastStream(srv.URL, Bearer(func(context.Context) (string, error) { return "", errors.New("signed out") })).Run(ctx, log.apply)
	log.waitFor(t, Everything)
	if hits.Load() != 0 {
		t.Fatal("asked the api while signed out")
	}
}

func TestStreamKeepsTheShareThroughAShortDropThenFallsBack(t *testing.T) {
	var up atomic.Bool
	up.Store(true)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !up.Load() {
			w.WriteHeader(http.StatusServiceUnavailable)
			return
		}
		fmt.Fprint(w, "data: {\"from\":0.2,\"to\":0.4,\"devices\":5}\n\n")
		w.(http.Flusher).Flush()
		up.Store(false)
	}))
	defer srv.Close()
	log := &shareLog{}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go fastStream(srv.URL, ServiceToken("svc")).Run(ctx, log.apply)
	log.waitFor(t, Share{From: 0.2, To: 0.4, Devices: 5})
	log.waitFor(t, Everything)
}

func TestStreamReconnectsAfterSilence(t *testing.T) {
	var conns atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		n := conns.Add(1)
		fmt.Fprintf(w, "data: {\"from\":0,\"to\":0.%d,\"devices\":1}\n\n", n)
		w.(http.Flusher).Flush()
		<-r.Context().Done()
	}))
	defer srv.Close()
	log := &shareLog{}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go fastStream(srv.URL, ServiceToken("svc")).Run(ctx, log.apply)
	log.waitFor(t, Share{From: 0, To: 0.2, Devices: 1})
}

func TestStreamMalformedEventPaintsEverything(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, "data: {\"from\":0.1,\"to\":0.3}\n\ndata: {\"from\":2}\n\n")
		w.(http.Flusher).Flush()
		<-r.Context().Done()
	}))
	defer srv.Close()
	log := &shareLog{}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go fastStream(srv.URL, ServiceToken("svc")).Run(ctx, log.apply)
	log.waitFor(t, Everything)
}

func TestStreamFallsBackToThePlainGetOnAnOlderApi(t *testing.T) {
	var (
		mu                   sync.Mutex
		token, device, proto string
	)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/pinger/report/share" {
			http.NotFound(w, r)
			return
		}
		mu.Lock()
		token, device, proto = r.Header.Get("X-Pinger-Service-Token"), r.URL.Query().Get("deviceId"), r.Header.Get("X-Forwarded-Proto")
		mu.Unlock()
		fmt.Fprint(w, `{"from":0.5,"to":0.75,"devices":4}`)
	}))
	defer srv.Close()
	log := &shareLog{}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go fastStream(srv.URL, ServiceToken("svc")).Run(ctx, log.apply)
	log.waitFor(t, Share{From: 0.5, To: 0.75, Devices: 4})

	mu.Lock()
	defer mu.Unlock()
	if token != "svc" || device != "dev-1" || proto != "https" {
		t.Fatalf("token %q device %q proto %q", token, device, proto)
	}
}

func TestStreamPlainGetFailurePaintsEverything(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/pinger/report/share" {
			w.WriteHeader(http.StatusInternalServerError)
			return
		}
		http.NotFound(w, r)
	}))
	defer srv.Close()
	log := &shareLog{}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go fastStream(srv.URL, ServiceToken("svc")).Run(ctx, log.apply)
	log.waitFor(t, Everything)
}
