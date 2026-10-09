//go:build linux

package paint

import (
	"log/slog"
	"os"
	"strconv"
	"strings"
)

// WarnIfConntrack logs how to exempt the pings when netfilter tracks connections on this host:
// every pixel is a new destination and so a new conntrack entry, which fills the table and then
// drops pings and the host's own new connections alike. Frames skip netfilter and need no
// exemption.
func WarnIfConntrack() {
	b, err := os.ReadFile("/proc/sys/net/netfilter/nf_conntrack_count")
	if err != nil {
		return
	}
	if n, _ := strconv.Atoi(strings.TrimSpace(string(b))); n == 0 {
		return
	}
	slog.Warn("the firewall tracks every ping as a connection, which can fill its table; exempt them once with: " +
		"sudo ip6tables -t raw -A OUTPUT -p ipv6-icmp --icmpv6-type echo-request -j CT --notrack")
}
