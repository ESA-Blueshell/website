//! The Ethernet frame every echo request shares. The kernel's own probe frame supplies the
//! Ethernet and IPv6 header, so routing, source selection and the neighbour lookup stay the
//! kernel's; per packet only the destination's low 64 bits and the ICMPv6 checksum change.

use std::time::Instant;

pub const ETH: usize = 14;
pub const IP6: usize = 40;
pub const ICMP: usize = 8;
pub const FRAME_LEN: usize = ETH + IP6 + ICMP;
const DST_LOW: usize = ETH + 32;
const CSUM: usize = ETH + IP6 + 2;

#[derive(Clone, Debug)]
pub struct Template {
    pub ifindex: i32,
    pub gateway: [u8; 6],
    pub net64: [u8; 8],
    pub frame: [u8; FRAME_LEN],
    /// The ones'-complement sum of everything the checksum covers but the destination's low 64
    /// bits, which are the pixel.
    sum: u32,
    pub learned: Instant,
}

impl Template {
    /// Builds the template from the first ETH+IP6 bytes of a frame the kernel sent, for a
    /// payload-free echo request carrying `id` and sequence 1.
    pub fn new(head: &[u8], ifindex: i32, id: u16) -> Template {
        let mut frame = [0u8; FRAME_LEN];
        frame[..ETH + IP6].copy_from_slice(&head[..ETH + IP6]);
        frame[ETH + 4..ETH + 6].copy_from_slice(&(ICMP as u16).to_be_bytes());
        let icmp = [128, 0, 0, 0, (id >> 8) as u8, id as u8, 0, 1];
        frame[ETH + IP6..].copy_from_slice(&icmp);
        frame[DST_LOW..DST_LOW + 8].fill(0);
        // Pseudo-header: source, destination, upper-layer length, next header; then the message.
        let sum = sum16(&frame[ETH + 8..ETH + 40]) + ICMP as u32 + 58 + sum16(&icmp);
        let mut gateway = [0u8; 6];
        gateway.copy_from_slice(&head[..6]);
        let mut net64 = [0u8; 8];
        net64.copy_from_slice(&head[ETH + 24..ETH + 32]);
        Template {
            ifindex,
            gateway,
            net64,
            frame,
            sum,
            learned: Instant::now(),
        }
    }

    /// The checksum of the echo request to the pixel whose address ends in `low`.
    #[inline]
    pub fn checksum(&self, low: &[u8; 8]) -> u16 {
        let mut s = self.sum
            + u16::from_be_bytes([low[0], low[1]]) as u32
            + u16::from_be_bytes([low[2], low[3]]) as u32
            + u16::from_be_bytes([low[4], low[5]]) as u32
            + u16::from_be_bytes([low[6], low[7]]) as u32;
        s = (s & 0xffff) + (s >> 16);
        s = (s & 0xffff) + (s >> 16);
        !(s as u16)
    }

    /// Writes the whole frame to `low` into `out`.
    #[inline]
    pub fn write(&self, out: &mut [u8], low: &[u8; 8]) {
        out[..FRAME_LEN].copy_from_slice(&self.frame);
        Self::patch(self, out, low);
    }

    /// Rewrites only the destination and checksum of a frame `write` already filled.
    #[inline]
    pub fn patch(&self, out: &mut [u8], low: &[u8; 8]) {
        out[DST_LOW..DST_LOW + 8].copy_from_slice(low);
        out[CSUM..CSUM + 2].copy_from_slice(&self.checksum(low).to_be_bytes());
    }
}

/// Adds b as big-endian 16-bit words, an odd trailing byte padded with zero.
pub fn sum16(b: &[u8]) -> u32 {
    let (words, rest) = b.as_chunks::<2>();
    let mut s: u32 = words.iter().map(|&w| u16::from_be_bytes(w) as u32).sum();
    if let [last] = rest {
        s += (*last as u32) << 8;
    }
    s
}

#[cfg(test)]
mod tests {
    use super::*;

    fn probe_head() -> Vec<u8> {
        let mut f = vec![0u8; ETH + IP6];
        f[..6].copy_from_slice(&[0x02, 0, 0, 0, 0, 0x01]);
        f[6..12].copy_from_slice(&[0x02, 0, 0, 0, 0, 0x02]);
        f[12..14].copy_from_slice(&[0x86, 0xdd]);
        f[ETH] = 0x60;
        f[ETH + 6] = 58;
        f[ETH + 7] = 64;
        f[ETH + 8..ETH + 24]
            .copy_from_slice(&[0x20, 0x01, 0x0d, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1]);
        f[ETH + 24..ETH + 40].copy_from_slice(&[
            0x20, 0x01, 0x06, 0x10, 0x05, 0xea, 0x22, 0x1e, 9, 9, 9, 9, 9, 9, 9, 9,
        ]);
        f
    }

    /// The checksum straight from RFC 4443: the pseudo-header and message summed whole.
    fn full_checksum(f: &[u8]) -> u16 {
        let ip = &f[ETH..];
        let mut msg = ip[IP6..IP6 + ICMP].to_vec();
        msg[2] = 0;
        msg[3] = 0;
        let mut s = sum16(&ip[8..40]) + ICMP as u32 + 58 + sum16(&msg);
        while s > 0xffff {
            s = (s & 0xffff) + (s >> 16);
        }
        !(s as u16)
    }

    #[test]
    fn frames_carry_the_rfc_4443_checksum() {
        let t = Template::new(&probe_head(), 3, 0x1234);
        let mut out = [0u8; FRAME_LEN];
        for low in [
            [0u8; 8],
            [0xff; 8],
            [0x00, 0x19, 0x00, 0x19, 0x00, 0xd1, 0xff, 0xff],
            [0x0e, 0xff, 0x08, 0x6f, 1, 2, 3, 4],
        ] {
            t.write(&mut out, &low);
            assert_eq!(&out[ETH + 32..ETH + 40], &low);
            assert_eq!(
                u16::from_be_bytes([out[CSUM], out[CSUM + 1]]),
                full_checksum(&out),
                "low {low:?}"
            );
        }
        assert_eq!(&out[ETH + 4..ETH + 6], &[0, 8]);
        assert_eq!(&out[ETH + IP6..ETH + IP6 + 2], &[128, 0]);
        assert_eq!(t.gateway, [0x02, 0, 0, 0, 0, 0x01]);
        assert_eq!(t.net64, [0x20, 0x01, 0x06, 0x10, 0x05, 0xea, 0x22, 0x1e]);
    }

    #[test]
    fn sum16_pads_an_odd_byte() {
        assert_eq!(sum16(&[0x01, 0x02, 0x03]), 0x0102 + 0x0300);
    }
}
