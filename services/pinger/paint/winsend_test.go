package paint

import (
	"errors"
	"net"
	"os"
	"testing"
	"time"

	"golang.org/x/net/ipv6"
)

func TestIPv6DestTakesRawAndDatagramAddresses(t *testing.T) {
	ip := net.ParseIP("2001:db8::1")
	for _, dst := range []net.Addr{&net.IPAddr{IP: ip}, &net.UDPAddr{IP: ip}} {
		got, err := ipv6Dest(dst)
		if err != nil {
			t.Fatal(err)
		}
		if !net.IP(got[:]).Equal(ip) {
			t.Fatalf("got %v, want %v", net.IP(got[:]), ip)
		}
	}
}

func TestIPv6DestRefusesWhatIsNotIPv6(t *testing.T) {
	for _, dst := range []net.Addr{
		nil,
		&net.IPAddr{IP: net.ParseIP("192.0.2.1")},
		&net.IPAddr{IP: net.IP{1, 2, 3}},
		&net.TCPAddr{IP: net.ParseIP("2001:db8::1")},
	} {
		if _, err := ipv6Dest(dst); !errors.Is(err, errNotIPv6) {
			t.Fatalf("ipv6Dest(%v) = %v, want errNotIPv6", dst, err)
		}
	}
}

func TestEchoPayloadDropsTheHeader(t *testing.T) {
	if p := echoPayload(echoRequest()); len(p) != 0 {
		t.Fatalf("a payload-free echo carries %d payload bytes", len(p))
	}
	if p := echoPayload([]byte{0, 0, 0, 0, 0, 0, 0, 0, 7, 9}); string(p) != string([]byte{7, 9}) {
		t.Fatalf("payload %v", p)
	}
}

func TestSendAllSkipsALaterFailure(t *testing.T) {
	msgs := numbered(5)
	var tried []byte
	n, err := sendAll(msgs, func(b []byte, _ net.Addr) error {
		tried = append(tried, b[0])
		if b[0] == 3 {
			return errors.New("boom")
		}
		return nil
	})
	if n != 5 || err != nil || string(tried) != string([]byte{0, 1, 2, 3, 4}) {
		t.Fatalf("n=%d err=%v tried=%v", n, err, tried)
	}
}

func TestSendAllFailsAtOnceWhenTheFirstSendFails(t *testing.T) {
	boom := errors.New("boom")
	calls := 0
	n, err := sendAll(numbered(5), func([]byte, net.Addr) error { calls++; return boom })
	if n != 0 || !errors.Is(err, boom) || calls != 1 {
		t.Fatalf("n=%d err=%v calls=%d", n, err, calls)
	}
}

func numbered(n int) []ipv6.Message {
	msgs := make([]ipv6.Message, n)
	for i := range msgs {
		msgs[i].Buffers = [][]byte{{byte(i)}}
	}
	return msgs
}

func TestReadGateWaitsOutTheDeadline(t *testing.T) {
	g := newReadGate()
	start := time.Now()
	_ = g.SetReadDeadline(start.Add(30 * time.Millisecond))
	if _, _, err := g.ReadFrom(nil); !errors.Is(err, os.ErrDeadlineExceeded) {
		t.Fatalf("err = %v", err)
	}
	if waited := time.Since(start); waited < 25*time.Millisecond {
		t.Fatalf("returned after %v, before the deadline", waited)
	}
}

func TestReadGateWakesOnClose(t *testing.T) {
	g := newReadGate()
	done := make(chan error, 1)
	go func() { _, _, err := g.ReadFrom(nil); done <- err }()
	g.Close()
	g.Close()
	select {
	case err := <-done:
		if !errors.Is(err, net.ErrClosed) {
			t.Fatalf("err = %v", err)
		}
	case <-time.After(time.Second):
		t.Fatal("a read with no deadline did not wake on close")
	}
}
