package server

import (
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"time"
)

// FromEnv is the HTTP server the settings in getenv describe, each with a default that suits a
// pod of 1 GiB.
func FromEnv(getenv func(string) string, log *slog.Logger) (*http.Server, error) {
	var limits Limits
	var concurrency int
	for _, s := range []struct {
		name     string
		fallback int
		into     func(int)
	}{
		{"IMAGING_MAX_BYTES", 20 << 20, func(n int) { limits.MaxBytes = int64(n) }},
		{"IMAGING_MAX_PIXELS", 50_000_000, func(n int) { limits.Picture.MaxPixels = int64(n) }},
		{"IMAGING_MAX_FRAMES", 1000, func(n int) { limits.Picture.MaxFrames = n }},
		{"IMAGING_MAX_ANIMATION_PIXELS", 1_000_000_000, func(n int) { limits.Picture.MaxAnimationPixels = int64(n) }},
		{"IMAGING_WAIT_SECONDS", 30, func(n int) { limits.Wait = time.Duration(n) * time.Second }},
		{"IMAGING_CONCURRENCY", 1, func(n int) { concurrency = n }},
	} {
		n := s.fallback
		if value := getenv(s.name); value != "" {
			parsed, err := strconv.Atoi(value)
			if err != nil || parsed < 1 {
				return nil, fmt.Errorf("%s is %q, not a positive whole number", s.name, value)
			}
			n = parsed
		}
		s.into(n)
	}

	listen := getenv("IMAGING_LISTEN")
	if listen == "" {
		listen = ":8080"
	}
	log.Info("configured", "address", listen, "max_bytes", limits.MaxBytes, "max_pixels", limits.Picture.MaxPixels,
		"max_frames", limits.Picture.MaxFrames, "max_animation_pixels", limits.Picture.MaxAnimationPixels,
		"wait", limits.Wait, "concurrency", concurrency)

	// The read timeout covers the largest body over a slow link, and the write timeout a request
	// that waited its turn and then converted the largest picture.
	return &http.Server{
		Addr:              listen,
		Handler:           New(limits, concurrency, log),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      limits.Wait + 60*time.Second,
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    16 << 10,
	}, nil
}
