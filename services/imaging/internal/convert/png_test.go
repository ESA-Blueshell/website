package convert

import (
	"bytes"
	"errors"
	"image"
	"image/png"
	"testing"
)

func TestStoredWebPIsDrawnAsPNGAtItsOwnSize(t *testing.T) {
	stored, err := Convert(jpegOf(t, picture(60, 30, false)), Target{MaxEdge: 2560, Quality: 82}, roomy)
	if err != nil {
		t.Fatal(err)
	}

	drawing, err := PNG(stored.WebP, roomy)
	if err != nil {
		t.Fatal(err)
	}

	decoded, err := png.Decode(bytes.NewReader(drawing.PNG))
	if err != nil {
		t.Fatalf("not a PNG: %v", err)
	}
	if decoded.Bounds() != image.Rect(0, 0, 60, 30) || drawing.Width != 60 || drawing.Height != 30 {
		t.Fatalf("drawn at %v, said %dx%d", decoded.Bounds(), drawing.Width, drawing.Height)
	}
}

func TestPNGRefusesWhatConvertRefuses(t *testing.T) {
	if _, err := PNG([]byte("not a picture"), roomy); !errors.Is(err, ErrUndecodable) {
		t.Errorf("garbage: got %v, want ErrUndecodable", err)
	}
	if _, err := PNG(pngOf(t, picture(100, 100, false)), Limits{MaxPixels: 9_999}); !errors.Is(err, ErrTooManyPixels) {
		t.Errorf("too many pixels: got %v, want ErrTooManyPixels", err)
	}
}
