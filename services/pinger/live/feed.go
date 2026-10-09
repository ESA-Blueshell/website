package live

import (
	"bufio"
	"bytes"
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// feed reads the HLS playlist and hands out each new segment once, init section first, skipping
// the player's habit of starting a few segments back.
type feed struct {
	playlist *url.URL
	hc       *http.Client
	lastSeg  string
	initURI  string
	init     []byte
}

func newFeed(playlist string) *feed {
	u, _ := url.Parse(playlist)
	return &feed{playlist: u, hc: &http.Client{Timeout: 5 * time.Second}}
}

// next returns the newest segment prefixed with its init section, or nil when nothing new landed.
func (f *feed) next(ctx context.Context) ([]byte, error) {
	body, err := f.get(ctx, f.playlist.String())
	if err != nil {
		return nil, err
	}
	initURI, seg := parsePlaylist(body)
	if seg == "" || seg == f.lastSeg {
		return nil, nil
	}
	if initURI != f.initURI || f.init == nil {
		init, err := f.get(ctx, f.resolve(initURI))
		if err != nil {
			return nil, err
		}
		f.initURI, f.init = initURI, init
	}
	data, err := f.get(ctx, f.resolve(seg))
	if err != nil {
		return nil, err
	}
	f.lastSeg = seg
	return append(append([]byte(nil), f.init...), data...), nil
}

// resolve keeps a segment on the playlist's own host: an absolute URI elsewhere is refused by
// resolving only its path.
func (f *feed) resolve(ref string) string {
	r, err := url.Parse(ref)
	if err != nil {
		return f.playlist.String()
	}
	r.Scheme, r.Host, r.User = "", "", nil
	return f.playlist.ResolveReference(r).String()
}

func (f *feed) get(ctx context.Context, u string) ([]byte, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
	if err != nil {
		return nil, err
	}
	resp, err := f.hc.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("%s: status %d", u, resp.StatusCode)
	}
	return io.ReadAll(io.LimitReader(resp.Body, 64<<20))
}

// parsePlaylist returns the init section's URI and the newest segment's URI.
func parsePlaylist(body []byte) (initURI, newest string) {
	sc := bufio.NewScanner(bytes.NewReader(body))
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		switch {
		case strings.HasPrefix(line, "#EXT-X-MAP:"):
			if i := strings.Index(line, `URI="`); i >= 0 {
				rest := line[i+5:]
				if j := strings.IndexByte(rest, '"'); j >= 0 {
					initURI = rest[:j]
				}
			}
		case line != "" && !strings.HasPrefix(line, "#"):
			newest = line
		}
	}
	return initURI, newest
}
