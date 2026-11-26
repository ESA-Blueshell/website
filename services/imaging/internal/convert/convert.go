// Package convert turns an uploaded picture into WebP. Go decodes it, libwebp only encodes, and
// every limit is checked from headers before a pixel is allocated (architecture ADR-014).
package convert

import (
	"bytes"
	"errors"
	"image"
	"image/draw"
	"math"

	// The still decoders.
	_ "image/jpeg"
	_ "image/png"

	// Still WebP.
	_ "golang.org/x/image/webp"
)

// MaxEdge is the longest edge libwebp writes.
const MaxEdge = 16383

var (
	// ErrUndecodable is a picture none of the decoders reads.
	ErrUndecodable = errors.New("the picture could not be decoded")
	// ErrTooManyPixels is a picture over the pixel limit.
	ErrTooManyPixels = errors.New("the picture has too many pixels")
)

// Target is a master under MaxEdge, or a rendition at exactly Width by Height.
type Target struct {
	MaxEdge  int
	Width    int
	Height   int
	Quality  float32
	Lossless bool
}

// Limits bound what one picture may cost.
type Limits struct {
	// MaxPixels is the largest still or animation canvas.
	MaxPixels int64
	MaxFrames int
	// MaxAnimationPixels caps canvas times frames, which bounds how long a conversion runs.
	MaxAnimationPixels int64
}

// Result is the WebP bytes and the size they were drawn at.
type Result struct {
	WebP   []byte
	Width  int
	Height int
	Frames int
	// Unanimated is why an animation came out as its first frame, if it did.
	Unanimated error
}

// Convert encodes data as WebP at target.
func Convert(data []byte, target Target, limits Limits) (Result, error) {
	switch {
	case bytes.HasPrefix(data, []byte("GIF8")):
		a, err := gifAnimation(data)
		if a == nil {
			return Result{}, ErrUndecodable
		}
		return animate(a, err, target, limits)
	case isAnimatedWebP(data):
		a, err := webpAnimation(data)
		if a == nil {
			return Result{}, ErrUndecodable
		}
		return animate(a, err, target, limits)
	}
	return still(data, target, limits.MaxPixels)
}

func still(data []byte, target Target, maxPixels int64) (Result, error) {
	config, _, err := image.DecodeConfig(bytes.NewReader(data))
	if err != nil {
		return Result{}, ErrUndecodable
	}
	if config.Width <= 0 || config.Height <= 0 {
		return Result{}, ErrUndecodable
	}
	if int64(config.Width)*int64(config.Height) > maxPixels {
		return Result{}, ErrTooManyPixels
	}

	picture, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return Result{}, ErrUndecodable
	}
	bounds := picture.Bounds()
	width, height := bounds.Dx(), bounds.Dy()

	targetWidth, targetHeight := target.Width, target.Height
	if target.MaxEdge > 0 {
		targetWidth, targetHeight = fittedWithin(width, height, target.MaxEdge)
	}

	pix, stride := straightRGBA(picture)
	webp, err := encode(pix, stride, width, height, targetWidth, targetHeight, target.Lossless, target.Quality)
	if err != nil {
		return Result{}, err
	}
	return Result{WebP: webp, Width: targetWidth, Height: targetHeight, Frames: 1}, nil
}

// fittedWithin rounds as the api's ImageDimensions.Size.fittedWithin does.
func fittedWithin(width, height, maxEdge int) (int, int) {
	edge := max(width, height)
	if edge <= maxEdge {
		return width, height
	}
	ratio := float64(maxEdge) / float64(edge)
	return max(1, int(math.Round(float64(width)*ratio))), max(1, int(math.Round(float64(height)*ratio)))
}

// straightRGBA is what libwebp imports. Go's RGBA is premultiplied, so only an opaque picture,
// where both are the same bytes, takes the faster RGBA path.
func straightRGBA(picture image.Image) ([]byte, int) {
	if nrgba, ok := picture.(*image.NRGBA); ok {
		return nrgba.Pix, nrgba.Stride
	}
	bounds := picture.Bounds()
	frame := image.Rect(0, 0, bounds.Dx(), bounds.Dy())
	if opaque, ok := picture.(interface{ Opaque() bool }); ok && opaque.Opaque() {
		rgba := image.NewRGBA(frame)
		draw.Draw(rgba, frame, picture, bounds.Min, draw.Src)
		return rgba.Pix, rgba.Stride
	}
	nrgba := image.NewNRGBA(frame)
	draw.Draw(nrgba, frame, picture, bounds.Min, draw.Src)
	return nrgba.Pix, nrgba.Stride
}
