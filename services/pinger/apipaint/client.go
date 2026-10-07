// Package apipaint reads the paint job the api owns: the prefix, rate and the box an admin dragged,
// plus the image to paint. The pinger service and the helper exe both use it, so they paint the
// same thing from the same source of truth.
package apipaint

import (
	"context"
	"encoding/json"
	"fmt"
	"image"
	"io"
	"net/http"
	"strings"
	"time"

	// Register the decoders for the formats the api serves an uploaded image as.
	_ "image/jpeg"
	_ "image/png"

	_ "golang.org/x/image/webp"
)

// Descriptor is the paint job as GET /pinger/paint returns it. Every field is a value, so two
// descriptors compare with ==, which is how the poller notices a change.
type Descriptor struct {
	Prefix   string `json:"prefix"`
	RatePPS  int    `json:"ratePps"`
	OriginX  int    `json:"originX"`
	OriginY  int    `json:"originY"`
	Width    int    `json:"width"`
	Height   int    `json:"height"`
	ImageURL string `json:"imageUrl"`
}

// Source hands back the current descriptor and fetches the image it points at. Split from the HTTP
// client so the poller can be tested without a server.
type Source interface {
	Descriptor(ctx context.Context) (Descriptor, error)
	Image(ctx context.Context, url string) (image.Image, error)
}

// Client reads the paint job from an api base URL, e.g. https://esa-blueshell.nl/api.
type Client struct {
	base string
	hc   *http.Client
}

func NewClient(base string) *Client {
	return &Client{base: strings.TrimSuffix(base, "/"), hc: &http.Client{Timeout: 10 * time.Second}}
}

func (c *Client) Descriptor(ctx context.Context) (Descriptor, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.base+"/pinger/paint", nil)
	if err != nil {
		return Descriptor{}, err
	}
	req.Header.Set("Accept", "application/json")
	resp, err := c.hc.Do(req)
	if err != nil {
		return Descriptor{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return Descriptor{}, fmt.Errorf("paint descriptor: status %d", resp.StatusCode)
	}
	var d Descriptor
	if err := json.NewDecoder(resp.Body).Decode(&d); err != nil {
		return Descriptor{}, fmt.Errorf("paint descriptor: %w", err)
	}
	return d, nil
}

// Image fetches the image the descriptor points at. The URL is the path the api returns, which is
// resolved against the api origin — the same way the frontend resolves a public file.
func (c *Client) Image(ctx context.Context, url string) (image.Image, error) {
	full := url
	if strings.HasPrefix(url, "/") {
		full = c.base + url
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, full, nil)
	if err != nil {
		return nil, err
	}
	resp, err := c.hc.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("paint image: status %d", resp.StatusCode)
	}
	// Cap the read so a wrong URL cannot stream forever. The upload ceiling is 10 MB; allow a bit
	// more for the webp master before giving up.
	img, _, err := image.Decode(io.LimitReader(resp.Body, 24<<20))
	if err != nil {
		return nil, fmt.Errorf("paint image: %w", err)
	}
	return img, nil
}
