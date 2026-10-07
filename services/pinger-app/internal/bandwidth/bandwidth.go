// Package bandwidth turns a send rate into an approximate uplink cost. It is approximate on
// purpose: it counts the IP and ICMP bytes the sender actually puts on the wire but ignores
// link-layer framing, so the real cost is a little higher. The UI labels the readout an estimate.
package bandwidth

import "github.com/ESA-Blueshell/website/services/pinger/paint"

// ipv6HeaderBytes is the fixed IPv6 header with no extension headers. The sender emits plain echo
// requests, so no extension headers apply.
const ipv6HeaderBytes = 40

// BytesPerPacket is the estimated on-wire size of one ping: the IPv6 header plus the ICMPv6 echo
// request the sender really emits. Deriving the ICMP part from the sender keeps the two from
// drifting if the payload ever changes.
var BytesPerPacket = ipv6HeaderBytes + paint.EchoRequestSize()

// Mbps is the approximate uplink megabits per second for a rate in packets per second. A non-
// positive rate is no traffic, so it is zero.
func Mbps(pps int) float64 {
	if pps <= 0 {
		return 0
	}
	return float64(pps) * float64(BytesPerPacket) * 8 / 1_000_000
}
