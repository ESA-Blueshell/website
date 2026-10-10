//! The Linux send paths. Frames go out on a packet socket with the qdisc bypassed, either through
//! a PACKET_TX_RING (one sendto flushes a whole chunk, no per-packet msghdr or copy-in) or through
//! sendmmsg. A /64 whose route is not Ethernet goes through a raw ICMPv6 socket instead. All of it
//! needs CAP_NET_RAW and nothing more.

use std::io;
use std::mem::{size_of, zeroed};
use std::ptr;
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, Ordering::*};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

use crate::engine::{Engine, Path, CHUNK_MAX};
use crate::frame::{Template, ETH, FRAME_LEN, IP6};

const SOL_PACKET: libc::c_int = 263;
const PACKET_VERSION: libc::c_int = 10;
const PACKET_TX_RING: libc::c_int = 13;
const PACKET_QDISC_BYPASS: libc::c_int = 20;
const TPACKET_V2: libc::c_int = 1;
const TP_STATUS_AVAILABLE: u32 = 0;
const TP_STATUS_SEND_REQUEST: u32 = 1;
const ETH_P_IPV6: u16 = 0x86dd;
const ETH_P_ALL: u16 = 0x0003;
const PACKET_OUTGOING: u8 = 4;
const ARPHRD_ETHER: u16 = 1;
/// TPACKET_ALIGN(sizeof(struct tpacket2_hdr)): where a v2 TX frame's data starts.
const TX_DATA: usize = 32;
const RING_FRAME: usize = 128;
const RING_BLOCK: usize = 4096;
const RING_FRAMES: usize = 2 * CHUNK_MAX;
const SEND_BUFFER: libc::c_int = 4 << 20;
/// How long a learned route is trusted, and how long a failed learn is before it is tried again.
const RELEARN: Duration = Duration::from_secs(30);
const LEARN_WAIT: Duration = Duration::from_millis(500);

fn errno() -> io::Error {
    io::Error::last_os_error()
}

fn check(r: libc::c_int) -> io::Result<libc::c_int> {
    if r < 0 {
        Err(errno())
    } else {
        Ok(r)
    }
}

fn setopt<T>(fd: libc::c_int, level: libc::c_int, name: libc::c_int, v: &T) -> io::Result<()> {
    // SAFETY: v points at a live T of the size passed.
    check(unsafe {
        libc::setsockopt(
            fd,
            level,
            name,
            v as *const T as *const libc::c_void,
            size_of::<T>() as libc::socklen_t,
        )
    })
    .map(drop)
}

struct Fd(libc::c_int);

impl Drop for Fd {
    fn drop(&mut self) {
        // SAFETY: the descriptor is ours and closed once.
        unsafe { libc::close(self.0) };
    }
}

/// The learned frame template for the /64 being painted, shared by every worker.
#[derive(Default)]
pub struct Route {
    current: Mutex<Option<Arc<Template>>>,
    generation: AtomicU64,
    failed: Mutex<Option<([u8; 8], Instant)>>,
    invalid: AtomicBool,
}

impl Route {
    fn get(&self) -> Option<Arc<Template>> {
        self.current.lock().unwrap().clone()
    }

    fn failed_for(&self, net64: &[u8; 8]) -> bool {
        matches!(*self.failed.lock().unwrap(), Some((n, at)) if n == *net64 && at.elapsed() < RELEARN)
    }

    /// Learns the template again whenever the prefix moves, the route ages out or a send says the
    /// device went away. The old template keeps sending while a new one is learned.
    pub fn keep_learning(&self, engine: &Engine) {
        while !engine.stop.load(Relaxed) {
            thread::sleep(Duration::from_millis(100));
            let cfg = engine.config();
            let (Some(prefix), Some(px)) = (cfg.prefix, cfg.pixels.first()) else {
                continue;
            };
            let fresh =
                matches!(self.get(), Some(t) if t.net64 == prefix && t.learned.elapsed() < RELEARN);
            if fresh && !self.invalid.load(Relaxed) || self.failed_for(&prefix) {
                continue;
            }
            let mut dst = [0u8; 16];
            dst[..8].copy_from_slice(&prefix);
            dst[8..].copy_from_slice(&px.low64());
            match learn(dst) {
                Ok(t) => {
                    *self.failed.lock().unwrap() = None;
                    *self.current.lock().unwrap() = Some(Arc::new(t));
                    self.invalid.store(false, Relaxed);
                    self.generation.fetch_add(1, Release);
                }
                Err(e) => {
                    eprintln!("pinger-rs: no frame route, sending through the raw socket: {e}");
                    *self.failed.lock().unwrap() = Some((prefix, Instant::now()));
                    *self.current.lock().unwrap() = None;
                    self.generation.fetch_add(1, Release);
                }
            }
        }
    }
}

#[derive(Clone, Copy, PartialEq, Debug)]
pub enum Mode {
    Ring,
    Mmsg,
}

/// One worker's sockets: a packet socket for frames and the raw socket for routes that are not
/// Ethernet.
pub struct FramePath {
    route: Arc<Route>,
    template: Option<Arc<Template>>,
    seen: u64,
    fd: Fd,
    raw: RawSocket,
    ring: Option<Ring>,
    mmsg: Mmsg,
}

impl FramePath {
    pub fn open(route: Arc<Route>, mode: Mode, mmsg_batch: usize) -> io::Result<FramePath> {
        // Protocol 0: the socket only sends, so no frame is ever queued to it.
        // SAFETY: plain socket call.
        let fd = Fd(check(unsafe {
            libc::socket(libc::AF_PACKET, libc::SOCK_RAW | libc::SOCK_CLOEXEC, 0)
        })?);
        setopt(fd.0, SOL_PACKET, PACKET_QDISC_BYPASS, &1i32)?;
        if setopt(fd.0, libc::SOL_SOCKET, libc::SO_SNDBUFFORCE, &SEND_BUFFER).is_err() {
            let _ = setopt(fd.0, libc::SOL_SOCKET, libc::SO_SNDBUF, &SEND_BUFFER);
        }
        let ring = match mode {
            Mode::Ring => Some(Ring::map(fd.0)?),
            Mode::Mmsg => None,
        };
        Ok(FramePath {
            route,
            template: None,
            seen: u64::MAX,
            fd,
            raw: RawSocket::open()?,
            ring,
            mmsg: Mmsg::new(mmsg_batch),
        })
    }

    fn addr(t: &Template) -> libc::sockaddr_ll {
        // SAFETY: sockaddr_ll is plain data; zero is a valid value.
        let mut a: libc::sockaddr_ll = unsafe { zeroed() };
        a.sll_family = libc::AF_PACKET as u16;
        a.sll_protocol = ETH_P_IPV6.to_be();
        a.sll_ifindex = t.ifindex;
        a.sll_halen = 6;
        a.sll_addr[..6].copy_from_slice(&t.gateway);
        a
    }
}

impl Path for FramePath {
    fn send(&mut self, prefix: &[u8; 8], lows: &[[u8; 8]]) -> (usize, Option<String>) {
        let g = self.route.generation.load(Acquire);
        if g != self.seen {
            self.template = self.route.get();
            self.seen = g;
        }
        let Some(t) = self.template.clone().filter(|t| t.net64 == *prefix) else {
            if self.route.failed_for(prefix) {
                return self.raw.send(prefix, lows);
            }
            return (0, None);
        };
        let addr = Self::addr(&t);
        let (ok, err) = match &mut self.ring {
            Some(ring) => ring.send(self.fd.0, &t, g, &addr, lows),
            None => self.mmsg.send(self.fd.0, &t, &addr, lows),
        };
        if let Some(e) = &err {
            if matches!(
                e.raw_os_error(),
                Some(libc::ENETDOWN | libc::ENXIO | libc::ENODEV)
            ) {
                self.route.invalid.store(true, Relaxed);
            }
        }
        (ok, err.map(|e| e.to_string()))
    }
}

/// A TPACKET_V2 TX ring. Each slot keeps the frame it last carried, so a packet rewrites only the
/// destination and checksum unless the template changed since.
struct Ring {
    base: *mut u8,
    len: usize,
    head: usize,
    stamps: Vec<u64>,
}

// SAFETY: the mapping is owned by one worker and freed on drop.
unsafe impl Send for Ring {}

#[repr(C)]
struct TpacketReq {
    block_size: u32,
    block_nr: u32,
    frame_size: u32,
    frame_nr: u32,
}

impl Ring {
    fn map(fd: libc::c_int) -> io::Result<Ring> {
        setopt(fd, SOL_PACKET, PACKET_VERSION, &TPACKET_V2)?;
        let req = TpacketReq {
            block_size: RING_BLOCK as u32,
            block_nr: (RING_FRAMES * RING_FRAME / RING_BLOCK) as u32,
            frame_size: RING_FRAME as u32,
            frame_nr: RING_FRAMES as u32,
        };
        setopt(fd, SOL_PACKET, PACKET_TX_RING, &req)?;
        let len = RING_FRAMES * RING_FRAME;
        // SAFETY: maps the ring the kernel just allocated for fd.
        let base = unsafe {
            libc::mmap(
                ptr::null_mut(),
                len,
                libc::PROT_READ | libc::PROT_WRITE,
                libc::MAP_SHARED,
                fd,
                0,
            )
        };
        if base == libc::MAP_FAILED {
            return Err(errno());
        }
        Ok(Ring {
            base: base as *mut u8,
            len,
            head: 0,
            stamps: vec![u64::MAX; RING_FRAMES],
        })
    }

    fn status(&self, slot: usize) -> &AtomicU32 {
        // SAFETY: tp_status is the first, 4-aligned word of a slot inside the mapping.
        unsafe { &*(self.base.add(slot * RING_FRAME) as *const AtomicU32) }
    }

    /// Waits for the kernel to hand a slot back, which it does once the frame left the device.
    fn wait(&self, fd: libc::c_int, addr: &libc::sockaddr_ll, slot: usize) -> io::Result<()> {
        let deadline = Instant::now() + Duration::from_millis(200);
        let mut spins = 0u32;
        while self.status(slot).load(Acquire) != TP_STATUS_AVAILABLE {
            if spins == 0 {
                // A blocking flush waits for the frames in flight to complete.
                // SAFETY: addr is a live sockaddr_ll.
                unsafe {
                    libc::sendto(
                        fd,
                        ptr::null(),
                        0,
                        0,
                        addr as *const _ as *const libc::sockaddr,
                        size_of::<libc::sockaddr_ll>() as u32,
                    )
                };
            }
            spins += 1;
            if Instant::now() > deadline {
                return Err(io::Error::new(
                    io::ErrorKind::WouldBlock,
                    "the tx ring stayed full",
                ));
            }
            if spins > 64 {
                thread::sleep(Duration::from_micros(50));
            } else {
                thread::yield_now();
            }
        }
        Ok(())
    }

    fn send(
        &mut self,
        fd: libc::c_int,
        t: &Template,
        stamp: u64,
        addr: &libc::sockaddr_ll,
        lows: &[[u8; 8]],
    ) -> (usize, Option<io::Error>) {
        let mut filled = 0;
        let mut err = None;
        for low in lows {
            let slot = self.head;
            if let Err(e) = self.wait(fd, addr, slot) {
                err = Some(e);
                break;
            }
            // SAFETY: the slot belongs to us while AVAILABLE; data and header lie inside it.
            unsafe {
                let frame = self.base.add(slot * RING_FRAME);
                let data = std::slice::from_raw_parts_mut(frame.add(TX_DATA), FRAME_LEN);
                if self.stamps[slot] == stamp {
                    t.patch(data, low);
                } else {
                    t.write(data, low);
                    self.stamps[slot] = stamp;
                }
                (frame.add(4) as *mut u32).write(FRAME_LEN as u32);
            }
            self.status(slot).store(TP_STATUS_SEND_REQUEST, Release);
            self.head = (slot + 1) % RING_FRAMES;
            filled += 1;
        }
        // SAFETY: addr is a live sockaddr_ll; the kernel sends every SEND_REQUEST slot.
        let r = unsafe {
            libc::sendto(
                fd,
                ptr::null(),
                0,
                libc::MSG_DONTWAIT,
                addr as *const _ as *const libc::sockaddr,
                size_of::<libc::sockaddr_ll>() as u32,
            )
        };
        if r >= 0 {
            return (filled, err);
        }
        // The kernel walks the ring from its own head and stops at the first slot not marked for
        // sending, so a refused slot must stay marked or every later one strands: it goes out on the
        // next flush. Until then the chunk counts as failed.
        (0, Some(errno()))
    }
}

impl Drop for Ring {
    fn drop(&mut self) {
        // SAFETY: unmaps what map mapped.
        unsafe { libc::munmap(self.base as *mut libc::c_void, self.len) };
    }
}

/// sendmmsg over prebuilt frames, as the Go sender does it.
struct Mmsg {
    batch: usize,
    frames: Vec<u8>,
    iovs: Vec<libc::iovec>,
    hdrs: Vec<libc::mmsghdr>,
}

// SAFETY: the raw pointers in iovs and hdrs only ever point into this struct's own buffers.
unsafe impl Send for Mmsg {}

impl Mmsg {
    fn new(batch: usize) -> Mmsg {
        let batch = batch.clamp(1, 1024);
        // SAFETY: iovec and mmsghdr are plain data; zero is a valid value.
        Mmsg {
            batch,
            frames: vec![0; batch * FRAME_LEN],
            iovs: vec![unsafe { zeroed() }; batch],
            hdrs: vec![unsafe { zeroed() }; batch],
        }
    }

    fn send(
        &mut self,
        fd: libc::c_int,
        t: &Template,
        addr: &libc::sockaddr_ll,
        lows: &[[u8; 8]],
    ) -> (usize, Option<io::Error>) {
        let mut sent = 0;
        let mut err = None;
        for part in lows.chunks(self.batch) {
            for (i, low) in part.iter().enumerate() {
                let f = &mut self.frames[i * FRAME_LEN..(i + 1) * FRAME_LEN];
                t.write(f, low);
                self.iovs[i] = libc::iovec {
                    iov_base: f.as_mut_ptr() as *mut libc::c_void,
                    iov_len: FRAME_LEN,
                };
                let h = &mut self.hdrs[i].msg_hdr;
                h.msg_name = addr as *const _ as *mut libc::c_void;
                h.msg_namelen = size_of::<libc::sockaddr_ll>() as u32;
                h.msg_iov = &mut self.iovs[i];
                h.msg_iovlen = 1;
            }
            let mut at = 0;
            while at < part.len() {
                // SAFETY: hdrs[at..part.len()] point at live frames and addr.
                let n = unsafe {
                    libc::sendmmsg(
                        fd,
                        self.hdrs[at..].as_mut_ptr(),
                        (part.len() - at) as u32,
                        0,
                    )
                };
                if n < 0 {
                    err = Some(errno());
                    at += 1; // skip the message the kernel refused
                    continue;
                }
                if n == 0 {
                    break;
                }
                sent += n as usize;
                at += n as usize;
            }
        }
        (sent, err)
    }
}

/// The raw ICMPv6 socket, for routes that carry no Ethernet header; the kernel fills in the
/// checksum and the headers.
struct RawSocket {
    fd: Fd,
    msgs: Vec<libc::sockaddr_in6>,
}

impl RawSocket {
    fn open() -> io::Result<RawSocket> {
        // SAFETY: plain socket call.
        let fd = Fd(check(unsafe {
            libc::socket(
                libc::AF_INET6,
                libc::SOCK_RAW | libc::SOCK_CLOEXEC,
                libc::IPPROTO_ICMPV6,
            )
        })?);
        // ICMP6_FILTER with every type blocked: nothing reads the replies.
        let _ = setopt(fd.0, libc::IPPROTO_ICMPV6, 1, &[u32::MAX; 8]);
        Ok(RawSocket {
            fd,
            msgs: Vec::new(),
        })
    }

    fn send(&mut self, prefix: &[u8; 8], lows: &[[u8; 8]]) -> (usize, Option<String>) {
        let echo: [u8; 8] = [128, 0, 0, 0, 0, 1, 0, 1];
        let mut sent = 0;
        let mut err = None;
        for part in lows.chunks(256) {
            self.msgs.clear();
            for low in part {
                // SAFETY: sockaddr_in6 is plain data; zero is a valid value.
                let mut a: libc::sockaddr_in6 = unsafe { zeroed() };
                a.sin6_family = libc::AF_INET6 as u16;
                a.sin6_addr.s6_addr[..8].copy_from_slice(prefix);
                a.sin6_addr.s6_addr[8..].copy_from_slice(low);
                self.msgs.push(a);
            }
            let mut iov = libc::iovec {
                iov_base: echo.as_ptr() as *mut libc::c_void,
                iov_len: echo.len(),
            };
            let mut hdrs: Vec<libc::mmsghdr> = self
                .msgs
                .iter_mut()
                .map(|a| {
                    // SAFETY: mmsghdr is plain data; zero is a valid value.
                    let mut h: libc::mmsghdr = unsafe { zeroed() };
                    h.msg_hdr.msg_name = a as *mut _ as *mut libc::c_void;
                    h.msg_hdr.msg_namelen = size_of::<libc::sockaddr_in6>() as u32;
                    h.msg_hdr.msg_iov = &mut iov;
                    h.msg_hdr.msg_iovlen = 1;
                    h
                })
                .collect();
            let mut at = 0;
            while at < hdrs.len() {
                // SAFETY: every header points at a live address and the echo.
                let n = unsafe {
                    libc::sendmmsg(
                        self.fd.0,
                        hdrs[at..].as_mut_ptr(),
                        (hdrs.len() - at) as u32,
                        0,
                    )
                };
                if n <= 0 {
                    err = Some(errno().to_string());
                    at += 1;
                    continue;
                }
                sent += n as usize;
                at += n as usize;
            }
        }
        (sent, err)
    }
}

/// Pings dst once through the kernel and reads the frame it sent back off a packet socket.
fn learn(dst: [u8; 16]) -> io::Result<Template> {
    let id = (std::process::id() as u16).max(1);
    let seq = (Instant::now().elapsed().as_nanos() as u16) | 0x8000;
    // SAFETY: plain socket calls.
    let pkt = Fd(check(unsafe {
        libc::socket(
            libc::AF_PACKET,
            libc::SOCK_RAW | libc::SOCK_CLOEXEC,
            ETH_P_ALL.to_be() as i32,
        )
    })?);
    attach_probe_filter(pkt.0, id)?;
    let tv = libc::timeval {
        tv_sec: 0,
        tv_usec: 50_000,
    };
    setopt(pkt.0, libc::SOL_SOCKET, libc::SO_RCVTIMEO, &tv)?;
    let raw = RawSocket::open()?;
    let echo = [
        128u8,
        0,
        0,
        0,
        (id >> 8) as u8,
        id as u8,
        (seq >> 8) as u8,
        seq as u8,
    ];
    // SAFETY: sockaddr_in6 is plain data; zero is a valid value.
    let mut to: libc::sockaddr_in6 = unsafe { zeroed() };
    to.sin6_family = libc::AF_INET6 as u16;
    to.sin6_addr.s6_addr = dst;
    let mut buf = [0u8; 2048];
    for _ in 0..3 {
        // SAFETY: echo and to are live for the call.
        let r = unsafe {
            libc::sendto(
                raw.fd.0,
                echo.as_ptr() as *const libc::c_void,
                echo.len(),
                0,
                &to as *const _ as *const libc::sockaddr,
                size_of::<libc::sockaddr_in6>() as u32,
            )
        };
        if r < 0 {
            return Err(errno());
        }
        let deadline = Instant::now() + LEARN_WAIT;
        while Instant::now() < deadline {
            // SAFETY: sockaddr_ll is plain data; zero is a valid value.
            let mut from: libc::sockaddr_ll = unsafe { zeroed() };
            let mut flen = size_of::<libc::sockaddr_ll>() as u32;
            // SAFETY: buf and from are live and sized as passed.
            let n = unsafe {
                libc::recvfrom(
                    pkt.0,
                    buf.as_mut_ptr() as *mut libc::c_void,
                    buf.len(),
                    0,
                    &mut from as *mut _ as *mut libc::sockaddr,
                    &mut flen,
                )
            };
            if n < 0 || from.sll_pkttype != PACKET_OUTGOING {
                continue;
            }
            let f = &buf[..n as usize];
            if from.sll_hatype != ARPHRD_ETHER {
                return Err(io::Error::other(format!(
                    "the route is not Ethernet (link type {})",
                    from.sll_hatype
                )));
            }
            if f.len() < ETH + IP6 + 8
                || f[12..14] != ETH_P_IPV6.to_be_bytes()
                || f[ETH + 24..ETH + 40] != dst
            {
                continue;
            }
            let icmp = &f[ETH + IP6..];
            if icmp[0] != 128 || icmp[4..6] != id.to_be_bytes() || icmp[6..8] != seq.to_be_bytes() {
                continue;
            }
            check_own_frame(f, from.sll_ifindex)?;
            return Ok(Template::new(
                f,
                from.sll_ifindex,
                std::process::id() as u16,
            ));
        }
    }
    Err(io::Error::other(
        "the probe frame never left; no route or no neighbour",
    ))
}

/// Passes only ICMPv6 echo requests carrying id, read at the network header.
fn attach_probe_filter(fd: libc::c_int, id: u16) -> io::Result<()> {
    const NET: u32 = 0xfff0_0000; // SKF_NET_OFF
    let ins = |code: u16, jt: u8, jf: u8, k: u32| libc::sock_filter { code, jt, jf, k };
    let prog = [
        ins(0x30, 0, 0, NET + 6),              // ldb next header
        ins(0x15, 0, 5, 58),                   // icmpv6?
        ins(0x30, 0, 0, NET + IP6 as u32),     // ldb type
        ins(0x15, 0, 3, 128),                  // echo request?
        ins(0x28, 0, 0, NET + IP6 as u32 + 4), // ldh id
        ins(0x15, 0, 1, id as u32),            // ours?
        ins(0x06, 0, 0, 0xffff),               // keep
        ins(0x06, 0, 0, 0),                    // drop
    ];
    let fprog = libc::sock_fprog {
        len: prog.len() as u16,
        filter: prog.as_ptr() as *mut libc::sock_filter,
    };
    setopt(fd, libc::SOL_SOCKET, libc::SO_ATTACH_FILTER, &fprog)
}

/// Refuses a probe frame that does not leave from this host to a single next hop: its source MAC
/// must be the interface's own, its source address one of ours, its destination MAC unicast and
/// someone else's.
fn check_own_frame(f: &[u8], ifindex: i32) -> io::Result<()> {
    let mut name = [0 as libc::c_char; libc::IF_NAMESIZE];
    // SAFETY: name holds IF_NAMESIZE bytes.
    if unsafe { libc::if_indextoname(ifindex as u32, name.as_mut_ptr()) }.is_null() {
        return Err(errno());
    }
    // SAFETY: if_indextoname wrote a terminated string.
    let ifname = unsafe { std::ffi::CStr::from_ptr(name.as_ptr()) }
        .to_string_lossy()
        .into_owned();
    let own = std::fs::read_to_string(format!("/sys/class/net/{ifname}/address"))?;
    let mac = |b: &[u8]| {
        b.iter()
            .map(|x| format!("{x:02x}"))
            .collect::<Vec<_>>()
            .join(":")
    };
    let (dst_mac, src_mac) = (&f[..6], &f[6..12]);
    if mac(src_mac) != own.trim() {
        return Err(io::Error::other(format!(
            "the probe left {ifname} from {}, not its own {}",
            mac(src_mac),
            own.trim()
        )));
    }
    if dst_mac[0] & 1 != 0 || dst_mac == src_mac {
        return Err(io::Error::other(format!(
            "the probe's next hop {} is not another single host",
            mac(dst_mac)
        )));
    }
    let src = &f[ETH + 8..ETH + 24];
    let mut ifap: *mut libc::ifaddrs = ptr::null_mut();
    // SAFETY: getifaddrs fills ifap, freed below.
    check(unsafe { libc::getifaddrs(&mut ifap) })?;
    let mut ours = false;
    let mut cur = ifap;
    while !cur.is_null() {
        // SAFETY: cur walks the list getifaddrs returned.
        unsafe {
            let a = (*cur).ifa_addr;
            if !a.is_null() && (*a).sa_family as i32 == libc::AF_INET6 {
                ours |= (*(a as *const libc::sockaddr_in6)).sin6_addr.s6_addr == src[..];
            }
            cur = (*cur).ifa_next;
        }
    }
    // SAFETY: frees what getifaddrs allocated.
    unsafe { libc::freeifaddrs(ifap) };
    if !ours {
        return Err(io::Error::other(
            "the probe's source is not an address of this host",
        ));
    }
    Ok(())
}
