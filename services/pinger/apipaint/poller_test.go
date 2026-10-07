package apipaint

import (
	"context"
	"image"
	"image/color"
	"sync"
	"testing"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
)

// fakeSource scripts the descriptor and counts image fetches, so a test can assert the poller
// refetches the image only when the image or its box changes.
type fakeSource struct {
	mu     sync.Mutex
	desc   Descriptor
	descN  int
	imageN int
}

func (f *fakeSource) set(d Descriptor) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.desc = d
}

func (f *fakeSource) Descriptor(context.Context) (Descriptor, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.descN++
	return f.desc, nil
}

func (f *fakeSource) Image(context.Context, string) (image.Image, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.imageN++
	img := image.NewNRGBA(image.Rect(0, 0, 10, 10))
	for y := range 10 {
		for x := range 10 {
			img.SetNRGBA(x, y, color.NRGBA{R: 255, A: 255})
		}
	}
	return img, nil
}

type pixelSink struct {
	mu     sync.Mutex
	pixels []canvas.Pixel
	sets   int
}

func (s *pixelSink) SetPixels(p []canvas.Pixel) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.pixels = p
	s.sets++
}

func (s *pixelSink) snapshot() (int, int) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return len(s.pixels), s.sets
}

func TestPollerPutsThePrefixAndRateIntoSettings(t *testing.T) {
	src := &fakeSource{desc: Descriptor{Prefix: "2001:db8:b317:a000::/64", RatePPS: 256}}
	p := NewPoller(src, 0, &pixelSink{}, nil)

	p.poll(context.Background())

	got := p.Current()
	if got.Prefix.IsZero() {
		t.Fatal("prefix not set")
	}
	if got.RatePPS != 256 {
		t.Fatalf("rate %d, want 256", got.RatePPS)
	}
}

func TestPollerIdlesOnAnEmptyPrefix(t *testing.T) {
	src := &fakeSource{desc: Descriptor{Prefix: "", RatePPS: 128}}
	p := NewPoller(src, 0, &pixelSink{}, nil)

	p.poll(context.Background())

	if !p.Current().Prefix.IsZero() {
		t.Fatal("empty prefix did not idle")
	}
}

func TestPollerPlacesTheImageAndRefetchesOnlyWhenItChanges(t *testing.T) {
	src := &fakeSource{desc: Descriptor{Prefix: "2001:db8::/64", RatePPS: 128, Width: 100, Height: 100, ImageURL: "/files/public/p/a.webp"}}
	sink := &pixelSink{}
	p := NewPoller(src, 0, sink, nil)

	p.poll(context.Background())
	if n, sets := sink.snapshot(); n == 0 || sets != 1 {
		t.Fatalf("after first poll: %d pixels, %d sets", n, sets)
	}

	// A rate-only change must not refetch the image.
	src.set(Descriptor{Prefix: "2001:db8::/64", RatePPS: 200, Width: 100, Height: 100, ImageURL: "/files/public/p/a.webp"})
	p.poll(context.Background())
	if src.imageN != 1 {
		t.Fatalf("image fetched %d times after a rate-only change", src.imageN)
	}

	// Moving the box refetches and replaces the pixels.
	src.set(Descriptor{Prefix: "2001:db8::/64", RatePPS: 200, OriginX: 50, Width: 100, Height: 100, ImageURL: "/files/public/p/a.webp"})
	p.poll(context.Background())
	if src.imageN != 2 {
		t.Fatalf("moving the box did not refetch: %d", src.imageN)
	}
}

func TestPollerClearsThePixelsWhenTheImageGoes(t *testing.T) {
	src := &fakeSource{desc: Descriptor{Prefix: "2001:db8::/64", RatePPS: 128, Width: 100, Height: 100, ImageURL: "/files/public/p/a.webp"}}
	sink := &pixelSink{}
	p := NewPoller(src, 0, sink, nil)
	p.poll(context.Background())

	src.set(Descriptor{Prefix: "2001:db8::/64", RatePPS: 128, Width: 100, Height: 100, ImageURL: ""})
	p.poll(context.Background())

	if n, _ := sink.snapshot(); n != 0 {
		t.Fatalf("pixels not cleared: %d", n)
	}
}

func TestPollerCarriesTheSiteCieToggleIntoSettings(t *testing.T) {
	src := &fakeSource{desc: Descriptor{Prefix: "2001:db8::/64", RatePPS: 128, SiteCieEnabled: true}}
	p := NewPoller(src, 0, &pixelSink{}, nil)

	p.poll(context.Background())
	if !p.Current().Enabled {
		t.Fatal("enabled toggle not carried into settings")
	}

	src.set(Descriptor{Prefix: "2001:db8::/64", RatePPS: 128, SiteCieEnabled: false})
	p.poll(context.Background())
	if p.Current().Enabled {
		t.Fatal("disabled toggle not carried into settings")
	}
}
