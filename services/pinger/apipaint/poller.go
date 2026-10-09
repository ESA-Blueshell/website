package apipaint

import (
	"context"
	"errors"
	"image"
	"image/color"
	"log/slog"
	"sync"
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

// OffsetSink takes how far each moving placement sits from where it was placed. *paint.Sender is one.
type OffsetSink interface {
	SetOffsets([]paint.Offset)
}

// Poller follows the api descriptor and pushes changes outward: the prefix and rate become the
// sender's settings, and a new image or size is placed and handed to the sink. A placement that only
// moves keeps its pixels: the poller hands the offset sink where its box is now, every moveEvery,
// so the sender paints it there without restarting its pass. It holds the last descriptor it acted
// on, and refetches an image only when the image or its size changes.
type Poller struct {
	src      Source
	interval time.Duration
	sink     PixelSink
	onImage  func(placed canvas.Placement)
	offsets  OffsetSink
	now      func() time.Time

	backoffMin, backoffMax time.Duration
	// streamRetry is how long an api without the stream is polled before the stream is tried
	// again, so a pinger started before the api deploys it picks it up.
	streamRetry time.Duration
	moveEvery   time.Duration

	settings atomic.Pointer[paint.Settings]
	clock    serverClock
	boxes    atomic.Pointer[[]placedBox]

	// mu serialises applying a descriptor, which the stream and the retry both do.
	mu      sync.Mutex
	last    Descriptor
	seen    bool
	placed  []placedBox
	pending *arrival

	moveMu      sync.Mutex
	lastOffsets []paint.Offset
}

// placedBox is a placement and the box its pixels were placed in, which a later move leaves behind.
type placedBox struct {
	Placement
	placedX, placedY int
	pixels           []canvas.Pixel
}

type arrival struct {
	d  Descriptor
	at time.Time
}

// NewPoller wires a poller. onImage is called with the freshly placed image whenever it changes, so
// the page can show a preview; it may be nil.
func NewPoller(src Source, interval time.Duration, sink PixelSink, onImage func(canvas.Placement)) *Poller {
	p := &Poller{
		src: src, interval: interval, sink: sink, onImage: onImage, now: time.Now,
		backoffMin: 500 * time.Millisecond, backoffMax: 5 * time.Second,
		streamRetry: time.Minute, moveEvery: 25 * time.Millisecond,
	}
	p.settings.Store(&paint.Settings{})
	return p
}

// SetOffsetSink makes the poller move placements by handing o their offsets; without one every
// placement is painted where it was placed. Call it before Run.
func (p *Poller) SetOffsetSink(o OffsetSink) { p.offsets = o }

// Current is the latest settings the poller could read. Until the first read it is the zero
// Settings, which has no prefix, so the sender stays idle.
func (p *Poller) Current() paint.Settings { return *p.settings.Load() }

// Run follows the paint stream when the source has one, and polls every interval otherwise or while
// the api serves none.
func (p *Poller) Run(ctx context.Context) {
	if p.offsets != nil {
		go p.moveLoop(ctx)
	}
	st, ok := p.src.(Streamer)
	if !ok {
		p.pollFor(ctx, 0)
		return
	}
	go p.retryLoop(ctx)
	backoff := p.backoffMin
	for ctx.Err() == nil {
		err := st.Stream(ctx, func() { backoff = p.backoffMin }, func(d Descriptor, at time.Time) { p.apply(ctx, d, at) })
		switch {
		case ctx.Err() != nil:
			return
		case errors.Is(err, ErrStreamMissing):
			p.pollFor(ctx, p.streamRetry)
			continue
		default:
			slog.Warn("paint stream", "err", err)
			// A read while the stream is down keeps a change made meanwhile from waiting on it.
			p.poll(ctx)
		}
		select {
		case <-ctx.Done():
		case <-time.After(backoff):
		}
		backoff = min(backoff*2, p.backoffMax)
	}
}

// pollFor polls every interval for d, or until ctx ends when d is zero.
func (p *Poller) pollFor(ctx context.Context, d time.Duration) {
	var until <-chan time.Time
	if d > 0 {
		until = time.After(d)
	}
	tick := time.NewTicker(p.interval)
	defer tick.Stop()
	for {
		p.poll(ctx)
		select {
		case <-ctx.Done():
			return
		case <-until:
			return
		case <-tick.C:
		}
	}
}

// retryLoop re-applies a streamed descriptor whose images failed to load: the stream sends only
// changes, so nothing else would bring it back.
func (p *Poller) retryLoop(ctx context.Context) {
	tick := time.NewTicker(p.interval)
	defer tick.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
		}
		p.mu.Lock()
		if a := p.pending; a != nil {
			p.applyLocked(ctx, a.d, a.at)
		}
		p.mu.Unlock()
	}
}

func (p *Poller) moveLoop(ctx context.Context) {
	tick := time.NewTicker(p.moveEvery)
	defer tick.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
			p.move()
		}
	}
}

func (p *Poller) poll(ctx context.Context) {
	d, err := p.src.Descriptor(ctx)
	if err != nil {
		slog.Warn("read paint descriptor", "err", err)
		return
	}
	p.apply(ctx, d, p.now())
}

// apply acts on a descriptor that arrived at local time at.
func (p *Poller) apply(ctx context.Context, d Descriptor, at time.Time) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.applyLocked(ctx, d, at)
}

func (p *Poller) applyLocked(ctx context.Context, d Descriptor, at time.Time) {
	p.clock.observe(d.ServerTime, at)
	p.settings.Store(&paint.Settings{Prefix: parsePrefix(d.Prefix), RatePPS: d.RatePPS, Enabled: d.SiteCieEnabled})
	p.pending = nil
	if p.seen && !placementsChanged(p.last, d) {
		p.last = d
		return
	}
	if !p.place(ctx, d.Placements) {
		p.pending = &arrival{d: d, at: at}
		return
	}
	p.last, p.seen = d, true
}

// place reuses the pixels of every placement whose image and size are unchanged, and fetches and
// places the rest. A placement whose image cannot be fetched aborts it, so the change is retried
// rather than painting a partial canvas. The sink is handed new pixels only when they changed.
func (p *Poller) place(ctx context.Context, placements []Placement) bool {
	next := make([]placedBox, len(placements))
	used := make([]bool, len(p.placed))
	same := p.seen && len(placements) == len(p.placed)
	for i, pl := range placements {
		if j := reusable(p.placed, used, pl); j >= 0 {
			used[j] = true
			next[i] = p.placed[j]
			next[i].Placement = pl
			same = same && j == i
			continue
		}
		img, err := p.src.Image(ctx, pl.ImageURL)
		if err != nil {
			slog.Warn("fetch paint image", "url", pl.ImageURL, "err", err)
			return false
		}
		placed := canvas.PlaceInBox(img, pl.OriginX, pl.OriginY, pl.Width, pl.Height)
		next[i] = placedBox{Placement: pl, placedX: pl.OriginX, placedY: pl.OriginY, pixels: placed.Pixels}
		same = false
	}
	p.placed = next
	p.boxes.Store(&next)
	p.move()
	if same {
		return true
	}
	pixels := union(next)
	p.sink.SetPixels(pixels)
	if p.onImage != nil {
		if pixels == nil {
			p.onImage(canvas.Placement{})
		} else {
			p.onImage(canvas.Placement{Image: compositePreview(pixels), Pixels: pixels})
		}
	}
	return true
}

// reusable finds an unused placed box with pl's image and size, or -1.
func reusable(placed []placedBox, used []bool, pl Placement) int {
	for j, b := range placed {
		if !used[j] && b.ImageURL == pl.ImageURL && b.Width == pl.Width && b.Height == pl.Height {
			return j
		}
	}
	return -1
}

// union joins every box's pixels, each stamped with its placement's index, and points each box at
// its own run of the result so the next change copies from there.
func union(boxes []placedBox) []canvas.Pixel {
	total := 0
	for _, b := range boxes {
		total += len(b.pixels)
	}
	if total == 0 {
		return nil
	}
	out := make([]canvas.Pixel, 0, total)
	for i := range boxes {
		from := len(out)
		for _, px := range boxes[i].pixels {
			px.Placement = uint16(i)
			out = append(out, px)
		}
		boxes[i].pixels = out[from:len(out):len(out)]
	}
	return out
}

// move hands the offset sink where each box is now against where it was placed, when that changed.
func (p *Poller) move() {
	if p.offsets == nil {
		return
	}
	boxes := p.boxes.Load()
	if boxes == nil {
		return
	}
	now := p.clock.now(p.now())
	p.moveMu.Lock()
	defer p.moveMu.Unlock()
	offs := make([]paint.Offset, len(*boxes))
	changed := len(offs) != len(p.lastOffsets)
	for i, b := range *boxes {
		x, y := b.PositionAt(now)
		offs[i] = paint.Offset{X: int32(x - b.placedX), Y: int32(y - b.placedY)}
		changed = changed || offs[i] != p.lastOffsets[i]
	}
	if changed {
		p.lastOffsets = offs
		p.offsets.SetOffsets(offs)
	}
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
