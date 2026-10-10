//go:build linux

package paint

import (
	"encoding/binary"
	"os"
	"sync/atomic"
	"time"
	"unsafe"

	"golang.org/x/sys/unix"
)

const (
	ringFrameSize = 128
	ringFrames    = 2048
	// ringData is where a frame's bytes start in its slot: past the tpacket2_hdr, padded to
	// TPACKET_ALIGNMENT, as the kernel reads it without PACKET_TX_HAS_OFF.
	ringData = (unix.SizeofTpacket2Hdr + unix.TPACKET_ALIGNMENT - 1) &^ (unix.TPACKET_ALIGNMENT - 1)
	// ringWait bounds how long a full ring waits for the link to drain before the send fails.
	ringWait = 100 * time.Millisecond
	ringBusy = unix.TP_STATUS_SEND_REQUEST | unix.TP_STATUS_SENDING | unix.TP_STATUS_WRONG_FORMAT
	// vnetHdrLen is sizeof(struct virtio_net_hdr), whose fields the kernel reads in host order.
	vnetHdrLen = 10
)

// ringVnet is off only in benchmarks that measure the ring without it.
var ringVnet = true

// txRing is a PACKET_TX_RING shared with the kernel: frames are written straight into it and one
// send hands the kernel every frame marked ready, so no msghdr, iovec or address is copied in per
// frame. The kernel walks the ring strictly in order from its own head, so frames must be filled
// in that order too: a slot skipped would stall every frame behind it.
type txRing struct {
	mem  []byte
	next int
	// vnet is the virtio_net_hdr each frame starts with, or 0. Its hdr_len has the kernel copy the
	// whole frame into the skb; without it everything past the Ethernet header is attached as a
	// page of the ring, which costs a page reference per frame and a pull at every hop that reads
	// the IPv6 header.
	vnet int
}

// openTxRing maps a TX ring on fd, which must not have sent yet. On an error fd is left half set up,
// so the caller discards it.
func openTxRing(fd int) (*txRing, error) {
	if err := unix.SetsockoptInt(fd, unix.SOL_PACKET, unix.PACKET_VERSION, unix.TPACKET_V2); err != nil {
		return nil, err
	}
	r := &txRing{}
	if ringVnet && unix.SetsockoptInt(fd, unix.SOL_PACKET, unix.PACKET_VNET_HDR, 1) == nil {
		r.vnet = vnetHdrLen
	}
	// Without PACKET_LOSS a malformed frame parks the ring until someone clears it by hand.
	if err := unix.SetsockoptInt(fd, unix.SOL_PACKET, unix.PACKET_LOSS, 1); err != nil {
		return nil, err
	}
	block := max(os.Getpagesize(), 64<<10)
	req := unix.TpacketReq{
		Block_size: uint32(block),
		Block_nr:   uint32(ringFrames * ringFrameSize / block),
		Frame_size: ringFrameSize,
		Frame_nr:   ringFrames,
	}
	if err := unix.SetsockoptTpacketReq(fd, unix.SOL_PACKET, unix.PACKET_TX_RING, &req); err != nil {
		return nil, err
	}
	mem, err := unix.Mmap(fd, 0, ringFrames*ringFrameSize, unix.PROT_READ|unix.PROT_WRITE, unix.MAP_SHARED)
	if err != nil {
		return nil, err
	}
	r.mem = mem
	return r, nil
}

func (r *txRing) status(slot int) *uint32 {
	return (*uint32)(unsafe.Pointer(&r.mem[slot*ringFrameSize]))
}

// claim returns the next slot's frame space, flushing and waiting while the kernel still holds it.
func (r *txRing) claim(fd int, to *unix.RawSockaddrLinklayer) ([]byte, error) {
	st := r.status(r.next)
	// poll cannot say when this slot frees: it reports the slot at the kernel's head, which a full
	// send buffer leaves unsent. So retry the flush on a short poll.
	var deadline time.Time
	for atomic.LoadUint32(st)&ringBusy != 0 {
		if err := r.flush(fd, to); err != nil {
			return nil, err
		}
		if atomic.LoadUint32(st)&ringBusy == 0 {
			break
		}
		if deadline.IsZero() {
			deadline = time.Now().Add(ringWait)
		} else if time.Now().After(deadline) {
			return nil, unix.ENOBUFS
		}
		_, _ = unix.Poll([]unix.PollFd{{Fd: int32(fd), Events: unix.POLLOUT}}, 1)
	}
	off := r.next*ringFrameSize + ringData + r.vnet
	return r.mem[off : (r.next+1)*ringFrameSize], nil
}

// ready marks the claimed slot as an n-byte frame to send and moves to the next.
func (r *txRing) ready(n int) {
	slot := r.mem[r.next*ringFrameSize:]
	if r.vnet != 0 {
		// flags, gso_type, gso_size and the checksum fields stay zero: no offload asked.
		clear(slot[ringData : ringData+r.vnet])
		binary.NativeEndian.PutUint16(slot[ringData+2:], uint16(n))
	}
	binary.NativeEndian.PutUint32(slot[4:], uint32(r.vnet+n))
	atomic.StoreUint32(r.status(r.next), unix.TP_STATUS_SEND_REQUEST)
	r.next = (r.next + 1) % ringFrames
}

// flush hands the kernel every ready frame without waiting for the link. A frame the kernel
// could not take yet stays ready for the next flush, so only a missing device is an error.
func (r *txRing) flush(fd int, to *unix.RawSockaddrLinklayer) error {
	_, _, errno := unix.Syscall6(unix.SYS_SENDTO, uintptr(fd), 0, 0, unix.MSG_DONTWAIT,
		uintptr(unsafe.Pointer(to)), unix.SizeofSockaddrLinklayer)
	switch {
	case errno == 0, errno == unix.EAGAIN, errno == unix.ENOBUFS, errno == unix.EINTR:
		return nil
	}
	return errno
}

func (r *txRing) close() error {
	if r == nil {
		return nil
	}
	return unix.Munmap(r.mem)
}
