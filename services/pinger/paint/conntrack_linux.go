//go:build linux

package paint

import (
	"log/slog"
	"os"
	"strconv"
	"strings"
)

// ConntrackNotice is what a member reads when netfilter tracks every ping as a connection.
const ConntrackNotice = "The firewall tracks every ping as a connection, which fills its table, drops pings and " +
	"costs CPU. Exempt them once with: " +
	"sudo ip6tables -t raw -A OUTPUT -p ipv6-icmp --icmpv6-type echo-request -j CT --notrack"

// WarnIfConntrack logs ConntrackNotice and returns it when netfilter tracks connections on this
// host, or returns "" when it does not: every pixel is a new destination and so a new conntrack
// entry, which fills the table and then drops pings and the host's own new connections alike.
// Frames skip netfilter, so call it only on a socket path.
func WarnIfConntrack() string {
	b, err := os.ReadFile("/proc/sys/net/netfilter/nf_conntrack_count")
	if err != nil {
		return ""
	}
	if n, _ := strconv.Atoi(strings.TrimSpace(string(b))); n == 0 {
		return ""
	}
	slog.Warn(ConntrackNotice)
	return ConntrackNotice
}
