# pinger-rs

A Rust port of the Go pinger's Linux send path, built to find out whether Rust sends more packets
per CPU. It does when the sender is pinned at 100% of a core, but not by enough to matter here. The
Go pinger does not use it, and nothing builds or tests it in CI.

## What it does

- Sends payload-free echo requests as 62-byte Ethernet frames on an `AF_PACKET` socket with
  `PACKET_QDISC_BYPASS`. It learns the frame header the same way the Go sender does: it pings once
  through the kernel and reads its own frame back. For each packet it writes only the
  destination's low 64 bits and the checksum, which it updates from a precomputed sum (RFC 1624).
- Has two send modes: `--mode ring` (a `PACKET_TX_RING`, TPACKET_V2, where one `sendto` flushes a
  chunk) and `--mode mmsg` (`sendmmsg` over prebuilt frames, like the Go sender).
- Runs passes the way `paint/sender.go` does: the same seeded hash order, a pass that keeps going
  when the image changes, placement offsets and a rate split over `--workers`.
- Takes its commands on stdin (`prefix`, `rate`, `pixels`, `offsets`; see `src/proto.rs`) and
  prints a `stats` line on stdout every second. It exits when stdin closes.
- `--synthetic N --prefix P --rate R --duration S` runs a benchmark without a driver.

AF_XDP was ruled out. Creating the socket needs only `CAP_NET_RAW`, but the UMEM counts against
`RLIMIT_MEMLOCK`, which the pod does not control. On a NIC without zero-copy support, copy mode
builds one skb per frame and sends it with `__dev_direct_xmit`, which is the same work the packet
socket already does. Zero-copy depends on the node's NIC driver, and binding it reconfigures a
queue that the host's own traffic uses. We run on the host network, so that is too risky during
the event.

## Numbers

`bench/run.sh` builds both senders into one image and sends into a veth pair inside the
container's network namespace, so nothing leaves the host. Both senders use 450,000 pixels and run
for 10 s after a 1 s warm-up. CPU is the process's user plus system time. The host is Docker
Desktop: 8 vCPUs, kernel 6.10.

Packets per second with the sender pinned:

| sender                         | `--cpus=1`  | all CPUs              |
| ------------------------------ | ----------- | --------------------- |
| Go, sendmmsg (main)            | 3.27–3.42 M | 12.5 M on 3.5 cores   |
| Rust, sendmmsg 64 per call     | 4.30–4.41 M | 4.44 M (1 worker)     |
| Rust, sendmmsg 1024 per call   | 4.45–4.47 M |                       |
| Rust, TX ring                  | 3.54–3.58 M | 12.7 M on 3.6 cores (4 workers) |

CPU at a fixed rate:

| rate      | Go  | Rust TX ring | Rust sendmmsg |
| --------- | --- | ------------ | ------------- |
| 200,000   | 17–19% | 13–14%    | 12%           |
| 1,000,000 | 41% | 40–42%       | 39%           |

- Rust with sendmmsg sends 1.3x the Go sender's packets per core, but only when the sender is
  pinned at 100% of a core. At the rates the pinger actually runs, the gap is a few percent of one
  core.
- The TX ring is slower than sendmmsg for frames this small. The kernel attaches ring pages to each
  skb as fragments and frees them through a destructor, which costs more than copying 62 bytes.
- The api and the CLI cap the rate at 200,000 packets a second. At that rate the Go sender uses
  less than a fifth of the pod's one CPU, so a faster sender would not add a single packet.
- On a real NIC the driver's work for each packet comes on top of these numbers, so the gap shrinks
  further.

## Building

```sh
docker run --rm -v "$PWD":/src -w /src rust:1-bookworm cargo test   # from services/pinger-rs
services/pinger-rs/bench/run.sh                                      # from the repository root
```
