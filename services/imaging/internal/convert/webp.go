package convert

import (
	"errors"
	"fmt"
	"image"

	"github.com/ESA-Blueshell/website/services/imaging/internal/libwebp"
)

var errAnimationRefused = errors.New("libwebp refused the animation")

func encode(pix []byte, stride, width, height, targetWidth, targetHeight int, lossless bool, quality float32) ([]byte, error) {
	webp, code := libwebp.Encode(pix, stride, width, height, targetWidth, targetHeight, lossless, quality)
	if code != 0 {
		return nil, fmt.Errorf("libwebp refused the picture (error %d)", code)
	}
	return webp, nil
}

type animationEncoder struct {
	animation *libwebp.Animation
}

func newAnimationEncoder(width, height int, lossless bool, quality float32) (*animationEncoder, error) {
	animation := libwebp.NewAnimation(width, height, lossless, quality)
	if animation.Refused() {
		return nil, errAnimationRefused
	}
	return &animationEncoder{animation: animation}, nil
}

func (e *animationEncoder) add(canvas *image.NRGBA, timestamp int) error {
	size := canvas.Rect.Size()
	if e.animation.Add(canvas.Pix, size.X, size.Y, canvas.Stride, timestamp) != 0 {
		return errAnimationRefused
	}
	return nil
}

func (e *animationEncoder) assemble(end int) ([]byte, error) {
	webp, code := e.animation.Assemble(end)
	if code != 0 {
		return nil, errAnimationRefused
	}
	return webp, nil
}

func (e *animationEncoder) close() {
	e.animation.Close()
}
