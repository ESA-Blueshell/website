// Package server answers POST /convert with one picture's WebP.
package server

import (
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strconv"
	"time"

	"github.com/ESA-Blueshell/website/services/imaging/internal/convert"
)

// Limits bound what one request may cost.
type Limits struct {
	MaxBytes int64
	Picture  convert.Limits
	Wait     time.Duration
}

// server's slots keep decoded bitmaps inside the pod's memory limit.
type server struct {
	limits Limits
	slots  chan struct{}
	log    *slog.Logger
	encode func([]byte, convert.Target, convert.Limits) (convert.Result, error)
}

// New serves GET /healthz and POST /convert.
func New(limits Limits, concurrency int, log *slog.Logger) http.Handler {
	return newServer(limits, concurrency, log, convert.Convert)
}

func newServer(limits Limits, concurrency int, log *slog.Logger, encode func([]byte, convert.Target, convert.Limits) (convert.Result, error)) http.Handler {
	s := &server{limits: limits, slots: make(chan struct{}, concurrency), log: log, encode: encode}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	})
	mux.HandleFunc("POST /convert", s.convert)
	return mux
}

func (s *server) convert(w http.ResponseWriter, r *http.Request) {
	target, err := targetOf(r)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	body, err := io.ReadAll(http.MaxBytesReader(w, r.Body, s.limits.MaxBytes))
	if err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			http.Error(w, "the picture is too large", http.StatusRequestEntityTooLarge)
			return
		}
		http.Error(w, "the picture could not be read", http.StatusBadRequest)
		return
	}

	wait := time.NewTimer(s.limits.Wait)
	defer wait.Stop()
	select {
	case s.slots <- struct{}{}:
		defer func() { <-s.slots }()
	case <-wait.C:
		http.Error(w, "every conversion is busy", http.StatusServiceUnavailable)
		return
	case <-r.Context().Done():
		return
	}

	started := time.Now()
	result, err := s.encode(body, target, s.limits.Picture)
	if err != nil {
		status := http.StatusUnprocessableEntity
		if !errors.Is(err, convert.ErrUndecodable) && !errors.Is(err, convert.ErrTooManyPixels) &&
			!errors.Is(err, convert.ErrTooManyFrames) {
			status = http.StatusInternalServerError
		}
		s.log.Warn("refused a picture", "bytes", len(body), "status", status, "error", err)
		http.Error(w, err.Error(), status)
		return
	}
	if result.Unanimated != nil {
		s.log.Warn("drew an animation as its first frame", "bytes", len(body), "error", result.Unanimated)
	}
	s.log.Info("converted a picture",
		"bytes", len(body), "webp_bytes", len(result.WebP),
		"width", result.Width, "height", result.Height, "frames", result.Frames,
		"millis", time.Since(started).Milliseconds())

	w.Header().Set("Content-Type", "image/webp")
	w.Header().Set("Content-Length", strconv.Itoa(len(result.WebP)))
	w.Header().Set("Image-Width", strconv.Itoa(result.Width))
	w.Header().Set("Image-Height", strconv.Itoa(result.Height))
	_, _ = w.Write(result.WebP)
}

func targetOf(r *http.Request) (convert.Target, error) {
	query := r.URL.Query()
	var target convert.Target
	var err error

	if query.Has("max-edge") {
		if target.MaxEdge, err = edge(query.Get("max-edge")); err != nil {
			return target, fmt.Errorf("max-edge: %w", err)
		}
	} else {
		if target.Width, err = edge(query.Get("width")); err != nil {
			return target, fmt.Errorf("width: %w", err)
		}
		if target.Height, err = edge(query.Get("height")); err != nil {
			return target, fmt.Errorf("height: %w", err)
		}
	}

	target.Lossless = query.Get("lossless") == "true"
	if !target.Lossless {
		quality, err := strconv.Atoi(query.Get("quality"))
		if err != nil || quality < 0 || quality > 100 {
			return target, errors.New("quality: a whole number from 0 to 100, or lossless=true")
		}
		target.Quality = float32(quality)
	} else {
		// cwebp's default effort for -lossless.
		target.Quality = 75
	}
	return target, nil
}

func edge(value string) (int, error) {
	n, err := strconv.Atoi(value)
	if err != nil || n < 1 || n > convert.MaxEdge {
		return 0, fmt.Errorf("a whole number from 1 to %d", convert.MaxEdge)
	}
	return n, nil
}
