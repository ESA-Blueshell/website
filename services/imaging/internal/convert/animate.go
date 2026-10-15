package convert

import (
	"errors"
	"image"
	"image/color"
	"image/draw"
	"math"
)

// ErrTooManyFrames is an animation over the frame or animation pixel limit.
var ErrTooManyFrames = errors.New("the animation has too many frames for its size")

var errTooLong = errors.New("the animation runs longer than WebP can say")

type animation struct {
	width, height int
	frames        []frame
}

type disposal int

const (
	disposeNone disposal = iota
	disposeBackground
	disposePrevious
)

// frameEncoder is a seam for tests: libwebp fails only when out of memory.
type frameEncoder interface {
	add(canvas *image.NRGBA, timestamp int) error
	assemble(end int) ([]byte, error)
	close()
}

var newFrameEncoder = func(width, height int, lossless bool, quality float32) (frameEncoder, error) {
	return newAnimationEncoder(width, height, lossless, quality)
}

type frame struct {
	rect     image.Rectangle
	duration int
	blend    bool
	dispose  disposal
	decode   func() (image.Image, error)
}

// animate holds one canvas and one frame at a time. A frame that will not decode makes the picture
// a still of its first frame (api ADR-029).
func animate(a *animation, broken error, target Target, limits Limits) (Result, error) {
	pixels := int64(a.width) * int64(a.height)
	if pixels > limits.MaxPixels {
		return Result{}, ErrTooManyPixels
	}
	if len(a.frames) > limits.MaxFrames || pixels*int64(len(a.frames)) > limits.MaxAnimationPixels {
		return Result{}, ErrTooManyFrames
	}
	width, height := target.Width, target.Height
	if target.MaxEdge > 0 {
		width, height = fittedWithin(a.width, a.height, target.MaxEdge)
	}
	if broken != nil || len(a.frames) == 1 {
		return firstFrame(a, broken, width, height, target)
	}
	// After moving returns, so two canvases are never held at once.
	result, undecoded, err := moving(a, width, height, target)
	if undecoded != nil {
		return firstFrame(a, undecoded, width, height, target)
	}
	return result, err
}

// moving answers the animation, a frame's decode error, or the encoder's error.
func moving(a *animation, width, height int, target Target) (Result, error, error) {
	encoder, err := newFrameEncoder(width, height, target.Lossless, target.Quality)
	if err != nil {
		return Result{}, nil, err
	}
	defer encoder.close()
	canvas := image.NewNRGBA(image.Rect(0, 0, a.width, a.height))
	var previous []byte
	timestamp := 0
	for _, f := range a.frames {
		picture, err := f.decode()
		if err != nil {
			return Result{}, err, nil
		}
		if f.dispose == disposePrevious {
			// Sized once: append growth would double a full-canvas copy.
			if size := f.rect.Intersect(canvas.Rect).Size(); cap(previous) < size.X*size.Y*4 {
				previous = make([]byte, 0, size.X*size.Y*4)
			}
			previous = rows(canvas, f.rect, previous[:0], func(row, saved []byte) []byte { return append(saved, row...) })
		}
		paint(canvas, picture, f.rect.Min, f.blend)
		if err := encoder.add(canvas, timestamp); err != nil {
			return Result{}, nil, err
		}
		// libwebp counts time in an int of milliseconds.
		if timestamp += f.duration; timestamp > math.MaxInt32 {
			return Result{}, errTooLong, nil
		}
		switch f.dispose {
		case disposeBackground:
			rows(canvas, f.rect, nil, func(row, _ []byte) []byte { clear(row); return nil })
		case disposePrevious:
			rows(canvas, f.rect, previous, func(row, saved []byte) []byte { return saved[copy(row, saved):] })
		}
	}
	webp, err := encoder.assemble(timestamp)
	if err != nil {
		return Result{}, nil, err
	}
	return Result{WebP: webp, Width: width, Height: height, Frames: len(a.frames)}, nil, nil
}

func firstFrame(a *animation, broken error, width, height int, target Target) (Result, error) {
	picture, err := a.frames[0].decode()
	if err != nil {
		return Result{}, ErrUndecodable
	}
	canvas := image.NewNRGBA(image.Rect(0, 0, a.width, a.height))
	paint(canvas, picture, a.frames[0].rect.Min, true)
	webp, err := encode(canvas.Pix, canvas.Stride, a.width, a.height, width, height, target.Lossless, target.Quality)
	if err != nil {
		return Result{}, err
	}
	return Result{WebP: webp, Width: width, Height: height, Frames: 1, Unanimated: broken}, nil
}

// paint blends straight alpha as the WebP container spec does, a row at a time so a frame costs
// no second canvas.
func paint(canvas *image.NRGBA, picture image.Image, at image.Point, blend bool) {
	bounds := picture.Bounds()
	area := image.Rectangle{Min: at, Max: at.Add(bounds.Size())}.Intersect(canvas.Rect)
	row := rowsOf(picture, area.Dx())
	for y := area.Min.Y; y < area.Max.Y; y++ {
		src := row(bounds.Min.X+area.Min.X-at.X, bounds.Min.Y+y-at.Y)
		dst := canvas.Pix[canvas.PixOffset(area.Min.X, y):]
		for x := 0; x < len(src); x += 4 {
			s, d := src[x:x+4:x+4], dst[x:x+4:x+4]
			switch {
			case !blend || s[3] == 255:
				copy(d, s)
			case s[3] == 0:
			default:
				over(d, s)
			}
		}
	}
}

func rowsOf(picture image.Image, width int) func(x, y int) []byte {
	switch p := picture.(type) {
	case *image.NRGBA:
		return func(x, y int) []byte {
			at := p.PixOffset(x, y)
			return p.Pix[at : at+width*4]
		}
	case *image.Paletted:
		// 256 entries, so an index past the palette is transparent rather than a panic.
		var colours [256 * 4]byte
		for i, c := range p.Palette {
			n := color.NRGBAModel.Convert(c).(color.NRGBA)
			copy(colours[i*4:], []byte{n.R, n.G, n.B, n.A})
		}
		buffer := make([]byte, width*4)
		return func(x, y int) []byte {
			at := p.PixOffset(x, y)
			for i, index := range p.Pix[at : at+width] {
				copy(buffer[i*4:i*4+4], colours[int(index)*4:])
			}
			return buffer
		}
	case *image.YCbCr:
		// Opaque, so RGBA's premultiplied bytes are already straight.
		buffer := image.NewRGBA(image.Rect(0, 0, width, 1))
		return func(x, y int) []byte {
			draw.Draw(buffer, buffer.Rect, p, image.Pt(x, y), draw.Src)
			return buffer.Pix
		}
	default:
		buffer := image.NewNRGBA(image.Rect(0, 0, width, 1))
		return func(x, y int) []byte {
			draw.Draw(buffer, buffer.Rect, p, image.Pt(x, y), draw.Src)
			return buffer.Pix
		}
	}
}

// over needs a source alpha above zero.
func over(d, s []byte) {
	sa, da := uint32(s[3]), uint32(d[3])*(255-uint32(s[3]))/255
	a := sa + da
	for i := range 3 {
		d[i] = byte((uint32(s[i])*sa + uint32(d[i])*da) / a) //nolint:gosec // a weighted mean of two bytes
	}
	d[3] = byte(a) //nolint:gosec // sa + da is at most 255
}

func rows(canvas *image.NRGBA, rect image.Rectangle, carry []byte, do func(row, carry []byte) []byte) []byte {
	area := rect.Intersect(canvas.Rect)
	for y := area.Min.Y; y < area.Max.Y; y++ {
		carry = do(canvas.Pix[canvas.PixOffset(area.Min.X, y):canvas.PixOffset(area.Max.X, y)], carry)
	}
	return carry
}
