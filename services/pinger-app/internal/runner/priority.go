package runner

import (
	"log/slog"

	"golang.org/x/net/icmp"
)

// lowEffortTrafficClass is DSCP CS1 in the IPv6 traffic class byte (DSCP sits above the two ECN
// bits). Routers that queue by class, and Wi-Fi's background access category, send it after
// everything else, so the member's other traffic goes first where the network honours it.
const lowEffortTrafficClass = 8 << 2

// markLowPriority tags every ping on c as low-effort traffic. An OS that refuses the option still
// sends the pings, just unmarked.
func markLowPriority(c *icmp.PacketConn) {
	p := c.IPv6PacketConn()
	if p == nil {
		return
	}
	if err := p.SetTrafficClass(lowEffortTrafficClass); err != nil {
		slog.Debug("mark pings low priority", "err", err)
	}
}
