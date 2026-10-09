package runner

import (
	"testing"

	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv6"
)

func TestMarkLowPrioritySetsTheTrafficClass(t *testing.T) {
	c, err := icmp.ListenPacket("udp6", "::1")
	if err != nil {
		t.Skipf("no unprivileged ICMPv6 socket here: %v", err)
	}
	defer c.Close()

	markLowPriority(c)

	tc, err := c.IPv6PacketConn().TrafficClass()
	if err != nil {
		t.Skipf("traffic class unreadable here: %v", err)
	}
	if tc != lowEffortTrafficClass {
		t.Errorf("traffic class = %d, want %d", tc, lowEffortTrafficClass)
	}
}

func TestListenMarksThePlainSocket(t *testing.T) {
	s, err := listen("udp6")
	if err != nil {
		t.Skipf("no unprivileged ICMPv6 socket here: %v", err)
	}
	defer s.Close()
	c, ok := s.(*icmp.PacketConn)
	if !ok {
		t.Skip("this OS wraps the socket; ListenFresh runs the mark on each one it opens")
	}
	tc, err := c.IPv6PacketConn().TrafficClass()
	if err != nil {
		t.Skipf("traffic class unreadable here: %v", err)
	}
	if tc != lowEffortTrafficClass {
		t.Errorf("traffic class = %d, want %d", tc, lowEffortTrafficClass)
	}
}

func TestDiscardInboundBlocksEveryICMPType(t *testing.T) {
	c, err := icmp.ListenPacket("udp6", "::1")
	if err != nil {
		t.Skipf("no unprivileged ICMPv6 socket here: %v", err)
	}
	defer c.Close()

	discardInbound(c)

	f, err := c.IPv6PacketConn().ICMPFilter()
	if err != nil {
		t.Skipf("ICMPv6 filter unsupported on this socket: %v", err)
	}
	for _, typ := range []ipv6.ICMPType{ipv6.ICMPTypeEchoReply, ipv6.ICMPTypeDestinationUnreachable, ipv6.ICMPTypeTimeExceeded} {
		if !f.WillBlock(typ) {
			t.Errorf("%v passes the filter", typ)
		}
	}
}
