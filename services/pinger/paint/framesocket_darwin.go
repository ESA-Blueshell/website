//go:build darwin

package paint

import (
	"crypto/rand"
	"encoding/binary"
	"errors"
	"log/slog"
	"net"
	"sync"
	"time"

	"golang.org/x/net/bpf"
	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv6"
	"golang.org/x/sys/unix"
)

// frameSockets is how many /dev/bpf descriptors OpenFrameSockets opens: a write costs a few
// microseconds, so two carry the rate cap, and the sender's workers share them.
const frameSockets = 2

const (
	// biocSBatchWrite is XNU's private BIOCSBATCHWRITE, _IOW('B', 143, int): one write then carries
	// many frames, each behind a bpf_hdr, instead of one. Kernels without it refuse the ioctl.
	biocSBatchWrite = 0x8004428f
	// batchHeader is sizeof(struct bpf_hdr) with the 32-bit timeval 64-bit userland uses.
	batchHeader = 20
)

// FrameSocket writes echo requests as whole Ethernet frames through /dev/bpf, past the IP stack and
// any network content filter. On a Mac whose security software filters sockets, every ICMP datagram
// waits on that filter, which holds the whole machine to a few thousand new destinations a second
// however many sockets share the work. A destination whose route is not Ethernet, such as a VPN,
// goes through a FreshSocket.
type FrameSocket struct {
	fd     int
	iface  int32
	probe  *icmp.PacketConn
	kernel *FreshSocket
	dgram  bool
	route  *frameRoute
	mu     sync.Mutex
	frame  []byte
	// batch is set while fd takes many frames per write, as checked on the bound interface.
	batch bool
	buf   []byte
}

// OpenFrameSockets opens frameSockets frame sockets sharing one learned route, when this user may
// write /dev/bpf. bypass is Linux's alone. prepare runs on each kernel ICMP socket, whose traffic
// class the frames copy off the probe.
func OpenFrameSockets(_ bool, prepare func(*icmp.PacketConn)) ([]*FrameSocket, error) {
	route := &frameRoute{}
	out := make([]*FrameSocket, 0, frameSockets)
	for range frameSockets {
		s, err := openFrameSocket(route, prepare)
		if err != nil {
			CloseSockets(out)
			return nil, err
		}
		out = append(out, s)
	}
	return out, nil
}

func openFrameSocket(route *frameRoute, prepare func(*icmp.PacketConn)) (*FrameSocket, error) {
	fd, err := openBPF()
	if err != nil {
		return nil, err
	}
	// Writing needs no capture: keep nothing, so passing traffic is never copied here.
	if err := setFilter(fd, []bpf.Instruction{bpf.RetConstant{Val: 0}}); err != nil {
		_ = unix.Close(fd)
		return nil, err
	}
	network, dgram := "udp6", true
	probe, err := icmp.ListenPacket(network, "::")
	if err != nil {
		network, dgram = "ip6:ipv6-icmp", false
		if probe, err = icmp.ListenPacket(network, "::"); err != nil {
			_ = unix.Close(fd)
			return nil, err
		}
	}
	if prepare != nil {
		prepare(probe)
	}
	kernel, err := ListenFresh(network, prepare)
	if err != nil {
		_ = probe.Close()
		_ = unix.Close(fd)
		return nil, err
	}
	return &FrameSocket{fd: fd, probe: probe, kernel: kernel, dgram: dgram, route: route}, nil
}

// WriteBatch writes ms as frames, every message routed as the first one is, and stops at the first
// write the interface refuses.
func (s *FrameSocket) WriteBatch(ms []ipv6.Message, _ int) (int, error) {
	if len(ms) == 0 {
		return 0, nil
	}
	t := s.route.template(destination(ms[0].Addr), s.probe)
	if t == nil {
		return s.writeKernel(ms)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.iface != t.ifindex {
		ifc, err := net.InterfaceByIndex(int(t.ifindex))
		if err != nil {
			return 0, err
		}
		if err := bindBPF(s.fd, ifc.Name); err != nil {
			s.route.invalidate()
			return 0, err
		}
		s.iface = t.ifindex
		s.batch = batchWrites(s.fd, ifc.Name, t, destination(ms[0].Addr))
	}
	if s.batch {
		n, err := s.writeBatched(t, ms)
		if !errors.Is(err, unix.EINVAL) && !errors.Is(err, unix.EMSGSIZE) {
			return n, err
		}
		// A refused batch sent nothing: bpf_movein_batch frees the whole chain on any error. Left on,
		// batch mode would read each lone frame as a bpf_hdr.
		s.batch = false
		_ = unix.IoctlSetPointerInt(s.fd, biocSBatchWrite, 0)
		slog.Warn("writing one frame at a time: the batch write was refused", "err", err)
	}
	for i, m := range ms {
		if len(m.Buffers) != 1 || len(m.Buffers[0]) > maxICMPLen {
			return i, errors.New("a frame takes one echo request of at most 64 bytes")
		}
		f := s.frame[:0]
		if cap(f) < frameHead+maxICMPLen {
			f = make([]byte, 0, frameHead+maxICMPLen)
		}
		f = f[:frameHead+len(m.Buffers[0])]
		s.frame = f
		t.frame(f, destination(m.Addr), m.Buffers[0])
		if _, err := unix.Write(s.fd, f); err != nil {
			if errors.Is(err, unix.ENXIO) || errors.Is(err, unix.ENETDOWN) {
				s.route.invalidate()
			}
			return i, err
		}
	}
	return len(ms), nil
}

// writeBatched writes ms in one batch write. A message too large for a frame ends the batch there.
func (s *FrameSocket) writeBatched(t *frameTemplate, ms []ipv6.Message) (int, error) {
	need := len(ms) * (batchHeader + frameHead + maxICMPLen + 3)
	if cap(s.buf) < need {
		s.buf = make([]byte, need)
	}
	buf, at, n := s.buf[:need], 0, 0
	var bad error
	for _, m := range ms {
		if len(m.Buffers) != 1 || len(m.Buffers[0]) > maxICMPLen {
			bad = errors.New("a frame takes one echo request of at most 64 bytes")
			break
		}
		size := frameHead + len(m.Buffers[0])
		next := at + putBatchHeader(buf[at:], size)
		t.frame(buf[at+batchHeader:at+batchHeader+size], destination(m.Addr), m.Buffers[0])
		at = next
		n++
	}
	if n == 0 {
		return 0, bad
	}
	if _, err := unix.Write(s.fd, buf[:at]); err != nil {
		if errors.Is(err, unix.ENXIO) || errors.Is(err, unix.ENETDOWN) {
			s.route.invalidate()
		}
		return 0, err
	}
	return n, bad
}

// putBatchHeader writes into rec the bpf_hdr a batch write wants before a frame of n bytes, and
// returns the record's length: the next header starts on a 4-byte boundary.
func putBatchHeader(rec []byte, n int) int {
	end := (batchHeader + n + 3) &^ 3
	clear(rec[:batchHeader])
	clear(rec[batchHeader+n : end])
	binary.NativeEndian.PutUint32(rec[8:], uint32(n))
	binary.NativeEndian.PutUint32(rec[12:], uint32(n))
	binary.NativeEndian.PutUint16(rec[16:], batchHeader)
	return end
}

// batchWrites turns on batch writes for fd, bound to iface, when the kernel has them and a test
// batch of two probes to dst is seen leaving iface as written; otherwise fd keeps one frame a
// write. The check stops a kernel that reads the batch differently from sending garbage.
func batchWrites(fd int, iface string, t *frameTemplate, dst [16]byte) bool {
	if err := unix.IoctlSetPointerInt(fd, biocSBatchWrite, 1); err != nil {
		slog.Info("writing one frame at a time: no batch writes on this kernel", "err", err)
		return false
	}
	if err := checkBatch(fd, iface, t, dst); err != nil {
		_ = unix.IoctlSetPointerInt(fd, biocSBatchWrite, 0)
		slog.Warn("writing one frame at a time: a test batch did not leave as written", "err", err)
		return false
	}
	slog.Info("writing frames in batches", "iface", iface)
	return true
}

// checkBatch writes two probes to dst as one batch on fd and watches iface for both.
func checkBatch(fd int, iface string, t *frameTemplate, dst [16]byte) error {
	cfd, err := openBPF()
	if err != nil {
		return err
	}
	defer unix.Close(cfd)
	if err := captureProbes(cfd, iface); err != nil {
		return err
	}
	blen, err := unix.IoctlGetInt(cfd, unix.BIOCGBLEN)
	if err != nil {
		return err
	}
	var nonces [2][]byte
	size := frameHead + 8 + probeNonceLen
	buf := make([]byte, 2*(batchHeader+size+3))
	at := 0
	for i := range nonces {
		nonces[i] = make([]byte, probeNonceLen)
		_, _ = rand.Read(nonces[i])
		echo, err := (&icmp.Message{Type: ipv6.ICMPTypeEchoRequest, Body: &icmp.Echo{ID: 1, Seq: i + 1, Data: nonces[i]}}).Marshal(nil)
		if err != nil {
			return err
		}
		next := at + putBatchHeader(buf[at:], size)
		t.frame(buf[at+batchHeader:at+batchHeader+size], dst, echo)
		at = next
	}
	if _, err := unix.Write(fd, buf[:at]); err != nil {
		return err
	}
	var seen [2]bool
	rd := make([]byte, blen)
	for deadline := time.Now().Add(learnWait); time.Now().Before(deadline); {
		n, err := unix.Read(cfd, rd)
		if err != nil {
			continue
		}
		for _, f := range bpfFrames(rd[:n]) {
			for i, nonce := range nonces {
				seen[i] = seen[i] || isProbeFrame(f, dst, nonce)
			}
		}
		if seen[0] && seen[1] {
			return nil
		}
	}
	return errors.New("the test batch never left")
}

func (s *FrameSocket) writeKernel(ms []ipv6.Message) (int, error) {
	for i, m := range ms {
		dst := m.Addr
		if s.dgram {
			d := destination(dst)
			dst = &net.UDPAddr{IP: d[:]}
		}
		if _, err := s.kernel.WriteTo(m.Buffers[0], dst); err != nil {
			return i, err
		}
	}
	return len(ms), nil
}

func (s *FrameSocket) WriteTo(b []byte, dst net.Addr) (int, error) {
	if _, err := s.WriteBatch([]ipv6.Message{{Buffers: [][]byte{b}, Addr: dst}}, 0); err != nil {
		return 0, err
	}
	return len(b), nil
}

// ReadFrom reads the kernel fallback socket; replies to frames reach no socket.
func (s *FrameSocket) ReadFrom(b []byte) (int, net.Addr, error) { return s.kernel.ReadFrom(b) }

func (s *FrameSocket) SetReadDeadline(t time.Time) error { return s.kernel.SetReadDeadline(t) }

func (s *FrameSocket) Close() error {
	_ = unix.Close(s.fd)
	_ = s.probe.Close()
	return s.kernel.Close()
}

// WarnIfConntrack does nothing: only Linux's netfilter takes an entry per ping.
func WarnIfConntrack() string { return "" }
