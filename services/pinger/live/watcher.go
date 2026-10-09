// Package live watches the SNTPings livestream and tells the sender which of its pixels the canvas
// no longer shows, so it repaints only what others drew over.
package live

import (
	"bufio"
	"bytes"
	"context"
	"errors"
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
	// and the sender falls back to repainting every pixel. Segments land every two seconds.
	staleAfter = 8 * time.Second
	// pollInterval is how often the playlist is read, so a new segment is fetched within this of
	// landing.
	pollInterval = 500 * time.Millisecond
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

// Run follows the stream until ctx ends: it polls the playlist, and each time a new segment lands it
// decodes that segment alone and keeps its last frame, the newest picture the feed has.
func (w *Watcher) Run(ctx context.Context, url string) {
	f := newFeed(url)
	tick := time.NewTicker(pollInterval)
	defer tick.Stop()
	for {
		if err := w.step(ctx, f); err != nil && ctx.Err() == nil {
			slog.Warn("livestream", "err", err)
		}
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
		}
	}
}

func (w *Watcher) step(ctx context.Context, f *feed) error {
	seg, err := f.next(ctx)
	if err != nil || seg == nil {
		return err
	}
	frame, err := lastFrame(ctx, seg)
	if err != nil {
		return err
	}
	w.SetFrame(&Frame{W: frameW, H: frameH, RGB: frame, At: w.now()})
	return nil
}

// lastFrame decodes one fMP4 segment (init section first) and returns its final frame as RGB24.
// ffmpeg reads only its stdin, so nothing in the feed can point it at a file or another host.
func lastFrame(ctx context.Context, segment []byte) ([]byte, error) {
	cmd := exec.CommandContext(ctx, "ffmpeg", "-loglevel", "error", "-protocol_whitelist", "pipe",
		"-f", "mp4", "-i", "pipe:0", "-an",
		"-vf", "scale="+strconv.Itoa(frameW)+":"+strconv.Itoa(frameH),
		"-f", "rawvideo", "-pix_fmt", "rgb24", "pipe:1")
	cmd.Stdin = bytes.NewReader(segment)
	out, err := cmd.StdoutPipe()
	if err != nil {
		return nil, err
	}
	if err := cmd.Start(); err != nil {
		return nil, err
	}
	r := bufio.NewReaderSize(out, 1<<20)
	cur, next := make([]byte, frameW*frameH*3), make([]byte, frameW*frameH*3)
	frames := 0
	for {
		if _, err := io.ReadFull(r, next); err != nil {
			break
		}
		cur, next = next, cur
		frames++
	}
	if err := cmd.Wait(); err != nil && frames == 0 {
		return nil, err
	}
	if frames == 0 {
		return nil, errors.New("segment held no frame")
	}
	return cur, nil
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
