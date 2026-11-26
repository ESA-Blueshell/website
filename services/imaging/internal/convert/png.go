package convert

import (
	"bytes"
	"image/png"
)

// Drawing is a picture as PNG at its own size.
type Drawing struct {
	PNG    []byte
	Width  int
	Height int
}

// PNG decodes a still and writes it as PNG at its own size, for a document that cannot read WebP.
func PNG(data []byte, limits Limits) (Drawing, error) {
	picture, err := decodeStill(data, limits.MaxPixels)
	if err != nil {
		return Drawing{}, err
	}
	var out bytes.Buffer
	// A decoded picture has pixels and a buffer takes every write, so the encoder has nothing to refuse.
	_ = png.Encode(&out, picture)
	bounds := picture.Bounds()
	return Drawing{PNG: out.Bytes(), Width: bounds.Dx(), Height: bounds.Dy()}, nil
}
