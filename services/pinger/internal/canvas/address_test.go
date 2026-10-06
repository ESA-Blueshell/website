package canvas

import (
	"net/netip"
	"testing"
)

func TestAddressMatchesTheSpecExample(t *testing.T) {
	prefix, err := ParsePrefix("2001:db8:b317:a000::/64")
	if err != nil {
		t.Fatal(err)
	}

	got := prefix.Address(Pixel{X: 25, Y: 25, R: 0xff, G: 0xd1, B: 0x00, A: 0xff})

	want := netip.MustParseAddr("2001:db8:b317:a000:0019:0019:00d1:ffff")
	if got != want {
		t.Fatalf("got %s, want %s", got, want)
	}
}

func TestAddressPutsBlueBeforeGreenAndRedBeforeAlpha(t *testing.T) {
	prefix, _ := ParsePrefix("2001:db8:b317:a000::/64")

	got := prefix.Address(Pixel{X: 3839, Y: 2159, R: 0x11, G: 0x22, B: 0x33, A: 0x44})

	want := netip.MustParseAddr("2001:db8:b317:a000:eff:86f:3322:1144")
	if got != want {
		t.Fatalf("got %s, want %s", got, want)
	}
}

func TestParsePrefixAcceptsTheFormsTheEventPageMightAnnounce(t *testing.T) {
	for _, in := range []string{
		"2001:db8:b317:a000::/64",
		"2001:db8:b317:a000::",
		"2001:db8:b317:a000",
		" 2001:db8:b317:a000::/64 ",
	} {
		prefix, err := ParsePrefix(in)
		if err != nil {
			t.Errorf("%q: %v", in, err)
			continue
		}
		if prefix.String() != "2001:db8:b317:a000::/64" {
			t.Errorf("%q: got %s", in, prefix)
		}
	}
}

func TestParsePrefixRefusesWhatCannotCarryAPixel(t *testing.T) {
	for _, in := range []string{
		"",
		"not an address",
		"10.0.0.0/8",
		"2001:db8::/48",
		"2001:db8:b317:a000::1/64",
		"2001:db8:b317:a000::1",
	} {
		if _, err := ParsePrefix(in); err == nil {
			t.Errorf("%q: accepted", in)
		}
	}
}
