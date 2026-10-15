package server

import (
	"bytes"
	"context"
	"errors"
	"image"
	"image/png"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/ESA-Blueshell/website/services/imaging/internal/convert"
)

var quiet = slog.New(slog.NewTextHandler(io.Discard, nil))

var roomy = Limits{
	MaxBytes: 1 << 20,
	Picture:  convert.Limits{MaxPixels: 1_000_000, MaxFrames: 10, MaxAnimationPixels: 10_000_000},
	Wait:     time.Second,
}

func pngBytes(t *testing.T, width, height int) []byte {
	t.Helper()
	var b bytes.Buffer
	if err := png.Encode(&b, image.NewGray(image.Rect(0, 0, width, height))); err != nil {
		t.Fatal(err)
	}
	return b.Bytes()
}

func post(handler http.Handler, query string, body io.Reader) *httptest.ResponseRecorder {
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, httptest.NewRequestWithContext(context.Background(), http.MethodPost, "/convert?"+query, body))
	return recorder
}

func TestHealth(t *testing.T) {
	recorder := httptest.NewRecorder()
	New(roomy, 1, quiet).ServeHTTP(recorder, httptest.NewRequestWithContext(t.Context(), http.MethodGet, "/healthz", nil))
	if recorder.Code != http.StatusNoContent {
		t.Fatalf("answered %d", recorder.Code)
	}
}

func TestMasterAnswersItsBytesAndSize(t *testing.T) {
	recorder := post(New(roomy, 1, quiet), "max-edge=40&quality=82", bytes.NewReader(pngBytes(t, 100, 50)))
	if recorder.Code != http.StatusOK {
		t.Fatalf("answered %d: %s", recorder.Code, recorder.Body)
	}
	if recorder.Header().Get("Content-Type") != "image/webp" ||
		recorder.Header().Get("Image-Width") != "40" || recorder.Header().Get("Image-Height") != "20" {
		t.Fatalf("headers %v", recorder.Header())
	}
	if !bytes.HasPrefix(recorder.Body.Bytes(), []byte("RIFF")) {
		t.Fatal("the body is not a WebP")
	}
}

func TestRenditionIsLosslessWhereAskedFor(t *testing.T) {
	recorder := post(New(roomy, 1, quiet), "width=10&height=5&lossless=true", bytes.NewReader(pngBytes(t, 100, 50)))
	if recorder.Code != http.StatusOK || recorder.Header().Get("Image-Width") != "10" {
		t.Fatalf("answered %d %v", recorder.Code, recorder.Header())
	}
}

func TestRequestTheApiShouldNotSendIsABadRequest(t *testing.T) {
	for _, query := range []string{
		"max-edge=0&quality=82",
		"max-edge=16384&quality=82",
		"width=x&height=5&quality=82",
		"width=10&quality=82",
		"width=10&height=5",
		"width=10&height=5&quality=101",
		"max-edge=40&quality=-1",
	} {
		if recorder := post(New(roomy, 1, quiet), query, strings.NewReader("x")); recorder.Code != http.StatusBadRequest {
			t.Errorf("%s answered %d", query, recorder.Code)
		}
	}
}

func TestBodyOverTheLimitIsTooLarge(t *testing.T) {
	limits := roomy
	limits.MaxBytes = 10
	if recorder := post(New(limits, 1, quiet), "max-edge=40&quality=82", strings.NewReader(strings.Repeat("x", 11))); recorder.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("answered %d", recorder.Code)
	}
}

type failingReader struct{}

func (failingReader) Read([]byte) (int, error) { return 0, errors.New("connection reset") }

func TestBodyThatBreaksOffIsABadRequest(t *testing.T) {
	if recorder := post(New(roomy, 1, quiet), "max-edge=40&quality=82", failingReader{}); recorder.Code != http.StatusBadRequest {
		t.Fatalf("answered %d", recorder.Code)
	}
}

func TestPictureItRefusesIsUnprocessable(t *testing.T) {
	limits := roomy
	limits.Picture.MaxPixels = 100
	handler := New(limits, 1, quiet)
	for name, body := range map[string][]byte{"garbage": []byte("not a picture"), "too many pixels": pngBytes(t, 20, 20)} {
		if recorder := post(handler, "max-edge=40&quality=82", bytes.NewReader(body)); recorder.Code != http.StatusUnprocessableEntity {
			t.Errorf("%s answered %d", name, recorder.Code)
		}
	}
}

func TestAnimationOverTheFrameLimitIsUnprocessable(t *testing.T) {
	tooMany := func([]byte, convert.Target, convert.Limits) (convert.Result, error) {
		return convert.Result{}, convert.ErrTooManyFrames
	}
	recorder := post(newServer(roomy, 1, quiet, tooMany), "max-edge=40&quality=82", strings.NewReader("x"))
	if recorder.Code != http.StatusUnprocessableEntity || !strings.Contains(recorder.Body.String(), "too many frames") {
		t.Fatalf("answered %d: %s", recorder.Code, recorder.Body)
	}
}

func TestAnimationDrawnStillIsServedAndLogged(t *testing.T) {
	var logged bytes.Buffer
	stilled := func([]byte, convert.Target, convert.Limits) (convert.Result, error) {
		return convert.Result{WebP: []byte("RIFF"), Width: 1, Height: 1, Frames: 1, Unanimated: errors.New("frame 2 is broken")}, nil
	}
	recorder := post(newServer(roomy, 1, slog.New(slog.NewTextHandler(&logged, nil)), stilled), "max-edge=40&quality=82", strings.NewReader("x"))
	if recorder.Code != http.StatusOK || !strings.Contains(logged.String(), "frame 2 is broken") {
		t.Fatalf("answered %d, logged %s", recorder.Code, logged.String())
	}
}

func TestEncoderFailureIsTheServicesOwn(t *testing.T) {
	failing := func([]byte, convert.Target, convert.Limits) (convert.Result, error) {
		return convert.Result{}, errors.New("libwebp refused the picture")
	}
	if recorder := post(newServer(roomy, 1, quiet, failing), "max-edge=40&quality=82", strings.NewReader("x")); recorder.Code != http.StatusInternalServerError {
		t.Fatalf("answered %d", recorder.Code)
	}
}

func blocking(started, release chan struct{}) func([]byte, convert.Target, convert.Limits) (convert.Result, error) {
	return func([]byte, convert.Target, convert.Limits) (convert.Result, error) {
		close(started)
		<-release
		return convert.Result{WebP: []byte("RIFF"), Width: 1, Height: 1}, nil
	}
}

func TestRequestWithNoConversionFreeIsTurnedAway(t *testing.T) {
	started, release := make(chan struct{}), make(chan struct{})
	limits := roomy
	limits.Wait = 10 * time.Millisecond
	handler := newServer(limits, 1, quiet, blocking(started, release))
	go post(handler, "max-edge=40&quality=82", strings.NewReader("x"))
	<-started
	defer close(release)

	if recorder := post(handler, "max-edge=40&quality=82", strings.NewReader("x")); recorder.Code != http.StatusServiceUnavailable {
		t.Fatalf("answered %d", recorder.Code)
	}
}

func TestRequestThatGivesUpWaitingIsLeftAlone(t *testing.T) {
	started, release := make(chan struct{}), make(chan struct{})
	handler := newServer(roomy, 1, quiet, blocking(started, release))
	go post(handler, "max-edge=40&quality=82", strings.NewReader("x"))
	<-started
	defer close(release)

	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, httptest.NewRequestWithContext(ctx, http.MethodPost, "/convert?max-edge=40&quality=82", strings.NewReader("x")))
	if recorder.Body.Len() != 0 {
		t.Fatalf("answered %d: %s", recorder.Code, recorder.Body)
	}
}
