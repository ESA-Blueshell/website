package web

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/coder/websocket"

	"github.com/ESA-Blueshell/website/services/pinger/internal/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/internal/paint"
)

type fakeStats paint.Stats

func (f fakeStats) Snapshot() paint.Stats { return paint.Stats(f) }

type fakeSettings struct {
	current paint.Settings
	saves   int
}

func (f *fakeSettings) Current() paint.Settings { return f.current }
func (f *fakeSettings) Save(_ context.Context, v paint.Settings) error {
	f.current = v
	f.saves++
	return nil
}

type fakeAuth bool

func (f fakeAuth) IsAdmin(*http.Request) (bool, error) { return bool(f), nil }

func newTestServer(admin bool, settings *fakeSettings) http.Handler {
	stats := fakeStats{State: paint.Running, Sent: 1_234_567, Errors: 3, Passes: 6, PassDone: 50_000, PassTotal: 202_158, ActualPPS: 49_876, LastError: "no buffer space available"}
	return NewServer(stats, settings, fakeAuth(admin), []byte("png"))
}

func get(t *testing.T, h http.Handler, path string, groups string) (int, string) {
	t.Helper()
	r := httptest.NewRequest(http.MethodGet, path, nil)
	if groups != "" {
		r.Header.Set("X-User-Groups", groups)
	}
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	body, _ := io.ReadAll(w.Result().Body)
	return w.Code, string(body)
}

func post(h http.Handler, form url.Values) *httptest.ResponseRecorder {
	r := httptest.NewRequest(http.MethodPost, "/settings", strings.NewReader(form.Encode()))
	r.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	return w
}

func TestThePageShowsHowThePaintingGoes(t *testing.T) {
	p, _ := canvas.ParsePrefix("2001:db8:b317:a000::/64")
	h := newTestServer(false, &fakeSettings{current: paint.Settings{Prefix: p, RatePPS: 50_000}})

	code, body := get(t, h, "/", "MEMBER,GUEST")

	if code != http.StatusOK {
		t.Fatalf("status %d", code)
	}
	for _, want := range []string{"running", "1.2M", "49,876", "50,000", "2001:db8:b317:a000::/64", "24%", "no buffer space available", `/ws`} {
		if !strings.Contains(body, want) {
			t.Errorf("page lacks %q", want)
		}
	}
	if strings.Contains(body, `action="/settings"`) {
		t.Error("a member sees the settings form")
	}
}

func TestAnAdminSeesTheSettingsForm(t *testing.T) {
	h := newTestServer(true, &fakeSettings{current: paint.Settings{RatePPS: 50_000}})

	_, body := get(t, h, "/", "ADMIN,MEMBER")

	if !strings.Contains(body, `action="/settings"`) {
		t.Fatal("an admin does not see the settings form")
	}
}

func TestTheStatsFragmentStandsAlone(t *testing.T) {
	h := newTestServer(false, &fakeSettings{current: paint.Settings{RatePPS: 50_000}})

	code, body := get(t, h, "/stats", "")

	if code != http.StatusOK || strings.Contains(body, "<html") || !strings.Contains(body, "1.2M") {
		t.Fatalf("status %d, body %q", code, body)
	}
}

func TestAnAdminChangesTheSettings(t *testing.T) {
	settings := &fakeSettings{current: paint.Settings{RatePPS: 50_000}}
	h := newTestServer(true, settings)

	w := post(h, url.Values{"prefix": {"2001:db8:b317:a000::/64"}, "rate": {"20000"}, "paused": {"on"}})

	if w.Code != http.StatusSeeOther {
		t.Fatalf("status %d: %s", w.Code, w.Body)
	}
	p, _ := canvas.ParsePrefix("2001:db8:b317:a000::/64")
	want := paint.Settings{Prefix: p, RatePPS: 20_000, Paused: true}
	if settings.current != want {
		t.Fatalf("saved %+v, want %+v", settings.current, want)
	}
}

func TestAnAdminCanClearThePrefix(t *testing.T) {
	p, _ := canvas.ParsePrefix("2001:db8:b317:a000::/64")
	settings := &fakeSettings{current: paint.Settings{Prefix: p, RatePPS: 50_000}}
	h := newTestServer(true, settings)

	post(h, url.Values{"prefix": {""}, "rate": {"50000"}})

	if !settings.current.Prefix.IsZero() {
		t.Fatalf("prefix still %s", settings.current.Prefix)
	}
}

func TestOnlyAnAdminChangesTheSettings(t *testing.T) {
	settings := &fakeSettings{current: paint.Settings{RatePPS: 50_000}}
	h := newTestServer(false, settings)

	w := post(h, url.Values{"prefix": {"2001:db8::"}, "rate": {"1"}})

	if w.Code != http.StatusForbidden || settings.saves != 0 {
		t.Fatalf("status %d, %d saves", w.Code, settings.saves)
	}
}

func TestSettingsThatCannotWorkAreRefused(t *testing.T) {
	for name, form := range map[string]url.Values{
		"bad prefix":    {"prefix": {"2001:db8::/48"}, "rate": {"1000"}},
		"zero rate":     {"prefix": {""}, "rate": {"0"}},
		"rate over cap": {"prefix": {""}, "rate": {"200001"}},
		"no rate":       {"prefix": {""}},
	} {
		t.Run(name, func(t *testing.T) {
			settings := &fakeSettings{current: paint.Settings{RatePPS: 50_000}}

			w := post(newTestServer(true, settings), form)

			if w.Code != http.StatusBadRequest || settings.saves != 0 {
				t.Fatalf("status %d, %d saves", w.Code, settings.saves)
			}
		})
	}
}

func TestThePreviewIsThePlacedLogo(t *testing.T) {
	h := newTestServer(false, &fakeSettings{})

	code, body := get(t, h, "/preview.png", "")

	if code != http.StatusOK || body != "png" {
		t.Fatalf("status %d, body %q", code, body)
	}
}

func TestACrossSitePostIsRefusedEvenForAnAdmin(t *testing.T) {
	settings := &fakeSettings{current: paint.Settings{RatePPS: 50_000}}
	h := newTestServer(true, settings)
	r := httptest.NewRequest(http.MethodPost, "/settings", strings.NewReader("prefix=&rate=1"))
	r.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	r.Header.Set("Sec-Fetch-Site", "cross-site")
	w := httptest.NewRecorder()

	h.ServeHTTP(w, r)

	if w.Code != http.StatusForbidden || settings.saves != 0 {
		t.Fatalf("status %d, %d saves", w.Code, settings.saves)
	}
}

func TestCompactShortensBigTalliesAndKeepsSmallOnesExact(t *testing.T) {
	cases := map[any]string{
		uint64(0): "0", 42: "42", uint64(999): "999",
		uint64(1_000): "1K", 50_000: "50K", uint64(202_158): "202.2K",
		uint64(1_234_567): "1.2M", uint64(18_710_842): "18.7M", uint64(3_400_000_000): "3.4B",
		uint64(2_000_000_000_000): "2T",
	}
	for in, want := range cases {
		if got := compact(in); got != want {
			t.Errorf("compact(%v) = %q, want %q", in, got, want)
		}
	}
}

func TestTheSocketPushesTheLiveRegion(t *testing.T) {
	p, _ := canvas.ParsePrefix("2001:db8:b317:a000::/64")
	srv := httptest.NewServer(newTestServer(false, &fakeSettings{current: paint.Settings{Prefix: p, RatePPS: 50_000}}))
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
	body := string(data)
	for _, want := range []string{`id="live"`, "1.2M", "data-live-meter"} {
		if !strings.Contains(body, want) {
			t.Errorf("push lacks %q", want)
		}
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
