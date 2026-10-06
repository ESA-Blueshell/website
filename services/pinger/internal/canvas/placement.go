package canvas

import (
	"image"
	"image/color"

	"golang.org/x/image/draw"
)

// Placement is the logo as it lands on the canvas: the scaled image and every pixel worth sending.
type Placement struct {
	Image  *image.NRGBA
	Pixels []Pixel
}

// Place crops src to its visible pixels, fits the long side to maxSide and anchors it in the
// canvas's bottom-right corner. Fully transparent pixels are dropped: painting them changes nothing.
func Place(src image.Image, maxSide int) Placement {
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

	origin := image.Pt(Width-w, Height-h)
	pixels := make([]Pixel, 0, w*h)
	for y := range h {
		for x := range w {
			c := scaled.NRGBAAt(x, y)
			if c.A == 0 {
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
