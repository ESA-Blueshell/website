package apipaint

import (
	"context"
	"image"
	"image/color"
	"log/slog"
	"sync/atomic"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

// previewScale shrinks the composite preview the watch page draws, so several full-canvas placements
// do not add up to a huge image. The sender still gets every pixel at full resolution.
const previewScale = 4

// PixelSink takes the placed image. *paint.Sender is one.
type PixelSink interface {
	SetPixels([]canvas.Pixel)
}

// Poller reads the api descriptor on a tick and pushes changes outward: the prefix and rate become
// the sender's settings, and a new image or box is placed and handed to the sink. It holds the last
// descriptor it acted on, so it refetches the image only when the image or its box changes.
type Poller struct {
	src      Source
	interval time.Duration
	sink     PixelSink
	onImage  func(placed canvas.Placement)

	settings atomic.Pointer[paint.Settings]
	last     Descriptor
	seen     bool
}

// NewPoller wires a poller. onImage is called with the freshly placed image whenever it changes, so
// the page can show a preview; it may be nil.
func NewPoller(src Source, interval time.Duration, sink PixelSink, onImage func(canvas.Placement)) *Poller {
	p := &Poller{src: src, interval: interval, sink: sink, onImage: onImage}
	p.settings.Store(&paint.Settings{})
	return p
}

// Current is the latest settings the poller could read. Until the first read it is the zero
// Settings, which has no prefix, so the sender stays idle.
func (p *Poller) Current() paint.Settings { return *p.settings.Load() }

func (p *Poller) Run(ctx context.Context) {
	tick := time.NewTicker(p.interval)
	defer tick.Stop()
	for {
		p.poll(ctx)
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
		}
	}
}

func (p *Poller) poll(ctx context.Context) {
	d, err := p.src.Descriptor(ctx)
	if err != nil {
		slog.Warn("read paint descriptor", "err", err)
		return
	}
	p.settings.Store(&paint.Settings{Prefix: parsePrefix(d.Prefix), RatePPS: d.RatePPS, Enabled: d.SiteCieEnabled})

	if p.seen && !placementsChanged(p.last, d) {
		p.last = d
		return
	}
	if len(d.Placements) == 0 {
		p.sink.SetPixels(nil)
		if p.onImage != nil {
			p.onImage(canvas.Placement{})
		}
		p.last, p.seen = d, true
		return
	}
	// Fetch every placement's image and place each into its own box, then union all the pixels. A
	// placement whose image cannot be fetched aborts this pass so the change is retried next tick,
	// rather than painting a partial canvas.
	var pixels []canvas.Pixel
	for _, placement := range d.Placements {
		img, err := p.src.Image(ctx, placement.ImageURL)
		if err != nil {
			slog.Warn("fetch paint image", "url", placement.ImageURL, "err", err)
			return
		}
		placed := canvas.PlaceInBox(img, placement.OriginX, placement.OriginY, placement.Width, placement.Height)
		pixels = append(pixels, placed.Pixels...)
	}
	p.sink.SetPixels(pixels)
	if p.onImage != nil {
		p.onImage(canvas.Placement{Image: compositePreview(pixels), Pixels: pixels})
	}
	p.last, p.seen = d, true
}

// compositePreview renders every painted pixel onto one small image of the whole canvas, so the
// watch page can show all the placements at once without holding a full-resolution copy.
func compositePreview(pixels []canvas.Pixel) *image.NRGBA {
	img := image.NewNRGBA(image.Rect(0, 0, canvas.Width/previewScale, canvas.Height/previewScale))
	for _, px := range pixels {
		img.SetNRGBA(int(px.X)/previewScale, int(px.Y)/previewScale, colorOf(px))
	}
	return img
}

// parsePrefix turns the descriptor's prefix into a canvas prefix, or the zero prefix when it is
// empty or unparseable — either way the sender idles rather than painting to the wrong place.
func parsePrefix(s string) canvas.Prefix {
	if s == "" {
		return canvas.Prefix{}
	}
	p, err := canvas.ParsePrefix(s)
	if err != nil {
		slog.Warn("parse prefix", "prefix", s, "err", err)
		return canvas.Prefix{}
	}
	return p
}

// placementsChanged reports whether the images to paint, or any of their boxes, differ between two
// descriptors, which is when the pixels must be rebuilt.
func placementsChanged(a, b Descriptor) bool {
	if len(a.Placements) != len(b.Placements) {
		return true
	}
	for i := range a.Placements {
		if a.Placements[i] != b.Placements[i] {
			return true
		}
	}
	return false
}

func colorOf(px canvas.Pixel) color.NRGBA {
	return color.NRGBA{R: px.R, G: px.G, B: px.B, A: px.A}
}
