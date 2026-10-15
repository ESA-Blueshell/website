//go:build integration

// Package integration drives the hardened container at IMAGING_URL over HTTP.
package integration

import (
	"bytes"
	"encoding/binary"
	"hash/crc32"
	"image"
	"image/color"
	"image/gif"
	"image/jpeg"
	"image/png"
	"io"
	"net/http"
	"os"
	"strconv"
	"testing"
	"time"

	"golang.org/x/image/webp"
)

var client = &http.Client{Timeout: 2 * time.Minute}

func baseURL(t *testing.T) string {
	t.Helper()
	url := os.Getenv("IMAGING_URL")
	if url == "" {
		t.Fatal("IMAGING_URL names no running imaging service")
	}
	return url
}

type answer struct {
	status int
	header http.Header
	body   []byte
}

func convert(t *testing.T, query string, body []byte) answer {
	t.Helper()
	request, err := http.NewRequestWithContext(t.Context(), http.MethodPost, baseURL(t)+"/convert?"+query, bytes.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	response, err := client.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	defer func() { _ = response.Body.Close() }()
	read, err := io.ReadAll(response.Body)
	if err != nil {
		t.Fatal(err)
	}
	return answer{status: response.StatusCode, header: response.Header, body: read}
}

func photo(width, height int) *image.RGBA {
	m := image.NewRGBA(image.Rect(0, 0, width, height))
	for y := range height {
		for x := range width {
			m.SetRGBA(x, y, color.RGBA{R: uint8(x), G: uint8(y), B: 128, A: 255}) //nolint:gosec // a pattern; wrapping is fine
		}
	}
	return m
}

func encoded(t *testing.T, encode func(io.Writer) error) []byte {
	t.Helper()
	var b bytes.Buffer
	if err := encode(&b); err != nil {
		t.Fatal(err)
	}
	return b.Bytes()
}

func expectWebP(t *testing.T, got answer, width, height int) {
	t.Helper()
	if got.status != http.StatusOK {
		t.Fatalf("answered %d: %s", got.status, got.body)
	}
	config, err := webp.DecodeConfig(bytes.NewReader(got.body))
	if err != nil {
		t.Fatalf("not a WebP: %v", err)
	}
	if config.Width != width || config.Height != height {
		t.Fatalf("drawn at %dx%d, want %dx%d", config.Width, config.Height, width, height)
	}
	if got.header.Get("Image-Width") != strconv.Itoa(width) || got.header.Get("Image-Height") != strconv.Itoa(height) {
		t.Fatalf("headers name %sx%s", got.header.Get("Image-Width"), got.header.Get("Image-Height"))
	}
}

func TestHealth(t *testing.T) {
	request, err := http.NewRequestWithContext(t.Context(), http.MethodGet, baseURL(t)+"/healthz", nil)
	if err != nil {
		t.Fatal(err)
	}
	response, err := client.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	defer func() { _ = response.Body.Close() }()
	if response.StatusCode != http.StatusNoContent {
		t.Fatalf("answered %d", response.StatusCode)
	}
}

func TestJpegMasterIsBroughtUnderItsCeiling(t *testing.T) {
	source := encoded(t, func(w io.Writer) error { return jpeg.Encode(w, photo(3000, 1200), nil) })
	expectWebP(t, convert(t, "max-edge=2560&quality=82", source), 2560, 1024)
}

func TestPngIconIsKeptLossless(t *testing.T) {
	source := encoded(t, func(w io.Writer) error { return png.Encode(w, photo(300, 300)) })
	expectWebP(t, convert(t, "max-edge=512&lossless=true", source), 300, 300)
}

func TestWebpMasterGivesItsRenditions(t *testing.T) {
	source := encoded(t, func(w io.Writer) error { return png.Encode(w, photo(1600, 900)) })
	master := convert(t, "max-edge=2560&quality=82", source)
	expectWebP(t, master, 1600, 900)
	for _, width := range []int{320, 640, 1280} {
		height := width * 9 / 16
		expectWebP(t, convert(t, "width="+strconv.Itoa(width)+"&height="+strconv.Itoa(height)+"&quality=82", master.body), width, height)
	}
}

func TestBytesThatAreNoPictureAreRefused(t *testing.T) {
	if got := convert(t, "max-edge=2560&quality=82", []byte("<svg onload=alert(1)>")); got.status != http.StatusUnprocessableEntity {
		t.Fatalf("answered %d: %s", got.status, got.body)
	}
}

// 20000x20000 in the header only: refused before 1.6 GB is allocated.
func TestPictureClaimingTooManyPixelsIsRefusedFromItsHeader(t *testing.T) {
	header := make([]byte, 13)
	binary.BigEndian.PutUint32(header[0:], 20000)
	binary.BigEndian.PutUint32(header[4:], 20000)
	header[8], header[9] = 8, 6 // 8-bit RGBA
	chunk := append([]byte("IHDR"), header...)
	var bomb bytes.Buffer
	bomb.WriteString("\x89PNG\r\n\x1a\n")
	_ = binary.Write(&bomb, binary.BigEndian, uint32(13)) // IHDR's length
	bomb.Write(chunk)
	_ = binary.Write(&bomb, binary.BigEndian, crc32.ChecksumIEEE(chunk))

	if got := convert(t, "max-edge=2560&quality=82", bomb.Bytes()); got.status != http.StatusUnprocessableEntity {
		t.Fatalf("answered %d: %s", got.status, got.body)
	}
}

func TestBodyOverTheByteLimitIsRefused(t *testing.T) {
	if got := convert(t, "max-edge=2560&quality=82", make([]byte, 21<<20)); got.status != http.StatusRequestEntityTooLarge {
		t.Fatalf("answered %d", got.status)
	}
}

func banner(t *testing.T, width, height, frames int) []byte {
	t.Helper()
	g := &gif.GIF{}
	for i := range frames {
		m := image.NewPaletted(image.Rect(0, 0, width, height), color.Palette{
			color.RGBA{R: uint8(i * 40), G: 80, B: 200, A: 255}, //nolint:gosec // a pattern; wrapping is fine
			color.RGBA{R: 255, G: 255, B: 255, A: 255},
		})
		for x := range width / 4 {
			m.SetColorIndex(x+i*width/(4*frames), height/2, 1)
		}
		g.Image = append(g.Image, m)
		g.Delay = append(g.Delay, 10)
	}
	return encoded(t, func(w io.Writer) error { return gif.EncodeAll(w, g) })
}

func framesIn(body []byte) int {
	return bytes.Count(body, []byte("ANMF"))
}

func TestAnimatedBannerStaysAnimatedAtEveryWidth(t *testing.T) {
	master := convert(t, "max-edge=2560&quality=82", banner(t, 800, 450, 4))
	expectWebP(t, master, 800, 450)
	if framesIn(master.body) != 4 {
		t.Fatalf("the master holds %d frames", framesIn(master.body))
	}
	for _, width := range []int{160, 320, 640} {
		height := width * 9 / 16
		rendition := convert(t, "width="+strconv.Itoa(width)+"&height="+strconv.Itoa(height)+"&quality=82", master.body)
		expectWebP(t, rendition, width, height)
		if framesIn(rendition.body) != 4 {
			t.Fatalf("the %dpx rendition holds %d frames", width, framesIn(rendition.body))
		}
	}
}

func TestGifOfOneFrameIsAStill(t *testing.T) {
	got := convert(t, "max-edge=2560&quality=82", banner(t, 200, 100, 1))
	expectWebP(t, got, 200, 100)
	if framesIn(got.body) != 0 {
		t.Fatal("a still came out animated")
	}
}

func TestBannerWithABrokenFrameIsServedAsItsFirst(t *testing.T) {
	source := banner(t, 200, 100, 3)
	last := bytes.LastIndexByte(source, 0x2C)
	source[last+10+6] = 12 // past the two-colour table, an LZW code size no GIF has
	got := convert(t, "max-edge=2560&quality=82", source)
	expectWebP(t, got, 200, 100)
	if framesIn(got.body) != 0 {
		t.Fatal("a banner with a broken frame came out animated")
	}
}

func TestAnimationOverTheFrameLimitIsRefused(t *testing.T) {
	got := convert(t, "max-edge=2560&quality=82", banner(t, 4, 4, 1001))
	if got.status != http.StatusUnprocessableEntity || !bytes.Contains(got.body, []byte("too many frames")) {
		t.Fatalf("answered %d: %s", got.status, got.body)
	}
}

// A 65535x65535 screen: refused before 17 GB is allocated.
func TestAnimationClaimingTooManyPixelsIsRefusedFromItsHeader(t *testing.T) {
	source := banner(t, 4, 4, 2)
	binary.LittleEndian.PutUint16(source[6:], 65535)
	binary.LittleEndian.PutUint16(source[8:], 65535)
	if got := convert(t, "max-edge=2560&quality=82", source); got.status != http.StatusUnprocessableEntity {
		t.Fatalf("answered %d: %s", got.status, got.body)
	}
}
