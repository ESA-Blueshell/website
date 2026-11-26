package convert

import (
	"bytes"
	"encoding/binary"
	"errors"
	"image"
	"image/gif"
)

var errMalformed = errors.New("the picture's structure could not be read")

// gifAnimation reads the block structure without decoding, so limits apply before allocation. The
// canvas grows to hold frames outside the logical screen, as the api's reader did.
func gifAnimation(data []byte) (*animation, error) {
	const header = 13
	if len(data) < header {
		return nil, errMalformed
	}
	end := header
	if data[10]&0x80 != 0 {
		end += 3 << (data[10]&0x07 + 1)
	}
	if end > len(data) {
		return nil, errMalformed
	}
	prefix := data[:end]
	width := int(binary.LittleEndian.Uint16(data[6:]))
	height := int(binary.LittleEndian.Uint16(data[8:]))

	var frames []gifFrame
	var control []byte
	at := end
	err := func() error {
		for at < len(data) {
			switch data[at] {
			case 0x3B:
				return nil
			case 0x21:
				blocks, err := subBlocks(data, at+2)
				if err != nil {
					return err
				}
				if data[at+1] == 0xF9 && blocks-at == 8 {
					control = data[at:blocks]
				}
				at = blocks
			case 0x2C:
				if at+10 > len(data) {
					return errMalformed
				}
				descriptor := data[at : at+10]
				start := at + 10
				if descriptor[9]&0x80 != 0 {
					start += 3 << (descriptor[9]&0x07 + 1)
				}
				blocks, err := subBlocks(data, start+1)
				if err != nil {
					return err
				}
				left := int(binary.LittleEndian.Uint16(descriptor[1:]))
				top := int(binary.LittleEndian.Uint16(descriptor[3:]))
				rect := image.Rect(left, top,
					left+int(binary.LittleEndian.Uint16(descriptor[5:])),
					top+int(binary.LittleEndian.Uint16(descriptor[7:])))
				if rect.Max.X > 0xFFFF || rect.Max.Y > 0xFFFF {
					return errMalformed
				}
				frames = append(frames, gifFrame{control: control, image: data[at:blocks], rect: rect})
				width, height = max(width, rect.Max.X), max(height, rect.Max.Y)
				control = nil
				at = blocks
			default:
				return errMalformed
			}
		}
		return nil
	}()
	if len(frames) == 0 {
		return nil, errMalformed
	}

	a := &animation{width: max(1, width), height: max(1, height)}
	for _, f := range frames {
		a.frames = append(a.frames, f.frame(prefix, a.width, a.height))
	}
	return a, err
}

func subBlocks(data []byte, at int) (int, error) {
	for at < len(data) {
		size := int(data[at])
		at += 1 + size
		if size == 0 {
			return at, nil
		}
	}
	return 0, errMalformed
}

type gifFrame struct {
	control []byte
	image   []byte
	rect    image.Rectangle
}

// frame holds a delay of 0 or 1 hundredths at 100ms, as browsers do.
func (f gifFrame) frame(prefix []byte, width, height int) frame {
	delay, disposal := 0, 0
	if f.control != nil {
		delay = int(binary.LittleEndian.Uint16(f.control[4:]))
		disposal = int(f.control[3]>>2) & 0x07
	}
	duration := delay * 10
	if delay <= 1 {
		duration = 100
	}
	dispose := disposeNone
	switch disposal {
	case 2:
		dispose = disposeBackground
	case 3:
		dispose = disposePrevious
	}
	return frame{
		rect:     f.rect,
		duration: duration,
		blend:    true,
		dispose:  dispose,
		decode:   func() (image.Image, error) { return f.decode(prefix, width, height) },
	}
}

// decode uses a one-frame GIF on a canvas-sized screen, so Go accepts frames outside the declared one.
func (f gifFrame) decode(prefix []byte, width, height int) (image.Image, error) {
	one := make([]byte, 0, len(prefix)+len(f.control)+len(f.image)+1)
	one = append(one, prefix...)
	copy(one[0:6], "GIF89a")
	binary.LittleEndian.PutUint16(one[6:], uint16(width))  //nolint:gosec // no frame reaches past 65535
	binary.LittleEndian.PutUint16(one[8:], uint16(height)) //nolint:gosec // in either direction
	one = append(one, f.control...)
	one = append(one, f.image...)
	one = append(one, 0x3B)
	return gif.Decode(bytes.NewReader(one))
}
