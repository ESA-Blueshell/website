package convert

import (
	"bytes"
	"encoding/binary"
	"errors"
	"image"
	"image/color"
	"image/gif"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
)

var (
	red         = color.NRGBA{R: 255, A: 255}
	green       = color.NRGBA{G: 255, A: 255}
	blue        = color.NRGBA{B: 255, A: 255}
	transparent = color.NRGBA{}
)

// gifFrameOf is a frame of one colour over rect, with a transparent first palette entry.
func gifFrameOf(rect image.Rectangle, c color.NRGBA) *image.Paletted {
	m := image.NewPaletted(rect, color.Palette{color.NRGBA{}, red, green, blue})
	for y := rect.Min.Y; y < rect.Max.Y; y++ {
		for x := rect.Min.X; x < rect.Max.X; x++ {
			m.Set(x, y, c)
		}
	}
	return m
}

func gifOf(t *testing.T, g *gif.GIF) []byte {
	t.Helper()
	var b bytes.Buffer
	if err := gif.EncodeAll(&b, g); err != nil {
		t.Fatal(err)
	}
	return b.Bytes()
}

// threeFrames is a 40x20 GIF: red, then green over its left half, then blue over its right half,
// shown for 50ms, the 100ms a delay of none stands for, and 200ms.
func threeFrames(t *testing.T, disposal byte) []byte {
	t.Helper()
	return gifOf(t, &gif.GIF{
		Image: []*image.Paletted{
			gifFrameOf(image.Rect(0, 0, 40, 20), red),
			gifFrameOf(image.Rect(0, 0, 20, 20), green),
			gifFrameOf(image.Rect(20, 0, 40, 20), blue),
		},
		Delay:    []int{5, 0, 20},
		Disposal: []byte{gif.DisposalNone, disposal, gif.DisposalNone},
	})
}

// played is an animated WebP as it is shown: every frame's canvas, and how long it is shown.
func played(t *testing.T, data []byte) ([]*image.NRGBA, []int) {
	t.Helper()
	a, err := webpAnimation(data)
	if err != nil {
		t.Fatalf("not an animation: %v", err)
	}
	canvas := image.NewNRGBA(image.Rect(0, 0, a.width, a.height))
	var shown []*image.NRGBA
	var durations []int
	for _, f := range a.frames {
		m, err := f.decode()
		if err != nil {
			t.Fatal(err)
		}
		paint(canvas, m, f.rect.Min, f.blend)
		shown = append(shown, &image.NRGBA{Pix: bytes.Clone(canvas.Pix), Stride: canvas.Stride, Rect: canvas.Rect})
		durations = append(durations, f.duration)
		if f.dispose == disposeBackground {
			rows(canvas, f.rect, nil, func(row, _ []byte) []byte { clear(row); return nil })
		}
	}
	return shown, durations
}

func assertColour(t *testing.T, m *image.NRGBA, x, y int, want color.NRGBA) {
	t.Helper()
	have := m.NRGBAAt(x, y)
	near := func(a, b uint8) bool { return a-b < 48 || b-a < 48 } // lossy output drifts
	if want.A == 0 && near(have.A, 0) {
		return
	}
	if !near(have.R, want.R) || !near(have.G, want.G) || !near(have.B, want.B) || !near(have.A, want.A) {
		t.Fatalf("pixel %d,%d is %v, want %v", x, y, have, want)
	}
}

func TestGifKeepsItsFramesAndTheirDurations(t *testing.T) {
	got, err := Convert(threeFrames(t, gif.DisposalNone), Target{MaxEdge: 2560, Lossless: true, Quality: 75}, roomy)
	if err != nil {
		t.Fatal(err)
	}
	if got.Frames != 3 || got.Width != 40 || got.Height != 20 || got.Unanimated != nil {
		t.Fatalf("got %d frames at %dx%d (%v)", got.Frames, got.Width, got.Height, got.Unanimated)
	}
	shown, durations := played(t, got.WebP)
	if len(shown) != 3 || durations[0] != 50 || durations[1] != 100 || durations[2] != 200 {
		t.Fatalf("got %d frames shown for %v", len(shown), durations)
	}
	assertColour(t, shown[0], 30, 10, red)
	assertColour(t, shown[1], 5, 10, green)
	assertColour(t, shown[1], 30, 10, red)
	assertColour(t, shown[2], 5, 10, green)
	assertColour(t, shown[2], 30, 10, blue)
}

func TestGifDisposalIsReplayed(t *testing.T) {
	for name, c := range map[string]struct {
		disposal byte
		left     color.NRGBA
	}{
		"to the background": {gif.DisposalBackground, transparent},
		"to the previous":   {gif.DisposalPrevious, red},
	} {
		t.Run(name, func(t *testing.T) {
			got, err := Convert(threeFrames(t, c.disposal), Target{MaxEdge: 2560, Lossless: true, Quality: 75}, roomy)
			if err != nil {
				t.Fatal(err)
			}
			shown, _ := played(t, got.WebP)
			assertColour(t, shown[2], 5, 10, c.left)
			assertColour(t, shown[2], 30, 10, blue)
		})
	}
}

// Two frames that each put back what was under them: the second reuses the first's saved rows.
func TestRestoreToPreviousTwiceKeepsTheCanvasUnderneath(t *testing.T) {
	data := gifOf(t, &gif.GIF{
		Image: []*image.Paletted{
			gifFrameOf(image.Rect(0, 0, 40, 20), red),
			gifFrameOf(image.Rect(0, 0, 20, 20), green),
			gifFrameOf(image.Rect(0, 0, 10, 10), blue),
			gifFrameOf(image.Rect(30, 0, 40, 20), green),
		},
		Delay:    []int{10, 10, 10, 10},
		Disposal: []byte{gif.DisposalNone, gif.DisposalPrevious, gif.DisposalPrevious, gif.DisposalNone},
	})
	got, err := Convert(data, Target{MaxEdge: 2560, Lossless: true, Quality: 75}, roomy)
	if err != nil {
		t.Fatal(err)
	}
	shown, _ := played(t, got.WebP)
	assertColour(t, shown[2], 5, 5, blue)
	assertColour(t, shown[3], 5, 5, red)
	assertColour(t, shown[3], 15, 15, red)
	assertColour(t, shown[3], 35, 10, green)
}

// A GIF whose frames carry no graphic control extension: every frame is shown for the tenth of a
// second a browser gives it, and nothing is disposed.
func TestGifWithoutControlExtensionsIsShownAtATenthOfASecond(t *testing.T) {
	data := threeFrames(t, gif.DisposalBackground)
	bare := make([]byte, 0, len(data))
	for i := 0; i < len(data); i++ {
		if i+8 <= len(data) && data[i] == 0x21 && data[i+1] == 0xF9 && data[i+2] == 4 && data[i+7] == 0 {
			i += 7
			continue
		}
		bare = append(bare, data[i])
	}
	got, err := Convert(bare, Target{MaxEdge: 2560, Lossless: true, Quality: 75}, roomy)
	if err != nil {
		t.Fatal(err)
	}
	shown, durations := played(t, got.WebP)
	if len(shown) != 3 || durations[0] != 100 || durations[2] != 100 {
		t.Fatalf("got %d frames shown for %v", len(shown), durations)
	}
	assertColour(t, shown[2], 5, 10, green)
}

func TestAnimatedMasterIsBroughtUnderItsCeiling(t *testing.T) {
	got, err := Convert(threeFrames(t, gif.DisposalNone), Target{MaxEdge: 20, Quality: 82}, roomy)
	if err != nil {
		t.Fatal(err)
	}
	if got.Width != 20 || got.Height != 10 || got.Frames != 3 {
		t.Fatalf("got %d frames at %dx%d", got.Frames, got.Width, got.Height)
	}
	if w, h := sizeOf(t, got.WebP); w != 20 || h != 10 {
		t.Fatalf("encoded at %dx%d", w, h)
	}
}

// A rendition is drawn from the stored master, an animated WebP, and keeps its frames.
func TestAnimatedMasterRendersAnimatedAtEveryWidth(t *testing.T) {
	master, err := Convert(threeFrames(t, gif.DisposalBackground), Target{MaxEdge: 2560, Quality: 82}, roomy)
	if err != nil {
		t.Fatal(err)
	}
	got, err := Convert(master.WebP, Target{Width: 20, Height: 10, Quality: 82}, roomy)
	if err != nil {
		t.Fatal(err)
	}
	shown, durations := played(t, got.WebP)
	if got.Frames != 3 || len(shown) != 3 || durations[1] != 100 {
		t.Fatalf("got %d frames shown for %v", len(shown), durations)
	}
	assertColour(t, shown[0], 15, 5, red)
	assertColour(t, shown[2], 2, 5, transparent)
	assertColour(t, shown[2], 15, 5, blue)
}

func TestTransparencyOfALossyAnimationSurvives(t *testing.T) {
	g := &gif.GIF{
		Image: []*image.Paletted{gifFrameOf(image.Rect(0, 0, 32, 32), red), gifFrameOf(image.Rect(0, 0, 32, 32), red)},
		Delay: []int{10, 10},
	}
	g.Image[0].SetColorIndex(0, 0, 0)
	g.Image[1].SetColorIndex(31, 31, 0)
	master, err := Convert(gifOf(t, g), Target{MaxEdge: 2560, Quality: 82}, roomy)
	if err != nil {
		t.Fatal(err)
	}
	again, err := Convert(master.WebP, Target{Width: 32, Height: 32, Quality: 82}, roomy)
	if err != nil {
		t.Fatal(err)
	}
	shown, _ := played(t, again.WebP)
	assertColour(t, shown[0], 0, 0, transparent)
	assertColour(t, shown[0], 16, 16, red)
}

func TestGifOfOneFrameIsAStillOfItsScreen(t *testing.T) {
	data := gifOf(t, &gif.GIF{
		Image:  []*image.Paletted{gifFrameOf(image.Rect(10, 0, 20, 10), red)},
		Delay:  []int{0},
		Config: image.Config{ColorModel: color.Palette{color.NRGBA{}, red, green, blue}, Width: 30, Height: 10},
	})
	got, err := Convert(data, Target{MaxEdge: 2560, Lossless: true, Quality: 75}, roomy)
	if err != nil {
		t.Fatal(err)
	}
	if got.Frames != 1 || got.Unanimated != nil || got.Width != 30 {
		t.Fatalf("got %d frames at %d wide (%v)", got.Frames, got.Width, got.Unanimated)
	}
	if bytes.Contains(got.WebP, []byte("ANIM")) {
		t.Fatal("a still came out animated")
	}
}

func TestGifFrameOutsideItsScreenGrowsTheCanvas(t *testing.T) {
	data := threeFrames(t, gif.DisposalNone)
	binary.LittleEndian.PutUint16(data[6:], 10)
	got, err := Convert(data, Target{MaxEdge: 2560, Quality: 82}, roomy)
	if err != nil || got.Width != 40 || got.Height != 20 {
		t.Fatalf("got %dx%d, %v", got.Width, got.Height, err)
	}
}

func TestAnimationWhoseLaterFrameWillNotDecodeIsAStillOfItsFirst(t *testing.T) {
	broken := threeFrames(t, gif.DisposalNone)
	third := bytes.Index(broken, []byte{0x2C, 20, 0, 0, 0, 20, 0, 20, 0, 0x81})
	if third < 0 {
		t.Fatal("the fixture has moved")
	}
	broken[third+22] = 12 // past the colour table, an LZW code size no GIF has

	got, err := Convert(broken, Target{MaxEdge: 2560, Quality: 82}, roomy)
	if err != nil {
		t.Fatal(err)
	}
	if got.Frames != 1 || got.Unanimated == nil {
		t.Fatalf("got %d frames (%v)", got.Frames, got.Unanimated)
	}
}

func TestGifCutShortIsAStillOfItsFirstFrame(t *testing.T) {
	data := threeFrames(t, gif.DisposalNone)
	second := bytes.Index(data, []byte{0x2C, 0, 0, 0, 0, 20, 0, 20, 0})
	for name, cut := range map[string][]byte{
		"in an extension":     data[:second-3],
		"in a descriptor":     data[:second+5],
		"in its data":         data[:second+14],
		"on an unknown block": append(bytes.Clone(data[:second]), 0x99),
	} {
		got, err := Convert(cut, Target{MaxEdge: 2560, Quality: 82}, roomy)
		if err != nil || got.Frames != 1 || !errors.Is(got.Unanimated, errMalformed) {
			t.Errorf("%s: got %d frames (%v), %v", name, got.Frames, got.Unanimated, err)
		}
	}
}

func TestGifWithoutItsTrailerKeepsEveryFrame(t *testing.T) {
	data := threeFrames(t, gif.DisposalNone)
	got, err := Convert(data[:len(data)-1], Target{MaxEdge: 2560, Quality: 82}, roomy)
	if err != nil || got.Frames != 3 {
		t.Fatalf("got %d frames, %v", got.Frames, err)
	}
}

// A control extension of the wrong length is not one the decoder can trust, so its frame takes the
// defaults rather than a delay read from the wrong bytes.
func TestGifControlExtensionOfTheWrongLengthIsIgnored(t *testing.T) {
	data := threeFrames(t, gif.DisposalNone)
	first := bytes.Index(data, []byte{0x21, 0xF9, 4})
	long := append(append(bytes.Clone(data[:first+7]), 1, 0), data[first+7:]...)
	got, err := Convert(long, Target{MaxEdge: 2560, Quality: 82}, roomy)
	if err != nil {
		t.Fatal(err)
	}
	if _, durations := played(t, got.WebP); durations[0] != 100 {
		t.Fatalf("the first frame is shown for %dms", durations[0])
	}
}

func TestGifThatIsNotOneIsRefused(t *testing.T) {
	data := threeFrames(t, gif.DisposalNone)
	table := bytes.Clone(data)
	table[10] = 0x87
	far := bytes.Clone(data)
	first := bytes.Index(far, []byte{0x2C, 0, 0, 0, 0, 40, 0})
	binary.LittleEndian.PutUint16(far[first+1:], 65535)
	low := bytes.Clone(data)
	binary.LittleEndian.PutUint16(low[first+3:], 65535)
	for name, bad := range map[string][]byte{
		"shorter than a header":         data[:12],
		"a colour table past its end":   table,
		"a frame past the largest edge": far,
		"a frame below the lowest edge": low,
		"no frames":                     []byte("GIF89a\x01\x00\x01\x00\x00\x00\x00;"),
	} {
		if _, err := Convert(bad, Target{MaxEdge: 2560, Quality: 82}, roomy); !errors.Is(err, ErrUndecodable) {
			t.Errorf("%s: got %v", name, err)
		}
	}
}

func TestAnimationWhoseFirstFrameWillNotDecodeIsRefused(t *testing.T) {
	broken := threeFrames(t, gif.DisposalNone)
	first := bytes.Index(broken, []byte{0x2C, 0, 0, 0, 0, 40, 0, 20, 0, 0x81})
	broken[first+22] = 12 // an LZW code size no GIF has
	if _, err := Convert(broken, Target{MaxEdge: 2560, Quality: 82}, roomy); !errors.Is(err, ErrUndecodable) {
		t.Fatalf("got %v", err)
	}
}

func TestAnimationOverItsLimitsIsRefusedBeforeItIsDecoded(t *testing.T) {
	data := threeFrames(t, gif.DisposalNone)
	for name, c := range map[string]struct {
		limits Limits
		want   error
	}{
		"too many frames":             {Limits{MaxPixels: 1_000, MaxFrames: 2, MaxAnimationPixels: 1_000_000}, ErrTooManyFrames},
		"too many frames at its size": {Limits{MaxPixels: 1_000, MaxFrames: 10, MaxAnimationPixels: 2_399}, ErrTooManyFrames},
		"too large a canvas":          {Limits{MaxPixels: 799, MaxFrames: 10, MaxAnimationPixels: 1_000_000}, ErrTooManyPixels},
	} {
		if _, err := Convert(data, Target{MaxEdge: 2560, Quality: 82}, c.limits); !errors.Is(err, c.want) {
			t.Errorf("%s: got %v, want %v", name, err, c.want)
		}
	}
}

// riffOf wraps chunks in a WebP container.
func riffOf(chunks ...[]byte) []byte {
	body := bytes.Join(chunks, nil)
	out := binary.LittleEndian.AppendUint32([]byte("RIFF"), uint32(4+len(body))) //nolint:gosec // a test fixture
	return append(append(out, "WEBP"...), body...)
}

func chunk(id string, payload []byte) []byte {
	out := binary.LittleEndian.AppendUint32([]byte(id), uint32(len(payload))) //nolint:gosec // a test fixture
	out = append(out, payload...)
	if len(payload)%2 == 1 {
		out = append(out, 0)
	}
	return out
}

func u24le(n int) []byte { return appendU24(nil, n) }

func vp8x(width, height int) []byte {
	return chunk("VP8X", append(append([]byte{0x12, 0, 0, 0}, u24le(width-1)...), u24le(height-1)...))
}

func anmf(x, y, width, height, duration int, flags byte, bitstream []byte) []byte {
	header := append(append(append(append(u24le(x/2), u24le(y/2)...), u24le(width-1)...), u24le(height-1)...), u24le(duration)...)
	return chunk("ANMF", append(append(header, flags), bitstream...))
}

// bitstreamOf is a picture's frame data: the chunks of its lossless WebP after the container.
func bitstreamOf(t *testing.T, m image.Image) []byte {
	t.Helper()
	got, err := Convert(pngOf(t, m), Target{MaxEdge: 2560, Lossless: true, Quality: 75}, roomy)
	if err != nil {
		t.Fatal(err)
	}
	return got.WebP[12:]
}

func solid(width, height int, c color.NRGBA) *image.NRGBA {
	m := image.NewNRGBA(image.Rect(0, 0, width, height))
	for i := 0; i < len(m.Pix); i += 4 {
		copy(m.Pix[i:], []byte{c.R, c.G, c.B, c.A})
	}
	return m
}

func TestWebPFramesBlendAndDisposeAsTheyAsk(t *testing.T) {
	half := color.NRGBA{B: 255, A: 128}
	data := riffOf(
		vp8x(20, 10),
		chunk("ANIM", []byte{0, 0, 0, 0, 0, 0}),
		anmf(0, 0, 20, 10, 70, 0x02, bitstreamOf(t, solid(20, 10, red))),
		anmf(10, 0, 10, 10, 80, 0x01, bitstreamOf(t, solid(10, 10, half))),
		anmf(0, 0, 10, 10, 90, 0x02, bitstreamOf(t, solid(10, 10, green))),
	)
	got, err := Convert(data, Target{MaxEdge: 2560, Lossless: true, Quality: 75}, roomy)
	if err != nil {
		t.Fatal(err)
	}
	shown, durations := played(t, got.WebP)
	if len(shown) != 3 || durations[0] != 70 || durations[2] != 90 {
		t.Fatalf("got %d frames shown for %v", len(shown), durations)
	}
	assertColour(t, shown[1], 15, 5, color.NRGBA{R: 127, B: 128, A: 255})
	assertColour(t, shown[2], 5, 5, green)
	assertColour(t, shown[2], 15, 5, transparent)
}

func TestBlendingOntoTransparencyKeepsTheSourceColour(t *testing.T) {
	canvas := image.NewNRGBA(image.Rect(0, 0, 1, 1))
	paint(canvas, solid(1, 1, color.NRGBA{R: 200, A: 100}), image.Point{}, true)
	assertColour(t, canvas, 0, 0, color.NRGBA{R: 200, A: 100})
}

func TestWebPFrameTableThatBreaksOffIsAStillOfItsFirstFrame(t *testing.T) {
	first := anmf(0, 0, 4, 4, 100, 0, bitstreamOf(t, solid(4, 4, red)))
	for name, data := range map[string][]byte{
		"a chunk past the end":     append(riffOf(vp8x(4, 4), first), "ANMF\xff\x00\x00\x00"...),
		"a frame header cut short": riffOf(vp8x(4, 4), first, chunk("ANMF", make([]byte, 10))),
		"a frame off the canvas":   riffOf(vp8x(4, 4), first, anmf(2, 2, 4, 4, 100, 0, nil)),
	} {
		binary.LittleEndian.PutUint32(data[4:], uint32(len(data)-8)) //nolint:gosec // a test fixture
		got, err := Convert(data, Target{MaxEdge: 2560, Quality: 82}, roomy)
		if err != nil || got.Frames != 1 || !errors.Is(got.Unanimated, errMalformed) {
			t.Errorf("%s: got %d frames (%v), %v", name, got.Frames, got.Unanimated, err)
		}
	}
}

func TestWebPFrameLargerThanItsHeaderSaysIsNotDecoded(t *testing.T) {
	data := riffOf(
		vp8x(4, 4),
		anmf(0, 0, 4, 4, 100, 0, bitstreamOf(t, solid(4, 4, red))),
		anmf(0, 0, 4, 4, 100, 0, bitstreamOf(t, solid(400, 400, red))),
	)
	got, err := Convert(data, Target{MaxEdge: 2560, Quality: 82}, roomy)
	if err != nil || got.Frames != 1 || got.Unanimated == nil {
		t.Fatalf("got %d frames (%v), %v", got.Frames, got.Unanimated, err)
	}
}

func TestAnimationLongerThanWebPCanSayIsAStillOfItsFirstFrame(t *testing.T) {
	bitstream := bitstreamOf(t, solid(4, 4, red))
	chunks := [][]byte{vp8x(4, 4)}
	for range 130 {
		chunks = append(chunks, anmf(0, 0, 4, 4, 1<<24-1, 0, bitstream))
	}
	got, err := Convert(riffOf(chunks...), Target{MaxEdge: 2560, Quality: 82}, Limits{MaxPixels: 16, MaxFrames: 200, MaxAnimationPixels: 1 << 20})
	if err != nil || got.Frames != 1 || !errors.Is(got.Unanimated, errTooLong) {
		t.Fatalf("got %d frames (%v), %v", got.Frames, got.Unanimated, err)
	}
}

// A WebP with the extended header but no animation flag, as a lossy picture with transparency has,
// is a still.
func TestExtendedStillWebPIsConvertedAsAStill(t *testing.T) {
	master, err := Convert(pngOf(t, picture(64, 32, true)), Target{MaxEdge: 2560, Quality: 82}, roomy)
	if err != nil || !bytes.HasPrefix(master.WebP[12:], []byte("VP8X")) {
		t.Fatalf("the fixture has no extended header: %v", err)
	}
	got, err := Convert(master.WebP, Target{Width: 32, Height: 16, Quality: 82}, roomy)
	if err != nil || got.Frames != 1 || got.Unanimated != nil {
		t.Fatalf("got %d frames (%v), %v", got.Frames, got.Unanimated, err)
	}
}

func TestAnimatedWebPWithNoFramesIsRefused(t *testing.T) {
	for name, data := range map[string][]byte{
		"no frames":   riffOf(vp8x(4, 4), chunk("ANIM", make([]byte, 6))),
		"cut short":   riffOf(vp8x(4, 4))[:25],
		"bad frame 1": riffOf(vp8x(4, 4), chunk("ANMF", make([]byte, 3))),
	} {
		if _, err := Convert(data, Target{MaxEdge: 2560, Quality: 82}, roomy); !errors.Is(err, ErrUndecodable) {
			t.Errorf("%s: got %v", name, err)
		}
	}
}

// What the api wrote before the service took animations over: every frame the whole canvas,
// blending with nothing. Skipped where webpmux is not installed; the image's check stage has it.
func TestMasterTheApiWroteIsRead(t *testing.T) {
	webpmux, err := exec.LookPath("webpmux")
	if err != nil {
		t.Skip("webpmux is not installed")
	}
	dir := t.TempDir()
	args := []string{}
	for i, c := range []color.NRGBA{red, green} {
		path := filepath.Join(dir, string(rune('a'+i))+".webp")
		got, err := Convert(pngOf(t, solid(8, 8, c)), Target{MaxEdge: 2560, Quality: 82}, roomy)
		if err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(path, got.WebP, 0o600); err != nil {
			t.Fatal(err)
		}
		args = append(args, "-frame", path, "+150+0+0+0-b")
	}
	out := filepath.Join(dir, "master.webp")
	if output, err := exec.CommandContext(t.Context(), webpmux, append(args, "-loop", "0", "-o", out)...).CombinedOutput(); err != nil {
		t.Fatalf("webpmux: %v: %s", err, output)
	}
	master, err := os.ReadFile(out)
	if err != nil {
		t.Fatal(err)
	}
	got, err := Convert(master, Target{Width: 4, Height: 4, Quality: 82}, roomy)
	if err != nil || got.Frames != 2 || got.Unanimated != nil {
		t.Fatalf("got %d frames (%v), %v", got.Frames, got.Unanimated, err)
	}
	shown, durations := played(t, got.WebP)
	assertColour(t, shown[1], 2, 2, green)
	if durations[0] != 150 {
		t.Fatalf("shown for %v", durations)
	}
}

type failingEncoder struct{ addFails bool }

func (f failingEncoder) add(*image.NRGBA, int) error {
	if f.addFails {
		return errAnimationRefused
	}
	return nil
}
func (failingEncoder) assemble(int) ([]byte, error) { return nil, errAnimationRefused }
func (failingEncoder) close()                       {}

func TestEncoderThatRefusesIsAnError(t *testing.T) {
	data := threeFrames(t, gif.DisposalNone)
	if _, err := Convert(data, Target{Quality: 82}, roomy); !errors.Is(err, errAnimationRefused) {
		t.Errorf("no size: got %v", err)
	}
	original := newFrameEncoder
	t.Cleanup(func() { newFrameEncoder = original })
	for _, addFails := range []bool{true, false} {
		newFrameEncoder = func(int, int, bool, float32) (frameEncoder, error) { return failingEncoder{addFails}, nil }
		if _, err := Convert(data, Target{MaxEdge: 2560, Quality: 82}, roomy); !errors.Is(err, errAnimationRefused) {
			t.Errorf("add fails %v: got %v", addFails, err)
		}
	}
}

func TestStillOfAFirstFrameLibwebpWillNotDrawIsAnError(t *testing.T) {
	data := gifOf(t, &gif.GIF{Image: []*image.Paletted{gifFrameOf(image.Rect(0, 0, 4, 4), red)}, Delay: []int{0}})
	if _, err := Convert(data, Target{Quality: 82}, roomy); err == nil || errors.Is(err, ErrUndecodable) {
		t.Fatalf("got %v", err)
	}
}

func TestEncoderRefusesTimeRunningBackwards(t *testing.T) {
	e, err := newAnimationEncoder(4, 4, false, 82)
	if err != nil {
		t.Fatal(err)
	}
	defer e.close()
	canvas := image.NewNRGBA(image.Rect(0, 0, 4, 4))
	if err := e.add(canvas, 100); err != nil {
		t.Fatal(err)
	}
	if err := e.add(canvas, 50); !errors.Is(err, errAnimationRefused) {
		t.Fatalf("got %v", err)
	}
}

func TestEncoderWithNoFramesHasNothingToAssemble(t *testing.T) {
	e, err := newAnimationEncoder(4, 4, true, 75)
	if err != nil {
		t.Fatal(err)
	}
	defer e.close()
	if _, err := e.assemble(100); !errors.Is(err, errAnimationRefused) {
		t.Fatalf("got %v", err)
	}
}

func FuzzConvert(f *testing.F) {
	f.Add(threeFramesFor(f))
	f.Add(riffOf(vp8x(4, 4), anmf(0, 0, 4, 4, 100, 0x03, nil)))
	f.Fuzz(func(_ *testing.T, data []byte) {
		_, _ = Convert(data, Target{Width: 8, Height: 8, Quality: 50}, Limits{MaxPixels: 1 << 16, MaxFrames: 8, MaxAnimationPixels: 1 << 18})
	})
}

func threeFramesFor(f *testing.F) []byte {
	var b bytes.Buffer
	if err := gif.EncodeAll(&b, &gif.GIF{
		Image: []*image.Paletted{gifFrameOf(image.Rect(0, 0, 4, 4), red), gifFrameOf(image.Rect(1, 1, 3, 3), blue)},
		Delay: []int{1, 2}, Disposal: []byte{gif.DisposalPrevious, gif.DisposalBackground},
	}); err != nil {
		f.Fatal(err)
	}
	return b.Bytes()
}
