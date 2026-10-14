//go:build integration

// Package integration drives a running imaging container over HTTP, as the api does. It is the
// image under test, not the code: the static binary on its shell-less base, run read-only, as
// nobody, with no capabilities. IMAGING_URL names it; see the README for how CI starts it.
package integration

import (
	"bytes"
	"encoding/binary"
	"hash/crc32"
	"image"
	"image/color"
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

// answer is what the service said: its status, its headers and its body, read in full.
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

// expectWebP checks the answer is a WebP of the size its headers name.
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

// A PNG whose header claims 20000 by 20000 and whose body holds nothing: refused from the
// header, before a bitmap of 1.6 GB could be allocated for it.
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
