package paint

import (
	"bytes"
	"encoding/binary"
)

// probeNonceLen is the random payload a macOS probe carries, so only the frame that probe made
// matches: the echo ID alone is 16 bits, and a datagram socket may rewrite it.
const probeNonceLen = 8

// isProbeFrame matches the Ethernet frame of our probe to dst carrying nonce.
func isProbeFrame(f []byte, dst [16]byte, nonce []byte) bool {
	if len(f) != 14+40+8+len(nonce) || binary.BigEndian.Uint16(f[12:]) != 0x86dd {
		return false
	}
	p := f[14:]
	return p[0]>>4 == 6 && p[6] == 58 && [16]byte(p[24:40]) == dst && p[40] == 128 && bytes.Equal(p[48:], nonce)
}

// bpfFrames splits a buffer read off a BSD /dev/bpf into its captured frames. Each sits behind a
// bpf_hdr (caplen at 8, hdrlen at 16) and starts on a 4-byte boundary.
func bpfFrames(buf []byte) [][]byte {
	var out [][]byte
	for len(buf) >= 18 {
		capLen := int(binary.NativeEndian.Uint32(buf[8:]))
		hdrLen := int(binary.NativeEndian.Uint16(buf[16:]))
		if hdrLen < 18 || hdrLen+capLen > len(buf) {
			break
		}
		out = append(out, buf[hdrLen:hdrLen+capLen])
		buf = buf[min(len(buf), (hdrLen+capLen+3)&^3):]
	}
	return out
}
