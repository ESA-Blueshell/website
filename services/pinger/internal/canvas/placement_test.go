package canvas

import (
	"image"
	"image/color"
	"image/png"
	"os"
	"testing"
)

func transparent(w, h int) *image.NRGBA {
	return image.NewNRGBA(image.Rect(0, 0, w, h))
}

func fill(img *image.NRGBA, r image.Rectangle, c color.NRGBA) {
	for y := r.Min.Y; y < r.Max.Y; y++ {
		for x := r.Min.X; x < r.Max.X; x++ {
			img.SetNRGBA(x, y, c)
		}
	}
}

func TestPlaceCropsToTheVisiblePixelsAndFitsTheLongSide(t *testing.T) {
	src := transparent(100, 100)
	red := color.NRGBA{R: 0xff, A: 0xff}
	fill(src, image.Rect(40, 40, 60, 50), red)

	got := Place(src, 600)

	if got.Image.Bounds().Dx() != 600 || got.Image.Bounds().Dy() != 300 {
		t.Fatalf("scaled to %v, want 600x300", got.Image.Bounds().Size())
	}
	if len(got.Pixels) != 600*300 {
		t.Fatalf("got %d pixels, want %d", len(got.Pixels), 600*300)
	}
	for _, px := range got.Pixels {
		if px.X < 3240 || px.X > 3839 || px.Y < 1860 || px.Y > 2159 {
			t.Fatalf("pixel %+v outside the bottom-right 600x300", px)
		}
		if px.R != 0xff || px.G != 0 || px.B != 0 || px.A != 0xff {
			t.Fatalf("pixel %+v is not opaque red", px)
		}
	}
}

func TestPlaceSkipsTransparentPixelsAndKeepsEdgeAlpha(t *testing.T) {
	src := transparent(600, 2)
	fill(src, image.Rect(0, 0, 600, 2), color.NRGBA{G: 0xff, A: 0xff})
	src.SetNRGBA(10, 0, color.NRGBA{})
	src.SetNRGBA(11, 0, color.NRGBA{R: 0x40, G: 0x80, B: 0xc0, A: 0x80})

	got := Place(src, 600)

	if len(got.Pixels) != 1199 {
		t.Fatalf("got %d pixels, want 1199", len(got.Pixels))
	}
	var edge *Pixel
	for i, px := range got.Pixels {
		if px.X == 3240+10 && px.Y == 2158 {
			t.Fatalf("transparent pixel was kept: %+v", px)
		}
		if px.X == 3240+11 && px.Y == 2158 {
			edge = &got.Pixels[i]
		}
	}
	want := Pixel{X: 3251, Y: 2158, R: 0x40, G: 0x80, B: 0xc0, A: 0x80}
	if edge == nil || *edge != want {
		t.Fatalf("edge pixel %+v, want %+v", edge, want)
	}
}

func TestPlaceFitsTheBlueshellLogoTo600By480InTheCorner(t *testing.T) {
	f, err := os.Open("../../logo.png")
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	src, err := png.Decode(f)
	if err != nil {
		t.Fatal(err)
	}

	got := Place(src, 600)

	if got.Image.Bounds().Size() != image.Pt(600, 480) {
		t.Fatalf("scaled to %v, want 600x480", got.Image.Bounds().Size())
	}
	minX, minY, maxX, maxY := uint16(Width), uint16(Height), uint16(0), uint16(0)
	for _, px := range got.Pixels {
		minX, minY = min(minX, px.X), min(minY, px.Y)
		maxX, maxY = max(maxX, px.X), max(maxY, px.Y)
	}
	if minX != 3240 || maxX != 3839 || minY != 1680 || maxY != 2159 {
		t.Fatalf("logo spans x %d-%d y %d-%d, want x 3240-3839 y 1680-2159", minX, maxX, minY, maxY)
	}
	if len(got.Pixels) >= 600*480 {
		t.Fatalf("got %d pixels, transparent ones were not skipped", len(got.Pixels))
	}
}
