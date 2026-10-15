package convert

import (
	"bytes"
	"errors"
	"image"
	"image/color"
	"image/jpeg"
	"image/png"
	"os"
	"os/exec"
	"path/filepath"
	"testing"

	"golang.org/x/image/webp"
)

func picture(width, height int, transparent bool) *image.NRGBA {
	m := image.NewNRGBA(image.Rect(0, 0, width, height))
	for y := range height {
		for x := range width {
			a := uint8(255)
			if transparent && x < width/2 {
				a = uint8(x * 255 / width) //nolint:gosec // x is under width/2, so this is under 128
			}
			m.SetNRGBA(x, y, color.NRGBA{R: uint8(x), G: uint8(y), B: uint8(x + y), A: a}) //nolint:gosec // a pattern; wrapping is fine
		}
	}
	return m
}

func pngOf(t *testing.T, m image.Image) []byte {
	t.Helper()
	var b bytes.Buffer
	if err := png.Encode(&b, m); err != nil {
		t.Fatal(err)
	}
	return b.Bytes()
}

func jpegOf(t *testing.T, m image.Image) []byte {
	t.Helper()
	var b bytes.Buffer
	if err := jpeg.Encode(&b, m, &jpeg.Options{Quality: 90}); err != nil {
		t.Fatal(err)
	}
	return b.Bytes()
}

func sizeOf(t *testing.T, data []byte) (int, int) {
	t.Helper()
	config, err := webp.DecodeConfig(bytes.NewReader(data))
	if err != nil {
		t.Fatalf("not a WebP: %v", err)
	}
	return config.Width, config.Height
}

func TestMasterIsBroughtUnderItsCeilingKeepingItsShape(t *testing.T) {
	got, err := Convert(jpegOf(t, picture(3000, 100, false)), Target{MaxEdge: 2560, Quality: 82}, 50_000_000)
	if err != nil {
		t.Fatal(err)
	}
	if got.Width != 2560 || got.Height != 85 {
		t.Fatalf("drawn at %dx%d, want 2560x85", got.Width, got.Height)
	}
	if w, h := sizeOf(t, got.WebP); w != 2560 || h != 85 {
		t.Fatalf("encoded at %dx%d, want 2560x85", w, h)
	}
}

func TestMasterWithinItsCeilingKeepsItsSize(t *testing.T) {
	got, err := Convert(pngOf(t, picture(300, 200, true)), Target{MaxEdge: 512, Lossless: true, Quality: 75}, 50_000_000)
	if err != nil {
		t.Fatal(err)
	}
	if w, h := sizeOf(t, got.WebP); w != 300 || h != 200 || got.Width != 300 || got.Height != 200 {
		t.Fatalf("drawn at %dx%d, encoded at %dx%d, want 300x200", got.Width, got.Height, w, h)
	}
}

func TestLosslessKeepsEveryPixelAndItsAlpha(t *testing.T) {
	source := picture(64, 48, true)
	got, err := Convert(pngOf(t, source), Target{MaxEdge: 512, Lossless: true, Quality: 75}, 50_000_000)
	if err != nil {
		t.Fatal(err)
	}
	decoded, err := webp.Decode(bytes.NewReader(got.WebP))
	if err != nil {
		t.Fatal(err)
	}
	for y := range 48 {
		for x := range 64 {
			want := source.NRGBAAt(x, y)
			if want.A == 0 {
				continue // libwebp may rewrite what a fully transparent pixel hides
			}
			if have := color.NRGBAModel.Convert(decoded.At(x, y)).(color.NRGBA); have != want {
				t.Fatalf("pixel %d,%d is %v, want %v", x, y, have, want)
			}
		}
	}
}

func TestRenditionIsDrawnAtTheSizeAskedFor(t *testing.T) {
	master, err := Convert(pngOf(t, picture(640, 256, false)), Target{MaxEdge: 2560, Quality: 82}, 50_000_000)
	if err != nil {
		t.Fatal(err)
	}
	got, err := Convert(master.WebP, Target{Width: 320, Height: 128, Quality: 82}, 50_000_000)
	if err != nil {
		t.Fatal(err)
	}
	if w, h := sizeOf(t, got.WebP); w != 320 || h != 128 {
		t.Fatalf("encoded at %dx%d, want 320x128", w, h)
	}
}

func TestPictureOverThePixelLimitIsRefusedBeforeItIsDecoded(t *testing.T) {
	_, err := Convert(pngOf(t, picture(100, 100, false)), Target{MaxEdge: 2560, Quality: 82}, 9_999)
	if !errors.Is(err, ErrTooManyPixels) {
		t.Fatalf("got %v, want ErrTooManyPixels", err)
	}
}

func TestBytesNoDecoderReadsAreRefused(t *testing.T) {
	for name, data := range map[string][]byte{
		"garbage":   []byte("not a picture"),
		"truncated": pngOf(t, picture(50, 50, false))[:60],
		"gif":       []byte("GIF89a\x01\x00\x01\x00\x00\x00\x00;"),
	} {
		if _, err := Convert(data, Target{MaxEdge: 2560, Quality: 82}, 50_000_000); !errors.Is(err, ErrUndecodable) {
			t.Errorf("%s: got %v, want ErrUndecodable", name, err)
		}
	}
}

// The bytes cwebp wrote when the api ran it, for the same picture and the same flags. Skipped
// where cwebp is not installed; the image's check stage installs it.
func TestOutputMatchesCwebp(t *testing.T) {
	cwebp, err := exec.LookPath("cwebp")
	if err != nil {
		t.Skip("cwebp is not installed")
	}
	cases := []struct {
		name   string
		source image.Image
		target Target
		args   []string
	}{
		{"lossy, resized", picture(900, 300, false), Target{Width: 320, Height: 107, Quality: 82}, []string{"-q", "82", "-resize", "320", "107"}},
		{"lossy, transparent", picture(200, 100, true), Target{MaxEdge: 2560, Quality: 85}, []string{"-q", "85"}},
		{"lossless, resized", picture(800, 600, true), Target{MaxEdge: 512, Lossless: true, Quality: 75}, []string{"-lossless", "-resize", "512", "384"}},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			dir := t.TempDir()
			in, out := filepath.Join(dir, "in.png"), filepath.Join(dir, "out.webp")
			if err := os.WriteFile(in, pngOf(t, c.source), 0o600); err != nil {
				t.Fatal(err)
			}
			args := append([]string{"-quiet"}, c.args...)
			if output, err := exec.CommandContext(t.Context(), cwebp, append(args, in, "-o", out)...).CombinedOutput(); err != nil {
				t.Fatalf("cwebp: %v: %s", err, output)
			}
			want, err := os.ReadFile(out)
			if err != nil {
				t.Fatal(err)
			}
			got, err := Convert(pngOf(t, c.source), c.target, 50_000_000)
			if err != nil {
				t.Fatal(err)
			}
			if !bytes.Equal(got.WebP, want) {
				t.Fatalf("%d bytes, cwebp wrote %d", len(got.WebP), len(want))
			}
		})
	}
}

func TestFittedWithinRoundsAsTheApiDoes(t *testing.T) {
	for _, c := range []struct{ w, h, edge, wantW, wantH int }{
		{3000, 100, 2560, 2560, 85},
		{100000, 10, 2560, 2560, 1},
		{500, 500, 512, 500, 500},
		{1025, 2049, 512, 256, 512},
	} {
		if w, h := fittedWithin(c.w, c.h, c.edge); w != c.wantW || h != c.wantH {
			t.Errorf("%dx%d within %d is %dx%d, want %dx%d", c.w, c.h, c.edge, w, h, c.wantW, c.wantH)
		}
	}
}

func TestPictureWithNoPixelsIsRefused(t *testing.T) {
	data := jpegOf(t, picture(8, 8, false))
	for i := 0; i+1 < len(data); i++ {
		if data[i] == 0xFF && data[i+1] == 0xC0 {
			data[i+5], data[i+6] = 0, 0 // the frame's height
			break
		}
	}
	if _, err := Convert(data, Target{MaxEdge: 2560, Quality: 82}, 50_000_000); !errors.Is(err, ErrUndecodable) {
		t.Fatalf("got %v, want ErrUndecodable", err)
	}
}

func TestPaletteWithTransparencyKeepsItsAlpha(t *testing.T) {
	palette := color.Palette{color.NRGBA{A: 0}, color.NRGBA{R: 200, G: 10, B: 10, A: 255}}
	source := image.NewPaletted(image.Rect(0, 0, 4, 4), palette)
	source.SetColorIndex(1, 1, 1)
	got, err := Convert(pngOf(t, source), Target{MaxEdge: 512, Lossless: true, Quality: 75}, 50_000_000)
	if err != nil {
		t.Fatal(err)
	}
	decoded, err := webp.Decode(bytes.NewReader(got.WebP))
	if err != nil {
		t.Fatal(err)
	}
	if _, _, _, a := decoded.At(0, 0).RGBA(); a != 0 {
		t.Fatalf("a transparent pixel came back with alpha %d", a)
	}
	if r, _, _, a := decoded.At(1, 1).RGBA(); r>>8 != 200 || a>>8 != 255 {
		t.Fatalf("an opaque pixel came back as %v", decoded.At(1, 1))
	}
}

func TestSizeLibwebpWillNotDrawIsAnError(t *testing.T) {
	_, err := Convert(pngOf(t, picture(10, 10, false)), Target{Quality: 82}, 50_000_000)
	if err == nil || errors.Is(err, ErrUndecodable) {
		t.Fatalf("got %v, want libwebp's refusal", err)
	}
}
