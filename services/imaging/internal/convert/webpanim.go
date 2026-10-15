package convert

import (
	"bytes"
	"encoding/binary"
	"image"

	"golang.org/x/image/webp"
)

func isAnimatedWebP(data []byte) bool {
	return len(data) >= 21 && string(data[0:4]) == "RIFF" && string(data[8:16]) == "WEBPVP8X" && data[20]&0x02 != 0
}

// webpAnimation reads the frame table without decoding. Each frame is later decoded as a still that
// declares the frame's size, which x/image/webp checks the bitstream against before allocating.
func webpAnimation(data []byte) (*animation, error) {
	if len(data) < 30 {
		return nil, errMalformed
	}
	a := &animation{width: 1 + int(u24(data[24:])), height: 1 + int(u24(data[27:]))}
	riffEnd := min(len(data), 8+int(binary.LittleEndian.Uint32(data[4:])))
	canvas := image.Rect(0, 0, a.width, a.height)

	err := func() error {
		for at := 12; at+8 <= riffEnd; {
			id, size := string(data[at:at+4]), int(binary.LittleEndian.Uint32(data[at+4:]))
			start, end := at+8, at+8+size
			if end > riffEnd {
				return errMalformed
			}
			at = end + size&1
			if id != "ANMF" {
				continue
			}
			if size < 16 {
				return errMalformed
			}
			header := data[start : start+16]
			x, y := 2*int(u24(header)), 2*int(u24(header[3:]))
			rect := image.Rect(x, y, x+1+int(u24(header[6:])), y+1+int(u24(header[9:])))
			if !rect.In(canvas) {
				return errMalformed
			}
			dispose := disposeNone
			if header[15]&0x01 != 0 {
				dispose = disposeBackground
			}
			bitstream := data[start+16 : end]
			a.frames = append(a.frames, frame{
				rect:     rect,
				duration: int(u24(header[12:])),
				blend:    header[15]&0x02 == 0,
				dispose:  dispose,
				decode:   func() (image.Image, error) { return webpFrame(bitstream, rect.Dx(), rect.Dy()) },
			})
		}
		return nil
	}()
	if len(a.frames) == 0 {
		return nil, errMalformed
	}
	return a, err
}

func webpFrame(chunks []byte, width, height int) (image.Image, error) {
	flags := byte(0)
	if bytes.HasPrefix(chunks, []byte("ALPH")) {
		flags = 0x10
	}
	still := make([]byte, 0, 30+len(chunks))
	still = append(still, "RIFF\x00\x00\x00\x00WEBPVP8X\x0a\x00\x00\x00"...)
	still = append(still, flags, 0, 0, 0)
	still = appendU24(appendU24(still, width-1), height-1)
	still = append(still, chunks...)
	binary.LittleEndian.PutUint32(still[4:], uint32(len(still)-8)) //nolint:gosec // under the body limit
	return webp.Decode(bytes.NewReader(still))
}

func appendU24(b []byte, n int) []byte {
	b = binary.LittleEndian.AppendUint32(b, uint32(n)) //nolint:gosec // under 2^24
	return b[:len(b)-1]
}

func u24(b []byte) uint32 {
	return uint32(b[0]) | uint32(b[1])<<8 | uint32(b[2])<<16
}
