package apipaint

import (
	"context"
	"log/slog"
	"sync/atomic"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
	"github.com/ESA-Blueshell/website/services/pinger/paint"
)

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
	if p.seen && d == p.last {
		return
	}
	p.settings.Store(&paint.Settings{Prefix: parsePrefix(d.Prefix), RatePPS: d.RatePPS})

	if p.seen && !imageChanged(p.last, d) {
		p.last = d
		return
	}
	if d.ImageURL == "" {
		p.sink.SetPixels(nil)
		if p.onImage != nil {
			p.onImage(canvas.Placement{})
		}
		p.last, p.seen = d, true
		return
	}
	img, err := p.src.Image(ctx, d.ImageURL)
	if err != nil {
		// Keep the current pixels and retry next tick: last is left untouched, so the change is
		// not marked handled.
		slog.Warn("fetch paint image", "err", err)
		return
	}
	placed := canvas.PlaceInBox(img, d.OriginX, d.OriginY, d.Width, d.Height)
	p.sink.SetPixels(placed.Pixels)
	if p.onImage != nil {
		p.onImage(placed)
	}
	p.last, p.seen = d, true
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

func imageChanged(a, b Descriptor) bool {
	return a.ImageURL != b.ImageURL || a.OriginX != b.OriginX || a.OriginY != b.OriginY ||
		a.Width != b.Width || a.Height != b.Height
}
