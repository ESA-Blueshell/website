package live

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

const playlist = `#EXTM3U
#EXT-X-VERSION:7
#EXT-X-TARGETDURATION:2
#EXT-X-MAP:URI="init.mp4"
#EXTINF:2.000000,
seg1.m4s
#EXTINF:2.000000,
seg2.m4s
`

func TestParsePlaylistFindsTheInitAndTheNewestSegment(t *testing.T) {
	initURI, newest := parsePlaylist([]byte(playlist))
	if initURI != "init.mp4" || newest != "seg2.m4s" {
		t.Fatalf("got %q %q, want init.mp4 seg2.m4s", initURI, newest)
	}
}

func TestFeedHandsOutEachNewSegmentOnceWithItsInit(t *testing.T) {
	list := playlist
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/live.m3u8":
			w.Write([]byte(list))
		default:
			w.Write([]byte(strings.TrimPrefix(r.URL.Path, "/") + ";"))
		}
	}))
	defer srv.Close()
	f := newFeed(srv.URL + "/live.m3u8")

	got, err := f.next(context.Background())
	if err != nil || string(got) != "init.mp4;seg2.m4s;" {
		t.Fatalf("first = %q, %v", got, err)
	}
	if again, _ := f.next(context.Background()); again != nil {
		t.Fatalf("the same segment came twice: %q", again)
	}
	list += "#EXTINF:2.000000,\nseg3.m4s\n"
	if got, _ := f.next(context.Background()); string(got) != "init.mp4;seg3.m4s;" {
		t.Fatalf("next = %q", got)
	}
}

func TestFeedKeepsSegmentsOnThePlaylistHost(t *testing.T) {
	f := newFeed("https://tv.example/live/a.m3u8")
	if got := f.resolve("https://evil.example/x.m4s"); got != "https://tv.example/x.m4s" {
		t.Fatalf("resolve = %q", got)
	}
	if got := f.resolve("seg.m4s"); got != "https://tv.example/live/seg.m4s" {
		t.Fatalf("resolve = %q", got)
	}
}
