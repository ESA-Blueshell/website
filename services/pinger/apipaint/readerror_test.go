package apipaint

import (
	"context"
	"errors"
	"image"
	"strings"
	"testing"
)

// failingSource fails its descriptor or image reads on demand, so a test can watch the poller name
// why the sender has nothing to paint.
type failingSource struct {
	fakeSource
	descErr, imageErr error
}

func (f *failingSource) Descriptor(ctx context.Context) (Descriptor, error) {
	if f.descErr != nil {
		return Descriptor{}, f.descErr
	}
	return f.fakeSource.Descriptor(ctx)
}

func (f *failingSource) Image(ctx context.Context, url string) (image.Image, error) {
	if f.imageErr != nil {
		return nil, f.imageErr
	}
	return f.fakeSource.Image(ctx, url)
}

// A machine that cannot reach the api idles on zero settings, so the reason has to be readable.
func TestPollerKeepsTheLastReadErrorUntilARead(t *testing.T) {
	src := &failingSource{descErr: errors.New("dial tcp: connection refused")}
	src.desc = descWith(100, plc("/a.png", 0))
	p := NewPoller(src, 0, &pixelSink{}, nil)

	p.poll(context.Background())
	if got := p.ReadError(); !strings.Contains(got, "connection refused") {
		t.Fatalf("ReadError() = %q after a failed read", got)
	}

	src.descErr = nil
	p.poll(context.Background())
	if got := p.ReadError(); got != "" {
		t.Fatalf("ReadError() = %q after a good read", got)
	}
}

func TestPollerReportsAnImageItCannotFetch(t *testing.T) {
	src := &failingSource{imageErr: errors.New("status 403")}
	src.desc = descWith(100, plc("/a.png", 0))
	p := NewPoller(src, 0, &pixelSink{}, nil)

	p.poll(context.Background())
	if got := p.ReadError(); !strings.Contains(got, "status 403") {
		t.Fatalf("ReadError() = %q after a failed image fetch", got)
	}
}
