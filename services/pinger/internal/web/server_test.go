package web

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/coder/websocket"

	"github.com/ESA-Blueshell/website/services/pinger/internal/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/internal/paint"
)

type fakeStats paint.Stats

func (f fakeStats) Snapshot() paint.Stats { return paint.Stats(f) }

func serverFor(settings paint.Settings) http.Handler {
	stats := fakeStats{State: paint.Running, Sent: 1_234_567, Errors: 3, Passes: 6, PassDone: 50_000, PassTotal: 202_158, ActualPPS: 49_876, LastError: "no buffer space available"}
	return NewServer(stats, func() paint.Settings { return settings }, func() []byte { return []byte("png") })
}

func get(t *testing.T, h http.Handler, path string) (int, string) {
	t.Helper()
	r := httptest.NewRequest(http.MethodGet, path, nil)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	body, _ := io.ReadAll(w.Result().Body)
	return w.Code, string(body)
}

func TestThePageShowsHowThePaintingGoes(t *testing.T) {
	p, _ := canvas.ParsePrefix("2001:db8:b317:a000::/64")
	h := serverFor(paint.Settings{Prefix: p, RatePPS: 50_000})

	code, body := get(t, h, "/")

	if code != http.StatusOK {
		t.Fatalf("status %d", code)
	}
	for _, want := range []string{"running", "1,235K", "49,876", "50,000", "2001:db8:b317:a000::/64", "24%", "no buffer space available", `/ws`} {
		if !strings.Contains(body, want) {
			t.Errorf("page lacks %q", want)
		}
	}
	// The pinger is steered from the main site, so the page carries no form at all.
	if strings.Contains(body, "<form") {
		t.Error("the watch page has a form")
	}
}

func TestLiveJSONStandsAlone(t *testing.T) {
	h := serverFor(paint.Settings{RatePPS: 50_000})

	code, body := get(t, h, "/live.json")

	var st map[string]any
	if code != http.StatusOK || strings.Contains(body, "<html") || json.Unmarshal([]byte(body), &st) != nil || st["sent"] != "1,235K" || st["state"] != "running" {
		t.Fatalf("status %d, body %q", code, body)
	}
}

func TestThePreviewIsThePlacedImage(t *testing.T) {
	h := serverFor(paint.Settings{})

	code, body := get(t, h, "/preview.png")

	if code != http.StatusOK || body != "png" {
		t.Fatalf("status %d, body %q", code, body)
	}
}

func TestThePreviewIs404WithoutAnImage(t *testing.T) {
	h := NewServer(fakeStats{}, func() paint.Settings { return paint.Settings{} }, func() []byte { return nil })

	code, _ := get(t, h, "/preview.png")

	if code != http.StatusNotFound {
		t.Fatalf("status %d, want 404", code)
	}
}

func TestCompactShortensBigTalliesAndKeepsSmallOnesExact(t *testing.T) {
	cases := map[any]string{
		uint64(0): "0", 42: "42", uint64(9_999): "9,999",
		uint64(10_000): "10K", 50_000: "50K", uint64(202_158): "202K",
		uint64(1_234_567): "1,235K", uint64(18_710_842): "19M", uint64(3_400_000_000): "3,400M",
		uint64(4_132_000_000_000): "4,132B", uint64(2_000_000_000_000): "2,000B",
	}
	for in, want := range cases {
		if got := compact(in); got != want {
			t.Errorf("compact(%v) = %q, want %q", in, got, want)
		}
	}
}

func TestTheSocketPushesTheLiveRegion(t *testing.T) {
	p, _ := canvas.ParsePrefix("2001:db8:b317:a000::/64")
	srv := httptest.NewServer(serverFor(paint.Settings{Prefix: p, RatePPS: 50_000}))
	defer srv.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	c, _, err := websocket.Dial(ctx, "ws"+strings.TrimPrefix(srv.URL, "http")+"/ws", nil)
	if err != nil {
		t.Fatal(err)
	}
	defer c.CloseNow()

	_, data, err := c.Read(ctx)
	if err != nil {
		t.Fatal(err)
	}
	var st map[string]any
	if err := json.Unmarshal(data, &st); err != nil {
		t.Fatalf("push is not JSON: %v (%q)", err, data)
	}
	if st["sent"] != "1,235K" || st["state"] != "running" || st["running"] != true {
		t.Errorf("push = %v", st)
	}
}

func TestRateLineFramesTheCapAtTwoThirds(t *testing.T) {
	// Peak (50,000) is at the cap, so the top of the frame is cap*1.5 = 75,000 and the cap sits
	// at two thirds height.
	points, area, cap, lastY, ceil := rateLine([]uint64{0, 50_000, 25_000}, 50_000)

	want := "0.00,100.00 50.00,33.33 100.00,66.67"
	if points != want {
		t.Errorf("points %q, want %q", points, want)
	}
	if area != "0,100 "+want+" 100,100" {
		t.Errorf("area %q", area)
	}
	if cap != 66 {
		t.Errorf("cap %d, want 66", cap)
	}
	if lastY != "66.67" {
		t.Errorf("lastY %q, want 66.67", lastY)
	}
	if ceil != 75_000 {
		t.Errorf("ceil %d, want 75000", ceil)
	}
	if p, a, c, l, ce := rateLine(nil, 50_000); p != "" || a != "" || c != 0 || l != "" || ce != 0 {
		t.Errorf("empty history gave %q %q %d %q %d", p, a, c, l, ce)
	}
}

func TestRateLineLiftsTheTopForABurstAboveTheCap(t *testing.T) {
	// Peak 80,000 exceeds the cap, so the top becomes peak*1.02 and the cap sits below it.
	_, _, cap, _, ceil := rateLine([]uint64{40_000, 80_000}, 50_000)
	if ceil != 81_600 {
		t.Errorf("ceil %d, want 81600", ceil)
	}
	if cap != 61 {
		t.Errorf("cap %d, want 61", cap)
	}
}
