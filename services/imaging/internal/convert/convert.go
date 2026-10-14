// Package convert turns an uploaded still into WebP.
//
// The picture is decoded by Go's own decoders, which are memory safe, and only the decoded
// pixels reach libwebp. A picture is measured from its header before it is decoded, so one
// whose size would not fit in memory is refused without a bitmap being allocated for it.
package convert

import (
	"bytes"
	"errors"
	"image"
	"image/draw"
	"math"

	// The formats an upload is converted from, registered with image.Decode. GIF is not among
	// them: an animation is still converted by the api (#2447).
	_ "image/jpeg"
	_ "image/png"

	// Still WebP, which the standard library does not read.
	_ "golang.org/x/image/webp"
)

// MaxEdge is the longest edge libwebp writes.
const MaxEdge = 16383

var (
	// ErrUndecodable is a picture none of the decoders reads.
	ErrUndecodable = errors.New("the picture could not be decoded")
	// ErrTooManyPixels is a picture larger than the limit, measured before it is decoded.
	ErrTooManyPixels = errors.New("the picture has too many pixels")
)

// Target is what a picture is converted to: either brought under MaxEdge keeping its shape,
// which is a master, or drawn at exactly Width by Height, which is a rendition.
type Target struct {
	MaxEdge  int
	Width    int
	Height   int
	Quality  float32
	Lossless bool
}

// Result is the WebP bytes and the size they were drawn at.
type Result struct {
	WebP   []byte
	Width  int
	Height int
}

// Convert decodes data and encodes it as WebP at target, refusing a picture of more than
// maxPixels.
func Convert(data []byte, target Target, maxPixels int64) (Result, error) {
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
	return Result{WebP: webp, Width: targetWidth, Height: targetHeight}, nil
}

// fittedWithin brings a size under maxEdge keeping its shape, rounding as the api's
// ImageDimensions.Size.fittedWithin does: nothing is upscaled, and no edge rounds below a pixel.
func fittedWithin(width, height, maxEdge int) (int, int) {
	edge := max(width, height)
	if edge <= maxEdge {
		return width, height
	}
	ratio := float64(maxEdge) / float64(edge)
	return max(1, int(math.Round(float64(width)*ratio))), max(1, int(math.Round(float64(height)*ratio)))
}

// straightRGBA is the picture as 8-bit RGBA with straight alpha, which is what libwebp imports.
//
// Go's RGBA is premultiplied, so a picture with transparency is drawn into NRGBA instead. An
// opaque one is the same either way and takes the RGBA path, which draws a JPEG far faster.
func straightRGBA(picture image.Image) ([]byte, int) {
	bounds := picture.Bounds()
	if nrgba, ok := picture.(*image.NRGBA); ok && bounds.Min == (image.Point{}) {
		return nrgba.Pix, nrgba.Stride
	}
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
