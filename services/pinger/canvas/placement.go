package canvas

import (
	"image"
	"image/color"

	"golang.org/x/image/draw"
)

// minAlpha is the faintest pixel worth a ping: an antialiased edge fainter than this barely tints
// the canvas, and a ping spent on it is one the rest of the logo goes without.
const minAlpha = 16

// Placement is the logo as it lands on the canvas: the scaled image and every pixel worth sending.
type Placement struct {
	Image  *image.NRGBA
	Pixels []Pixel
}

// PlaceInBox fits src inside a box of boxW by boxH, preserving the image's aspect, and centers it
// in the box. This is the admin's drag-and-resize box from the api descriptor: the image fills as
// much of the box as its own shape allows, and is centered in whichever axis has room to spare.
func PlaceInBox(src image.Image, originX, originY, boxW, boxH int) Placement {
	crop := visibleBounds(src)
	w, h := crop.Dx(), crop.Dy()
	if boxW < 1 || boxH < 1 || w < 1 || h < 1 {
		return Placement{Image: image.NewNRGBA(image.Rect(0, 0, 1, 1))}
	}
	// Scale so neither side overflows the box, then set the long side to what that scale gives.
	scale := min(float64(boxW)/float64(w), float64(boxH)/float64(h))
	maxSide := max(1, int(scale*float64(max(w, h))+0.5))
	center := image.Pt(originX+boxW/2, originY+boxH/2)
	return Place(src, maxSide, center)
}

// Place crops src to its visible pixels, fits the long side to maxSide and centers it on the
// given canvas point. Pixels fainter than minAlpha are dropped; the crop still counts them, so the
// logo keeps its size.
func Place(src image.Image, maxSide int, center image.Point) Placement {
	crop := visibleBounds(src)
	w, h := crop.Dx(), crop.Dy()
	if w >= h {
		w, h = maxSide, max(1, (h*maxSide+w/2)/w)
	} else {
		w, h = max(1, (w*maxSide+h/2)/h), maxSide
	}

	scaled := image.NewNRGBA(image.Rect(0, 0, w, h))
	if crop.Size() == scaled.Rect.Size() {
		draw.Copy(scaled, image.Point{}, src, crop, draw.Src, nil)
	} else {
		draw.CatmullRom.Scale(scaled, scaled.Rect, src, crop, draw.Src, nil)
	}

	origin := image.Pt(center.X-w/2, center.Y-h/2)
	pixels := make([]Pixel, 0, w*h)
	for y := range h {
		for x := range w {
			c := scaled.NRGBAAt(x, y)
			if c.A < minAlpha {
				continue
			}
			pixels = append(pixels, Pixel{
				X: uint16(origin.X + x), Y: uint16(origin.Y + y),
				R: c.R, G: c.G, B: c.B, A: c.A,
			})
		}
	}
	return Placement{Image: scaled, Pixels: pixels}
}

func visibleBounds(src image.Image) image.Rectangle {
	b := src.Bounds()
	visible := image.Rectangle{}
	for y := b.Min.Y; y < b.Max.Y; y++ {
		for x := b.Min.X; x < b.Max.X; x++ {
			if color.NRGBAModel.Convert(src.At(x, y)).(color.NRGBA).A != 0 {
				visible = visible.Union(image.Rect(x, y, x+1, y+1))
			}
		}
	}
	if visible.Empty() {
		return b
	}
	return visible
}
