//go:build windows

package paint

import (
	"fmt"
	"net"
	"os"
	"sync"
	"syscall"
	"time"
	"unsafe"

	"golang.org/x/net/ipv6"
	"golang.org/x/sys/windows"
)

const (
	// echoSlots caps the requests one handle keeps in flight. Each holds a reply buffer and an
	// event until the kernel times it out, so a handle sends at most echoSlots per echoTimeout.
	echoSlots = 4096
	// echoReplySize holds an ICMPV6_ECHO_REPLY, an 8-byte ICMP error and an IO_STATUS_BLOCK, the
	// minimum Icmp6SendEcho2 documents, with room to spare.
	echoReplySize = 256
	// echoTimeout is how long the kernel holds a request open for a reply nobody reads.
	echoTimeout = 200 * time.Millisecond
	// echoSlotWait bounds the wait for the oldest request to free its slot.
	echoSlotWait = 2 * time.Second
)

var (
	iphlpapi          = windows.NewLazySystemDLL("iphlpapi.dll")
	procIcmp6Create   = iphlpapi.NewProc("Icmp6CreateFile")
	procIcmp6SendEcho = iphlpapi.NewProc("Icmp6SendEcho2")
	procIcmpClose     = iphlpapi.NewProc("IcmpCloseHandle")
	echoNoPayload     byte
)

// echoAPI sends through Icmp6SendEcho2, the ICMP helper ping.exe uses, which needs no administrator.
// Each call is asynchronous: the kernel sends at once and keeps the request open, writing into its
// reply buffer and signalling its event when a reply comes or the request times out. The reply
// buffers live outside the Go heap, since the kernel writes them after the call returns.
type echoAPI struct {
	h      windows.Handle
	mu     sync.Mutex
	events [echoSlots]windows.Handle
	arena  uintptr
	next   int
	src    windows.RawSockaddrInet6
	dst    windows.RawSockaddrInet6
	gate   *readGate
}

func listenEchoAPI() (*echoAPI, error) {
	for _, p := range []*windows.LazyProc{procIcmp6Create, procIcmp6SendEcho, procIcmpClose} {
		if err := p.Find(); err != nil {
			return nil, err
		}
	}
	r, _, e := procIcmp6Create.Call()
	if windows.Handle(r) == windows.InvalidHandle {
		return nil, os.NewSyscallError("Icmp6CreateFile", e)
	}
	s := &echoAPI{
		h:    windows.Handle(r),
		src:  windows.RawSockaddrInet6{Family: windows.AF_INET6},
		dst:  windows.RawSockaddrInet6{Family: windows.AF_INET6},
		gate: newReadGate(),
	}
	arena, err := windows.VirtualAlloc(0, echoSlots*echoReplySize, windows.MEM_COMMIT|windows.MEM_RESERVE, windows.PAGE_READWRITE)
	if err != nil {
		_, _, _ = procIcmpClose.Call(r)
		return nil, os.NewSyscallError("VirtualAlloc", err)
	}
	s.arena = arena
	for i := range s.events {
		// Auto-reset and signalled: a slot is free while its event is set, and the wait that finds
		// it set also claims it.
		ev, err := windows.CreateEvent(nil, 0, 1, nil)
		if err != nil {
			_, _, _ = procIcmpClose.Call(r)
			s.release(0)
			return nil, os.NewSyscallError("CreateEvent", err)
		}
		s.events[i] = ev
	}
	return s, nil
}

func (s *echoAPI) WriteTo(b []byte, dst net.Addr) (int, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := s.send(b, dst); err != nil {
		return 0, err
	}
	return len(b), nil
}

func (s *echoAPI) WriteBatch(msgs []ipv6.Message, _ int) (int, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return sendAll(msgs, s.send)
}

func (s *echoAPI) send(b []byte, dst net.Addr) error {
	addr, err := ipv6Dest(dst)
	if err != nil {
		return err
	}
	slot := s.next
	ev := s.events[slot]
	if w, err := windows.WaitForSingleObject(ev, uint32(echoSlotWait/time.Millisecond)); w != windows.WAIT_OBJECT_0 {
		return fmt.Errorf("no free echo slot: wait %d: %w", w, err)
	}
	s.next = (slot + 1) % echoSlots
	s.dst.Addr = addr
	payload := echoPayload(b)
	data := unsafe.Pointer(&echoNoPayload)
	if len(payload) > 0 {
		data = unsafe.Pointer(&payload[0])
	}
	r, _, e := syscall.SyscallN(procIcmp6SendEcho.Addr(),
		uintptr(s.h), uintptr(ev), 0, 0,
		uintptr(unsafe.Pointer(&s.src)), uintptr(unsafe.Pointer(&s.dst)),
		uintptr(data), uintptr(len(payload)), 0,
		s.arena+uintptr(slot*echoReplySize), echoReplySize,
		uintptr(echoTimeout/time.Millisecond))
	if r == 0 && e != windows.ERROR_IO_PENDING {
		// Nothing is in flight on the slot, so free it again.
		_ = windows.SetEvent(ev)
		return os.NewSyscallError("Icmp6SendEcho2", e)
	}
	return nil
}

// ReadFrom never yields a packet: replies land in the reply buffers, which nothing reads.
func (s *echoAPI) ReadFrom(b []byte) (int, net.Addr, error) { return s.gate.ReadFrom(b) }

func (s *echoAPI) SetReadDeadline(t time.Time) error { return s.gate.SetReadDeadline(t) }

// Close cancels the requests in flight and frees their reply buffers once all have finished. A
// request that never finishes leaves the buffers allocated, since the kernel may still write them.
func (s *echoAPI) Close() error {
	s.gate.Close()
	s.mu.Lock()
	defer s.mu.Unlock()
	_, _, _ = procIcmpClose.Call(uintptr(s.h))
	s.release(echoTimeout + echoSlotWait)
	return nil
}

// release waits up to wait for every slot to come free, then closes the events and frees the arena
// if they all did.
func (s *echoAPI) release(wait time.Duration) {
	deadline := time.Now().Add(wait)
	settled := true
	for _, ev := range s.events {
		if ev == 0 {
			continue
		}
		left := max(0, time.Until(deadline))
		if w, _ := windows.WaitForSingleObject(ev, uint32(left/time.Millisecond)); w != windows.WAIT_OBJECT_0 {
			settled = false
		}
		_ = windows.CloseHandle(ev)
	}
	if settled && s.arena != 0 {
		_ = windows.VirtualFree(s.arena, 0, windows.MEM_RELEASE)
	}
	s.arena = 0
}
