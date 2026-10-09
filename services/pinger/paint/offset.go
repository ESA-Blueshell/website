package paint

import "github.com/ESA-Blueshell/website/services/pinger/canvas"

// Offset is how far a moving placement's pixels sit from where they were placed.
type Offset struct{ X, Y int32 }

// offsets holds one Offset per placement, indexed by canvas.Pixel.Placement. A pixel whose
// placement has no entry stays where it was placed.
type offsets struct{ by []Offset }

// shift moves px by its placement's offset, and reports false when that lands it off the canvas.
func (o *offsets) shift(px canvas.Pixel) (canvas.Pixel, bool) {
	if int(px.Placement) >= len(o.by) {
		return px, true
	}
	d := o.by[px.Placement]
	x, y := int32(px.X)+d.X, int32(px.Y)+d.Y
	if x < 0 || x >= canvas.Width || y < 0 || y >= canvas.Height {
		return px, false
	}
	px.X, px.Y = uint16(x), uint16(y)
	return px, true
}

// SetOffsets moves each placement's pixels by its offset from the next chunk on, without
// restarting the pass. Safe to call while the sender runs; the sender keeps the slice it is given.
// All-zero offsets paint every pixel where it was placed.
func (s *Sender) SetOffsets(by []Offset) {
	for _, d := range by {
		if d != (Offset{}) {
			s.offsets.Store(&offsets{by: by})
			return
		}
	}
	s.offsets.Store(nil)
}
