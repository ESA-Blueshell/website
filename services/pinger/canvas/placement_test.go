package canvas

import (
	"image"
	"image/color"
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

func TestPlaceInBoxFitsTheImagePreservingAspectAndCenters(t *testing.T) {
	src := transparent(40, 40)
	fill(src, image.Rect(10, 15, 30, 25), color.NRGBA{R: 0xff, A: 0xff}) // visible 20x10, aspect 2:1

	got := PlaceInBox(src, 100, 200, 100, 100)

	// Width binds: scale 5, so 100x50, centered in the 100x100 box at origin (100, 225).
	if got.Image.Bounds().Dx() != 100 || got.Image.Bounds().Dy() != 50 {
		t.Fatalf("scaled to %v, want 100x50", got.Image.Bounds().Size())
	}
	if len(got.Pixels) != 100*50 {
		t.Fatalf("got %d pixels, want %d", len(got.Pixels), 100*50)
	}
	for _, px := range got.Pixels {
		if px.X < 100 || px.X > 199 || px.Y < 225 || px.Y > 274 {
			t.Fatalf("pixel %+v outside the fitted box", px)
		}
	}
}

func TestPlaceInBoxRefusesAZeroBox(t *testing.T) {
	got := PlaceInBox(transparent(10, 10), 0, 0, 0, 100)
	if len(got.Pixels) != 0 {
		t.Fatalf("a zero-width box gave %d pixels", len(got.Pixels))
	}
}

func TestPlaceCropsToTheVisiblePixelsAndFitsTheLongSide(t *testing.T) {
	src := transparent(100, 100)
	red := color.NRGBA{R: 0xff, A: 0xff}
	fill(src, image.Rect(40, 40, 60, 50), red)

	got := Place(src, 600, image.Pt(1920, 720))

	if got.Image.Bounds().Dx() != 600 || got.Image.Bounds().Dy() != 300 {
		t.Fatalf("scaled to %v, want 600x300", got.Image.Bounds().Size())
	}
	if len(got.Pixels) != 600*300 {
		t.Fatalf("got %d pixels, want %d", len(got.Pixels), 600*300)
	}
	// Centered on (1920, 720): origin is (1920-300, 720-150) = (1620, 570).
	for _, px := range got.Pixels {
		if px.X < 1620 || px.X > 2219 || px.Y < 570 || px.Y > 869 {
			t.Fatalf("pixel %+v outside the centered 600x300", px)
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
	src.SetNRGBA(12, 0, color.NRGBA{G: 0xff, A: minAlpha - 1})

	got := Place(src, 600, image.Pt(1920, 720))

	if len(got.Pixels) != 1198 {
		t.Fatalf("got %d pixels, want 1198", len(got.Pixels))
	}
	// 600x2 scaled stays 600x2; origin is (1620, 719).
	var edge *Pixel
	for i, px := range got.Pixels {
		if (px.X == 1630 || px.X == 1632) && px.Y == 719 {
			t.Fatalf("transparent pixel was kept: %+v", px)
		}
		if px.X == 1631 && px.Y == 719 {
			edge = &got.Pixels[i]
		}
	}
	want := Pixel{X: 1631, Y: 719, R: 0x40, G: 0x80, B: 0xc0, A: 0x80}
	if edge == nil || *edge != want {
		t.Fatalf("edge pixel %+v, want %+v", edge, want)
	}
}
