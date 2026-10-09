package apipaint

import (
	"bytes"
	"context"
	"image"
	"image/png"
	"net/http"
	"net/http/httptest"
	"testing"
)

// requireHTTPS answers like the production api: a request that does not say it arrived over HTTPS
// gets a 500, which is what the redirect filter does to a plain in-cluster call on :8080.
func requireHTTPS(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-Forwarded-Proto") != "https" {
			w.WriteHeader(http.StatusInternalServerError)
			return
		}
		next(w, r)
	}
}

func TestDescriptorIsReadFromAnApiThatRequiresHTTPS(t *testing.T) {
	srv := httptest.NewServer(requireHTTPS(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte(`{"prefix":"2001:db8::/64","ratePps":1024,"siteCieEnabled":true,"placements":[]}`))
	}))
	defer srv.Close()

	d, err := NewClient(srv.URL).Descriptor(context.Background())
	if err != nil {
		t.Fatalf("descriptor: %v", err)
	}
	if !d.SiteCieEnabled || d.RatePPS != 1024 {
		t.Fatalf("descriptor %+v", d)
	}
}

func TestImageIsReadFromAnApiThatRequiresHTTPS(t *testing.T) {
	var buf bytes.Buffer
	if err := png.Encode(&buf, image.NewRGBA(image.Rect(0, 0, 2, 2))); err != nil {
		t.Fatal(err)
	}
	srv := httptest.NewServer(requireHTTPS(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write(buf.Bytes())
	}))
	defer srv.Close()

	img, err := NewClient(srv.URL).Image(context.Background(), "/files/public/pinger-paint/logo.png")
	if err != nil {
		t.Fatalf("image: %v", err)
	}
	if img.Bounds().Dx() != 2 {
		t.Fatalf("bounds %v", img.Bounds())
	}
}
