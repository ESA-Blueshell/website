package paint

import (
	"net"
	"testing"

	"golang.org/x/sys/unix"
)

// ListenFast must hand back a socket that sends whether or not /dev/bpf is open to this user.
func TestListenFastOpensASocketEitherWay(t *testing.T) {
	s, err := ListenFast("udp6", nil)
	if err != nil {
		t.Skipf("no ICMPv6 socket here: %v", err)
	}
	defer s.Close()
	fd, err := openBPF()
	if err != nil {
		if _, ok := s.(*FreshSocket); !ok {
			t.Fatalf("got %T without /dev/bpf access, want *FreshSocket", s)
		}
		return
	}
	_ = unix.Close(fd)
	if _, ok := s.(*LinkSocket); !ok {
		t.Fatalf("got %T with /dev/bpf access, want *LinkSocket", s)
	}
}

func TestLinkSocketRefusesNonIPDestinations(t *testing.T) {
	s := &LinkSocket{fd: -1, closed: make(chan struct{})}
	if _, err := s.WriteTo(echoRequest(), &net.UnixAddr{}); err == nil {
		t.Fatal("sent to a unix address")
	}
}
