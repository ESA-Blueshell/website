// Package live watches the SNTPings livestream and tells the sender which of its pixels the canvas
// no longer shows, so it repaints only what others drew over.
package live

import (
	"bufio"
	"context"
	"io"
	"log/slog"
	"os/exec"
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	"github.com/ESA-Blueshell/website/services/pinger/canvas"
)

// DefaultStreamURL is the event's own 1080p feed of the canvas.
const DefaultStreamURL = "https://tv.pings.utwente.io/1080p30_hls.m3u8"

const (
	frameW = 1920
	frameH = 1080
	// tolerance is the per-channel gap a stream pixel may have from ours and still count as ours.
	// The feed is half the canvas resolution and H.264, so exact matches never happen.
	tolerance = 48
	// staleAfter is how old the last frame may be before the watcher stops vouching for anything
	// and the sender falls back to repainting every pixel.
	staleAfter = 15 * time.Second
)

// Frame is one decoded picture of the canvas, RGB24 rows top to bottom.
type Frame struct {
	W, H int
	RGB  []byte
	At   time.Time
}

// Watcher holds the latest frame of the stream and diffs it against the pixels the sender paints.
type Watcher struct {
	frame atomic.Pointer[Frame]
	now   func() time.Time

	mu     sync.Mutex
	blocks *blockMap
}

func NewWatcher() *Watcher { return &Watcher{now: time.Now} }

// Available reports whether ffmpeg is on the PATH; without it there is no stream to watch.
func Available() bool {
	_, err := exec.LookPath("ffmpeg")
	return err == nil
}

// Run decodes the stream's keyframes until ctx ends, restarting ffmpeg when it drops.
func (w *Watcher) Run(ctx context.Context, url string) {
	for ctx.Err() == nil {
		if err := w.decode(ctx, url); err != nil && ctx.Err() == nil {
			slog.Warn("livestream", "err", err)
		}
		select {
		case <-ctx.Done():
		case <-time.After(5 * time.Second):
		}
	}
}

func (w *Watcher) decode(ctx context.Context, url string) error {
	// Keyframes only: every HLS segment opens on one, so that is a frame every two seconds for
	// almost no decoding.
	cmd := exec.CommandContext(ctx, "ffmpeg", "-loglevel", "error", "-skip_frame", "nokey", "-i", url, "-an",
		"-vf", "scale="+strconv.Itoa(frameW)+":"+strconv.Itoa(frameH), "-fps_mode", "passthrough",
		"-f", "rawvideo", "-pix_fmt", "rgb24", "pipe:1")
	out, err := cmd.StdoutPipe()
	if err != nil {
		return err
	}
	if err := cmd.Start(); err != nil {
		return err
	}
	r := bufio.NewReaderSize(out, 1<<20)
	for {
		buf := make([]byte, frameW*frameH*3)
		if _, err := io.ReadFull(r, buf); err != nil {
			_ = cmd.Wait()
			return err
		}
		w.SetFrame(&Frame{W: frameW, H: frameH, RGB: buf, At: w.now()})
	}
}

// SetFrame swaps in the latest picture of the canvas.
func (w *Watcher) SetFrame(f *Frame) { w.frame.Store(f) }

// Damaged answers which of pixels the last frame does not show, as indices into pixels. ok is
// false when there is no fresh frame, and the caller must then assume every pixel needs sending.
// gen identifies the pixels, so the stream-to-canvas mapping is built once per image.
func (w *Watcher) Damaged(gen uint64, pixels []canvas.Pixel) ([]int, bool) {
	f := w.frame.Load()
	if f == nil || w.now().Sub(f.At) > staleAfter {
		return nil, false
	}
	return w.blocksFor(gen, pixels, f.W, f.H).damaged(f), true
}

func (w *Watcher) blocksFor(gen uint64, pixels []canvas.Pixel, fw, fh int) *blockMap {
	w.mu.Lock()
	defer w.mu.Unlock()
	if b := w.blocks; b != nil && b.gen == gen && b.fw == fw && b.fh == fh {
		return b
	}
	w.blocks = newBlockMap(gen, pixels, fw, fh)
	return w.blocks
}

// blockMap groups our pixels by the stream pixel that shows them. A stream pixel covers a block of
// canvas pixels; only a block we paint whole and opaque has a colour we can predict.
type blockMap struct {
	gen    uint64
	fw, fh int
	blocks []block
}

type block struct {
	at      int // offset of the stream pixel in Frame.RGB
	r, g, b int // expected colour, the mean of our pixels in the block
	whole   bool
	members []int
}

func newBlockMap(gen uint64, pixels []canvas.Pixel, fw, fh int) *blockMap {
	// A canvas pixel's block is the stream pixel its top-left corner falls in.
	perBlock := (canvas.Width / fw) * (canvas.Height / fh)
	index := map[int]int{}
	m := &blockMap{gen: gen, fw: fw, fh: fh}
	sums := [][4]int{}
	for i, px := range pixels {
		u, v := int(px.X)*fw/canvas.Width, int(px.Y)*fh/canvas.Height
		at := (v*fw + u) * 3
		if u >= fw || v >= fh {
			at = -1 // off the canvas, so never whole and never read
		}
		k, ok := index[at]
		if !ok {
			k = len(m.blocks)
			index[at] = k
			m.blocks = append(m.blocks, block{at: at})
			sums = append(sums, [4]int{})
		}
		m.blocks[k].members = append(m.blocks[k].members, i)
		if px.A == 0xff {
			sums[k][0] += int(px.R)
			sums[k][1] += int(px.G)
			sums[k][2] += int(px.B)
			sums[k][3]++
		}
	}
	for k := range m.blocks {
		b := &m.blocks[k]
		n := sums[k][3]
		b.whole = b.at >= 0 && n == perBlock && len(b.members) == perBlock
		if n > 0 {
			b.r, b.g, b.b = sums[k][0]/n, sums[k][1]/n, sums[k][2]/n
		}
	}
	return m
}

// damaged lists every pixel in a block the frame shows off colour. A block we only partly cover,
// or cover with translucent pixels, mixes in colours we cannot know, so it always counts as damaged.
func (m *blockMap) damaged(f *Frame) []int {
	var out []int
	for _, b := range m.blocks {
		if b.whole && near(f.RGB[b.at], b.r) && near(f.RGB[b.at+1], b.g) && near(f.RGB[b.at+2], b.b) {
			continue
		}
		out = append(out, b.members...)
	}
	return out
}

func near(got byte, want int) bool {
	d := int(got) - want
	return d >= -tolerance && d <= tolerance
}
