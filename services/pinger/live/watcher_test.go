package live

import (
	"testing"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
)

// square paints the 2x2 canvas block that stream pixel (u, v) shows, in one colour.
func square(u, v int, r, g, b uint8) []canvas.Pixel {
	var out []canvas.Pixel
	for dy := range 2 {
		for dx := range 2 {
			out = append(out, canvas.Pixel{X: uint16(2*u + dx), Y: uint16(2*v + dy), R: r, G: g, B: b, A: 0xff})
		}
	}
	return out
}

func frame(at time.Time) *Frame {
	return &Frame{W: frameW, H: frameH, RGB: make([]byte, frameW*frameH*3), At: at}
}

func paintAt(f *Frame, u, v int, r, g, b byte) {
	i := (v*f.W + u) * 3
	f.RGB[i], f.RGB[i+1], f.RGB[i+2] = r, g, b
}

func TestWatcherCannotTellWithoutAFreshFrame(t *testing.T) {
	now := time.Unix(1000, 0)
	w := NewWatcher()
	w.now = func() time.Time { return now }
	px := square(10, 10, 200, 0, 0)

	if _, ok := w.Damaged(1, px); ok {
		t.Fatal("vouched without any frame")
	}
	w.SetFrame(frame(now.Add(-staleAfter - time.Second)))
	if _, ok := w.Damaged(1, px); ok {
		t.Fatal("vouched on a stale frame")
	}
}

func TestWatcherListsOnlyBlocksTheStreamShowsOffColour(t *testing.T) {
	now := time.Unix(1000, 0)
	w := NewWatcher()
	w.now = func() time.Time { return now }
	kept := square(10, 10, 200, 100, 50)
	lost := square(20, 10, 200, 100, 50)
	px := append(append([]canvas.Pixel{}, kept...), lost...)

	f := frame(now)
	paintAt(f, 10, 10, 220, 90, 60) // within tolerance of ours
	paintAt(f, 20, 10, 0, 0, 255)   // someone drew over it
	w.SetFrame(f)

	got, ok := w.Damaged(1, px)
	if !ok {
		t.Fatal("fresh frame not used")
	}
	if len(got) != 4 || got[0] != 4 {
		t.Fatalf("damaged %v, want the four pixels of the second block", got)
	}
}

func TestWatcherAlwaysRepaintsBlocksItCannotPredict(t *testing.T) {
	now := time.Unix(1000, 0)
	w := NewWatcher()
	w.now = func() time.Time { return now }
	partial := square(5, 5, 10, 10, 10)[:3]
	translucent := square(6, 5, 10, 10, 10)
	translucent[0].A = 0x80
	offCanvas := []canvas.Pixel{{X: canvas.Width + 1, Y: 0, A: 0xff}}
	px := append(append(append([]canvas.Pixel{}, partial...), translucent...), offCanvas...)

	f := frame(now)
	paintAt(f, 5, 5, 10, 10, 10)
	paintAt(f, 6, 5, 10, 10, 10)
	w.SetFrame(f)

	got, _ := w.Damaged(1, px)
	if len(got) != len(px) {
		t.Fatalf("damaged %d of %d, want all of them", len(got), len(px))
	}
}
