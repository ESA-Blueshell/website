//go:build darwin

package paint

import (
	"crypto/rand"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"time"
	"unsafe"

	"golang.org/x/net/bpf"
	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv6"
	"golang.org/x/sys/unix"
)

// learnTemplate pings dst once through the kernel and reads the frame it sent back off /dev/bpf,
// so routing, source address selection and the neighbour lookup are the kernel's own. macOS
// offers no public way to capture outgoing frames only, so the probe carries a random nonce and
// checkOwnFrame refuses any frame not sent from this host's own MAC and address.
func learnTemplate(dst [16]byte, probe *icmp.PacketConn) (*frameTemplate, error) {
	ifc, err := egress(dst)
	if err != nil {
		return nil, err
	}
	fd, err := openBPF()
	if err != nil {
		return nil, err
	}
	defer unix.Close(fd)
	if err := captureProbes(fd, ifc.Name); err != nil {
		return nil, fmt.Errorf("capture on %s: %w", ifc.Name, err)
	}
	blen, err := unix.IoctlGetInt(fd, unix.BIOCGBLEN)
	if err != nil {
		return nil, err
	}
	nonce := make([]byte, probeNonceLen)
	_, _ = rand.Read(nonce)
	echo, err := (&icmp.Message{Type: ipv6.ICMPTypeEchoRequest, Body: &icmp.Echo{ID: 1, Seq: 1, Data: nonce}}).Marshal(nil)
	if err != nil {
		return nil, err
	}
	var to net.Addr = &net.IPAddr{IP: dst[:]}
	if _, ok := probe.LocalAddr().(*net.UDPAddr); ok {
		to = &net.UDPAddr{IP: dst[:]}
	}
	buf := make([]byte, blen)
	for range 3 {
		if _, err := probe.WriteTo(echo, to); err != nil {
			return nil, fmt.Errorf("probe %s: %w", netip.AddrFrom16(dst), err)
		}
		for deadline := time.Now().Add(learnWait); time.Now().Before(deadline); {
			n, err := unix.Read(fd, buf)
			if err != nil {
				continue
			}
			for _, f := range bpfFrames(buf[:n]) {
				if !isProbeFrame(f, dst, nonce) {
					continue
				}
				if err := checkOwnFrame(f, ifc.Index); err != nil {
					return nil, err
				}
				return newTemplate(f, int32(ifc.Index)), nil
			}
		}
	}
	return nil, errors.New("the probe frame never left; no route or no neighbour")
}

// egress is the interface the kernel sends to dst from, found by the source address it picks; a
// UDP connect picks one without sending anything. Only Ethernet-type interfaces take frames.
func egress(dst [16]byte) (*net.Interface, error) {
	c, err := net.DialUDP("udp6", nil, &net.UDPAddr{IP: dst[:], Port: 9})
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
					return nil, fmt.Errorf("the route to %s leaves %s, which is not Ethernet", netip.AddrFrom16(dst), ifs[i].Name)
				}
				return &ifs[i], nil
			}
		}
	}
	return nil, fmt.Errorf("no interface holds %s", src)
}

// openBPF opens the first free /dev/bpfN; macOS has no cloning /dev/bpf, and creates the next node
// only when the last one is opened. It needs root or the access_bpf group Wireshark's ChmodBPF sets
// up.
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

// captureProbes binds fd to iface to read sent echo requests the size of a probe, each read
// returning promptly.
func captureProbes(fd int, iface string) error {
	err := setFilter(fd, []bpf.Instruction{
		bpf.LoadAbsolute{Off: 12, Size: 2},
		bpf.JumpIf{Cond: bpf.JumpNotEqual, Val: 0x86dd, SkipTrue: 7},
		bpf.LoadAbsolute{Off: ethHeader + 4, Size: 2},
		bpf.JumpIf{Cond: bpf.JumpNotEqual, Val: 8 + probeNonceLen, SkipTrue: 5},
		bpf.LoadAbsolute{Off: ethHeader + 6, Size: 1},
		bpf.JumpIf{Cond: bpf.JumpNotEqual, Val: 58, SkipTrue: 3},
		bpf.LoadAbsolute{Off: frameHead, Size: 1},
		bpf.JumpIf{Cond: bpf.JumpNotEqual, Val: uint32(ipv6.ICMPTypeEchoRequest), SkipTrue: 1},
		bpf.RetConstant{Val: 0xffff},
		bpf.RetConstant{Val: 0},
	})
	if err != nil {
		return err
	}
	if err := bindBPF(fd, iface); err != nil {
		return err
	}
	for _, opt := range []uint{unix.BIOCIMMEDIATE, unix.BIOCSSEESENT} {
		if err := unix.IoctlSetPointerInt(fd, opt, 1); err != nil {
			return err
		}
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
