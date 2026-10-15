package convert

import (
	"bytes"
	"encoding/binary"
	"errors"
	"image"
	"image/gif"
)

var errMalformed = errors.New("the picture's structure could not be read")

// gifAnimation reads the structure of a GIF without decoding any frame, so its size and frame
// count are known before a pixel is allocated. Each frame is decoded on its own later, by Go's
// decoder, from a GIF of that frame alone.
//
// The canvas is the logical screen where it holds every frame, and grows to hold the ones that
// reach outside it, as the api's reader did: cropping them would drop what was drawn.
//
// A GIF whose structure breaks off after its first frame answers the frames before the break
// and the error, which the caller turns into a still of the first frame.
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
				// The LZW code size, then the image data.
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

// subBlocks is the offset just past the sub-blocks that start at at.
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

// frame is one GIF frame as the compositor draws it.
//
// A GIF counts in hundredths of a second, and a frame that asks for none or for one is asking
// faster than any browser draws: both are held at the tenth of a second every renderer
// substitutes, as the api did.
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

// decode reads the frame through a GIF that holds it alone, on a logical screen the size of the
// canvas, so that Go's decoder accepts a frame that reaches outside the screen the file declared.
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
