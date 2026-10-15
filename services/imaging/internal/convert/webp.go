package convert

import (
	"errors"
	"fmt"
	"image"

	"github.com/ESA-Blueshell/website/services/imaging/internal/libwebp"
)

var errAnimationRefused = errors.New("libwebp refused the animation")

// encode writes straight-alpha RGBA pixels as WebP at the target size.
func encode(pix []byte, stride, width, height, targetWidth, targetHeight int, lossless bool, quality float32) ([]byte, error) {
	webp, code := libwebp.Encode(pix, stride, width, height, targetWidth, targetHeight, lossless, quality)
	if code != 0 {
		return nil, fmt.Errorf("libwebp refused the picture (error %d)", code)
	}
	return webp, nil
}

// animationEncoder assembles an animation at one size from canvases of another.
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

// add is canvas as the frame shown from timestamp milliseconds in.
func (e *animationEncoder) add(canvas *image.NRGBA, timestamp int) error {
	size := canvas.Rect.Size()
	if e.animation.Add(canvas.Pix, size.X, size.Y, canvas.Stride, timestamp) != 0 {
		return errAnimationRefused
	}
	return nil
}

// assemble is the animation, its last frame shown until end milliseconds in.
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
