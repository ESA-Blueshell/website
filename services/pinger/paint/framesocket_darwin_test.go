//go:build darwin

package paint

import (
	"bytes"
	"encoding/binary"
	"errors"
	"net"
	"testing"

	"golang.org/x/net/ipv6"
	"golang.org/x/sys/unix"
)

func testTemplate(t *testing.T) *frameTemplate {
	t.Helper()
	f := make([]byte, frameHead+8)
	copy(f[:6], []byte{2, 0, 0, 0, 0, 1})
	copy(f[6:12], []byte{2, 0, 0, 0, 0, 2})
	binary.BigEndian.PutUint16(f[12:], 0x86dd)
	ip := f[ethHeader:]
	ip[0], ip[6], ip[7] = 0x60, 58, 64
	copy(ip[8:24], net.ParseIP("2001:db8:1::1"))
	copy(ip[24:40], net.ParseIP("2001:db8:b317:a000::1"))
	return newTemplate(f, 7)
}

// frameConn is a FrameSocket whose /dev/bpf is one end of a datagram socketpair, so each write
// arrives as one datagram on the returned peer.
func frameConn(t *testing.T, batch bool) (*FrameSocket, int) {
	t.Helper()
	fds, err := unix.Socketpair(unix.AF_UNIX, unix.SOCK_DGRAM, 0)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = unix.Close(fds[0]); _ = unix.Close(fds[1]) })
	_ = unix.SetsockoptInt(fds[0], unix.SOL_SOCKET, unix.SO_SNDBUF, 1<<16)
	_ = unix.SetsockoptInt(fds[1], unix.SOL_SOCKET, unix.SO_RCVBUF, 1<<20)
	tpl := testTemplate(t)
	route := &frameRoute{}
	route.current.Store(tpl)
	return &FrameSocket{fd: fds[0], iface: tpl.ifindex, route: route, batch: batch}, fds[1]
}

func pixelMessages(n int) []ipv6.Message {
	echo := echoRequest()
	ms := make([]ipv6.Message, n)
	for i := range ms {
		ip := net.ParseIP("2001:db8:b317:a000::")
		ip[14], ip[15] = byte(i>>8), byte(i)
		ms[i] = ipv6.Message{Buffers: [][]byte{echo}, Addr: &net.IPAddr{IP: ip}}
	}
	return ms
}

func readDatagrams(t *testing.T, fd int) [][]byte {
	t.Helper()
	var out [][]byte
	buf := make([]byte, 1<<16)
	for {
		n, _, err := unix.Recvfrom(fd, buf, unix.MSG_DONTWAIT)
		if errors.Is(err, unix.EAGAIN) {
			return out
		}
		if err != nil {
			t.Fatal(err)
		}
		out = append(out, append([]byte(nil), buf[:n]...))
	}
}

func checkFrames(t *testing.T, frames [][]byte, ms []ipv6.Message) {
	t.Helper()
	if len(frames) != len(ms) {
		t.Fatalf("got %d frames, want %d", len(frames), len(ms))
	}
	want := make([]byte, frameHead+len(ms[0].Buffers[0]))
	for i, f := range frames {
		testTemplate(t).frame(want, destination(ms[i].Addr), ms[i].Buffers[0])
		if !bytes.Equal(f, want) {
			t.Fatalf("frame %d is\n%x, want\n%x", i, f, want)
		}
	}
}

func TestBatchRecordsReadBackAsFrames(t *testing.T) {
	frames := [][]byte{bytes.Repeat([]byte{1}, 62), bytes.Repeat([]byte{2}, 70), bytes.Repeat([]byte{3}, 63)}
	var buf []byte
	for _, f := range frames {
		rec := make([]byte, batchHeader+len(f)+3)
		n := putBatchHeader(rec, len(f))
		copy(rec[batchHeader:], f)
		buf = append(buf, rec[:n]...)
	}
	if len(buf)%4 != 0 {
		t.Fatalf("batch of %d bytes does not end on a word", len(buf))
	}
	got := bpfFrames(buf)
	if len(got) != len(frames) {
		t.Fatalf("read back %d frames, want %d", len(got), len(frames))
	}
	for i := range frames {
		if !bytes.Equal(got[i], frames[i]) {
			t.Fatalf("frame %d is %x, want %x", i, got[i], frames[i])
		}
	}
}

func TestFrameSocketWritesABatchInOneWrite(t *testing.T) {
	s, peer := frameConn(t, true)
	ms := pixelMessages(batch)
	n, err := s.WriteBatch(ms, 0)
	if err != nil || n != len(ms) {
		t.Fatalf("WriteBatch = %d, %v", n, err)
	}
	writes := readDatagrams(t, peer)
	if len(writes) != 1 {
		t.Fatalf("%d writes for one batch, want 1", len(writes))
	}
	checkFrames(t, bpfFrames(writes[0]), ms)
}

func TestFrameSocketWritesFramesOneByOneWithoutBatching(t *testing.T) {
	s, peer := frameConn(t, false)
	ms := pixelMessages(8)
	if n, err := s.WriteBatch(ms, 0); err != nil || n != len(ms) {
		t.Fatalf("WriteBatch = %d, %v", n, err)
	}
	checkFrames(t, readDatagrams(t, peer), ms)
}

// A batch the device refuses as malformed or oversized goes out frame by frame, and so does every
// later one.
func TestFrameSocketFallsBackWhenTheBatchIsRefused(t *testing.T) {
	s, peer := frameConn(t, true)
	// A datagram past the send buffer fails EMSGSIZE, as bpf_movein fails a batch it cannot take.
	if err := unix.SetsockoptInt(s.fd, unix.SOL_SOCKET, unix.SO_SNDBUF, 256); err != nil {
		t.Fatal(err)
	}
	ms := pixelMessages(16)
	if n, err := s.WriteBatch(ms, 0); err != nil || n != len(ms) {
		t.Fatalf("WriteBatch = %d, %v", n, err)
	}
	checkFrames(t, readDatagrams(t, peer), ms)
	if s.batch {
		t.Fatal("still batching after the device refused a batch")
	}
}
