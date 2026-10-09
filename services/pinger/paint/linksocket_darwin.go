package paint

import (
	"bytes"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"os"
	"sync"
	"time"
	"unsafe"

	"golang.org/x/net/bpf"
	"golang.org/x/net/icmp"
	"golang.org/x/sys/unix"
)

const (
	// headerTTL is how long a learned link header is trusted before a fresh probe checks it, so a
	// change of network, router or temporary address is picked up within it.
	headerTTL = 30 * time.Second
	// learnWait bounds how long a probe's frame is waited for before the /64 goes through the
	// kernel instead.
	learnWait = 500 * time.Millisecond
)

// ListenFast opens the fastest ICMPv6 sender this Mac permits: a LinkSocket when /dev/bpf can be
// written (root, or the access_bpf group Wireshark's ChmodBPF sets up), else a FreshSocket on
// network. prepare runs on every kernel socket either opens, the LinkSocket's probes included.
func ListenFast(network string, prepare func(*icmp.PacketConn)) (PacketSocket, error) {
	if s, err := listenLink(network, prepare); err == nil {
		return s, nil
	} else {
		slog.Debug("link-layer send unavailable, using ICMP sockets", "err", err)
	}
	return ListenFresh(network, prepare)
}

// LinkSocket writes whole Ethernet frames through /dev/bpf, past the IP stack and any network
// content filter. On a Mac whose security software filters sockets, every ICMP datagram waits on
// that filter, which holds a socket to a couple of thousand new destinations a second however
// many sockets share the work; frames written here never reach it. Each /64's link header is
// learned off one ping the kernel sends there, and a /64 the kernel sends somewhere other than an
// Ethernet interface, such as a VPN, falls back to a FreshSocket.
type LinkSocket struct {
	fd      int
	network string
	prepare func(*icmp.PacketConn)

	mu       sync.Mutex
	iface    string
	echo     []byte
	msg      *icmp.Message
	buf      []byte
	fallback PacketSocket

	closed chan struct{}
	once   sync.Once
}

func listenLink(network string, prepare func(*icmp.PacketConn)) (*LinkSocket, error) {
	fd, err := openBPF()
	if err != nil {
		return nil, err
	}
	if err := setFilter(fd, dropAll); err != nil {
		unix.Close(fd)
		return nil, err
	}
	return &LinkSocket{fd: fd, network: network, prepare: prepare, closed: make(chan struct{})}, nil
}

func (s *LinkSocket) WriteTo(b []byte, dst net.Addr) (int, error) {
	ip, err := addrIP(dst)
	if err != nil {
		return 0, err
	}
	h, ok := headers.get(ip, s.network, s.prepare)
	if !ok {
		return s.kernel(b, dst)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if h.iface != s.iface {
		if err := bindBPF(s.fd, h.iface); err != nil {
			return 0, err
		}
		s.iface = h.iface
	}
	if s.msg == nil || !bytes.Equal(s.echo, b) {
		if s.msg, err = icmp.ParseMessage(nextHeaderICMPv6, b); err != nil {
			return 0, err
		}
		s.echo = append(s.echo[:0], b...)
	}
	if s.buf, err = h.header.frame(s.buf, s.msg, ip); err != nil {
		return 0, err
	}
	if _, err := unix.Write(s.fd, s.buf); err != nil {
		return 0, err
	}
	return len(b), nil
}

// kernel sends through the socket path, for a /64 that has no link header.
func (s *LinkSocket) kernel(b []byte, dst net.Addr) (int, error) {
	s.mu.Lock()
	if s.fallback == nil {
		c, err := ListenFresh(s.network, s.prepare)
		if err != nil {
			s.mu.Unlock()
			return 0, err
		}
		s.fallback = c
	}
	c := s.fallback
	s.mu.Unlock()
	return c.WriteTo(b, dst)
}

// ReadFrom drains the kernel fallback once there is one. Replies to frames written through
// /dev/bpf reach no socket, so until then it waits a second, or for Close, and times out.
func (s *LinkSocket) ReadFrom(b []byte) (int, net.Addr, error) {
	s.mu.Lock()
	c := s.fallback
	s.mu.Unlock()
	if c != nil {
		return c.ReadFrom(b)
	}
	select {
	case <-s.closed:
		return 0, nil, net.ErrClosed
	case <-time.After(time.Second):
		return 0, nil, os.ErrDeadlineExceeded
	}
}

func (s *LinkSocket) SetReadDeadline(t time.Time) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.fallback != nil {
		return s.fallback.SetReadDeadline(t)
	}
	return nil
}

func (s *LinkSocket) Close() error {
	s.once.Do(func() { close(s.closed) })
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.fallback != nil {
		_ = s.fallback.Close()
	}
	return unix.Close(s.fd)
}

// learnedHeader is a /64's link header and the interface it goes out on; ok is false for a /64
// the kernel routes off Ethernet or that no probe came back for.
type learnedHeader struct {
	header linkHeader
	iface  string
	ok     bool
	at     time.Time
}

// headerCache holds one learned header per /64, shared by every LinkSocket so one probe serves
// them all. A stale entry is still used while one caller relearns it.
type headerCache struct {
	mu       sync.Mutex
	byPrefix map[[8]byte]*learnedHeader
	learning map[[8]byte]chan struct{}
}

var headers = headerCache{byPrefix: map[[8]byte]*learnedHeader{}, learning: map[[8]byte]chan struct{}{}}

func (c *headerCache) get(dst net.IP, network string, prepare func(*icmp.PacketConn)) (*learnedHeader, bool) {
	var key [8]byte
	copy(key[:], dst.To16())
	for {
		c.mu.Lock()
		h := c.byPrefix[key]
		if h != nil && time.Since(h.at) < headerTTL {
			c.mu.Unlock()
			return h, h.ok
		}
		wait, busy := c.learning[key]
		if busy && h != nil {
			c.mu.Unlock()
			return h, h.ok
		}
		if busy {
			c.mu.Unlock()
			<-wait
			continue
		}
		done := make(chan struct{})
		c.learning[key] = done
		c.mu.Unlock()

		learned := learn(dst, network, prepare)
		c.mu.Lock()
		c.byPrefix[key] = learned
		delete(c.learning, key)
		c.mu.Unlock()
		close(done)
		return learned, learned.ok
	}
}

// learn has the kernel ping dst and captures the frame it sends, which carries the route, the
// router's MAC and the source address the kernel picked for that /64.
func learn(dst net.IP, network string, prepare func(*icmp.PacketConn)) *learnedHeader {
	out := &learnedHeader{at: time.Now()}
	iface, err := egress(dst)
	if err != nil {
		slog.Info("link-layer send off for this prefix", "err", err)
		return out
	}
	fd, err := openBPF()
	if err != nil {
		return out
	}
	defer unix.Close(fd)
	if err := captureFrom(fd, iface.Name); err != nil {
		slog.Info("link-layer send off for this prefix", "iface", iface.Name, "err", err)
		return out
	}
	if err := probe(dst, network, prepare); err != nil {
		return out
	}
	blen, err := unix.IoctlGetInt(fd, unix.BIOCGBLEN)
	if err != nil {
		return out
	}
	buf := make([]byte, blen)
	for deadline := time.Now().Add(learnWait); time.Now().Before(deadline); {
		n, err := unix.Read(fd, buf)
		if err != nil && !errors.Is(err, unix.EINTR) && !errors.Is(err, unix.EAGAIN) {
			return out
		}
		for _, f := range bpfFrames(buf[:max(n, 0)]) {
			if h, ok := learnHeader(f, dst); ok {
				out.header, out.iface, out.ok = h, iface.Name, true
				slog.Info("sending through /dev/bpf", "iface", iface.Name, "source", h.source())
				return out
			}
		}
	}
	slog.Info("link-layer send off for this prefix: probe not seen", "iface", iface.Name)
	return out
}

// egress is the interface the kernel sends to dst from, found by the source address it picks; a
// UDP connect picks one without sending anything.
func egress(dst net.IP) (*net.Interface, error) {
	c, err := net.DialUDP("udp6", nil, &net.UDPAddr{IP: dst, Port: 9})
	if err != nil {
		return nil, err
	}
	src := c.LocalAddr().(*net.UDPAddr).IP
	_ = c.Close()
	ifs, err := net.Interfaces()
	if err != nil {
		return nil, err
	}
	for i := range ifs {
		addrs, _ := ifs[i].Addrs()
		for _, a := range addrs {
			if n, ok := a.(*net.IPNet); ok && n.IP.Equal(src) {
				if len(ifs[i].HardwareAddr) != 6 {
					return nil, fmt.Errorf("%s is not Ethernet", ifs[i].Name)
				}
				return &ifs[i], nil
			}
		}
	}
	return nil, fmt.Errorf("no interface holds %s", src)
}

func probe(dst net.IP, network string, prepare func(*icmp.PacketConn)) error {
	c, err := icmp.ListenPacket(network, "::")
	if err != nil {
		return err
	}
	defer c.Close()
	if prepare != nil {
		prepare(c)
	}
	var to net.Addr = &net.IPAddr{IP: dst}
	if network == "udp6" {
		to = &net.UDPAddr{IP: dst}
	}
	_, err = c.WriteTo(echoRequest(), to)
	return err
}

// openBPF opens the first free /dev/bpfN for writing; macOS has no cloning /dev/bpf, and creates
// the next node only when the last one is opened.
func openBPF() (int, error) {
	for i := range 256 {
		fd, err := unix.Open(fmt.Sprintf("/dev/bpf%d", i), unix.O_RDWR|unix.O_CLOEXEC, 0)
		if err == nil {
			return fd, nil
		}
		if !errors.Is(err, unix.EBUSY) && !errors.Is(err, unix.ENOENT) {
			return -1, err
		}
	}
	return -1, unix.EBUSY
}

// bindBPF attaches fd to iface and has it send frames exactly as written, source MAC included.
func bindBPF(fd int, iface string) error {
	var ifr [unix.IFNAMSIZ + 16]byte
	copy(ifr[:unix.IFNAMSIZ-1], iface)
	if _, _, e := unix.Syscall(unix.SYS_IOCTL, uintptr(fd), unix.BIOCSETIF, uintptr(unsafe.Pointer(&ifr))); e != 0 {
		return e
	}
	if dlt, err := unix.IoctlGetInt(fd, unix.BIOCGDLT); err != nil || dlt != unix.DLT_EN10MB {
		return fmt.Errorf("%s does not take Ethernet frames", iface)
	}
	return unix.IoctlSetPointerInt(fd, unix.BIOCSHDRCMPLT, 1)
}

// captureFrom binds fd to iface to read the echo requests it sends, returning each read promptly.
func captureFrom(fd int, iface string) error {
	if err := setFilter(fd, echoFilter); err != nil {
		return err
	}
	if err := bindBPF(fd, iface); err != nil {
		return err
	}
	if err := unix.IoctlSetPointerInt(fd, unix.BIOCIMMEDIATE, 1); err != nil {
		return err
	}
	if err := unix.IoctlSetPointerInt(fd, unix.BIOCSSEESENT, 1); err != nil {
		return err
	}
	tv := unix.NsecToTimeval((50 * time.Millisecond).Nanoseconds())
	if _, _, e := unix.Syscall(unix.SYS_IOCTL, uintptr(fd), unix.BIOCSRTIMEOUT, uintptr(unsafe.Pointer(&tv))); e != 0 {
		return e
	}
	return nil
}

func setFilter(fd int, prog []bpf.Instruction) error {
	raw, err := bpf.Assemble(prog)
	if err != nil {
		return err
	}
	insns := make([]unix.BpfInsn, len(raw))
	for i, r := range raw {
		insns[i] = unix.BpfInsn{Code: r.Op, Jt: r.Jt, Jf: r.Jf, K: r.K}
	}
	p := unix.BpfProgram{Len: uint32(len(insns)), Insns: &insns[0]}
	if _, _, e := unix.Syscall(unix.SYS_IOCTL, uintptr(fd), unix.BIOCSETF, uintptr(unsafe.Pointer(&p))); e != 0 {
		return e
	}
	return nil
}
