// Package canvas maps the logo onto the SNTPings canvas and each pixel onto the address that paints it.
package canvas

import (
	"errors"
	"fmt"
	"net/netip"
	"strings"
)

const (
	Width  = 3840
	Height = 2160
)

// Pixel is one canvas coordinate and the colour to paint there.
type Pixel struct {
	X, Y       uint16
	R, G, B, A uint8
}

// Prefix is the /64 the event announces; the low 64 bits of each address carry one pixel.
type Prefix struct {
	high [8]byte
}

// ParsePrefix accepts `a:b:c:d::/64`, `a:b:c:d::` or the bare `a:b:c:d`.
func ParsePrefix(s string) (Prefix, error) {
	s = strings.TrimSpace(s)
	s = strings.TrimSuffix(s, "/64")
	if !strings.Contains(s, "::") && strings.Count(s, ":") == 3 {
		s += "::"
	}
	addr, err := netip.ParseAddr(s)
	if err != nil {
		return Prefix{}, fmt.Errorf("prefix %q: %w", s, err)
	}
	if !addr.Is6() || addr.Is4In6() {
		return Prefix{}, fmt.Errorf("prefix %q is not IPv6", s)
	}
	b := addr.As16()
	for _, v := range b[8:] {
		if v != 0 {
			return Prefix{}, errors.New("prefix must be a /64 with its low 64 bits zero")
		}
	}
	var p Prefix
	copy(p.high[:], b[:8])
	return p, nil
}

// Address lays the pixel out as <prefix>:<X>:<Y>:<B><G>:<R><A>.
func (p Prefix) Address(px Pixel) netip.Addr {
	var b [16]byte
	copy(b[:8], p.high[:])
	b[8], b[9] = byte(px.X>>8), byte(px.X)
	b[10], b[11] = byte(px.Y>>8), byte(px.Y)
	b[12], b[13] = px.B, px.G
	b[14], b[15] = px.R, px.A
	return netip.AddrFrom16(b)
}

func (p Prefix) IsZero() bool { return p == Prefix{} }

func (p Prefix) String() string {
	if p.IsZero() {
		return ""
	}
	var b [16]byte
	copy(b[:8], p.high[:])
	return netip.PrefixFrom(netip.AddrFrom16(b), 64).String()
}
