//! Pixels, the addresses that paint them and the order a pass visits them in. The order is the Go
//! sender's (paint/sender.go `rank`), so a pass resumes the same way whichever sender runs it.

pub const WIDTH: i32 = 3840;
pub const HEIGHT: i32 = 2160;

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Pixel {
    pub x: u16,
    pub y: u16,
    pub r: u8,
    pub g: u8,
    pub b: u8,
    pub a: u8,
    pub placement: u16,
}

impl Pixel {
    /// The low 64 bits of the address that paints this pixel: `<X>:<Y>:<B><G>:<R><A>`.
    pub fn low64(&self) -> [u8; 8] {
        let [x0, x1] = self.x.to_be_bytes();
        let [y0, y1] = self.y.to_be_bytes();
        [x0, x1, y0, y1, self.b, self.g, self.r, self.a]
    }

    /// Moves the pixel by its placement's offset; None when that lands it off the canvas.
    pub fn shifted(self, offsets: &[(i32, i32)]) -> Option<Pixel> {
        let Some(&(dx, dy)) = offsets.get(self.placement as usize) else {
            return Some(self);
        };
        let (x, y) = (self.x as i32 + dx, self.y as i32 + dy);
        if !(0..WIDTH).contains(&x) || !(0..HEIGHT).contains(&y) {
            return None;
        }
        Some(Pixel {
            x: x as u16,
            y: y as u16,
            ..self
        })
    }
}

/// Places a pixel in the pass `seed` orders, by its position alone, so a pixel keeps its place
/// across images and colours.
pub fn rank(seed: u64, x: u16, y: u16) -> u32 {
    let mut z = seed ^ (x as u64) << 16 ^ y as u64;
    z = (z ^ z >> 30).wrapping_mul(0xbf58476d1ce4e5b9);
    z = (z ^ z >> 27).wrapping_mul(0x94d049bb133111eb);
    ((z ^ z >> 31) >> 32) as u32
}

/// The indices of `pixels` in ascending rank, ties by index. A radix sort keeps it linear: a large
/// logo holds millions of pixels and the order is rebuilt on every image change.
pub fn order(seed: u64, pixels: &[Pixel]) -> Vec<u32> {
    let mut keys: Vec<u64> = pixels
        .iter()
        .enumerate()
        .map(|(i, p)| (rank(seed, p.x, p.y) as u64) << 32 | i as u64)
        .collect();
    let mut spare = vec![0u64; keys.len()];
    let mut counts = vec![0usize; 1 << 16];
    for shift in [32, 48] {
        counts.iter_mut().for_each(|c| *c = 0);
        for k in &keys {
            counts[(k >> shift & 0xffff) as usize] += 1;
        }
        let mut pos = 0;
        for c in counts.iter_mut() {
            let n = *c;
            *c = pos;
            pos += n;
        }
        for &k in &keys {
            let b = (k >> shift & 0xffff) as usize;
            spare[counts[b]] = k;
            counts[b] += 1;
        }
        std::mem::swap(&mut keys, &mut spare);
    }
    keys.into_iter().map(|k| k as u32).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn low64_lays_out_x_y_b_g_r_a() {
        let p = Pixel {
            x: 0x19,
            y: 0x0a0b,
            r: 0xff,
            g: 0xd1,
            b: 0x00,
            a: 0xff,
            placement: 0,
        };
        assert_eq!(p.low64(), [0x00, 0x19, 0x0a, 0x0b, 0x00, 0xd1, 0xff, 0xff]);
    }

    #[test]
    fn rank_matches_the_go_sender() {
        // From paint.rank in Go for the same inputs.
        assert_eq!(rank(0, 0, 0), 0);
        assert_eq!(rank(1, 2, 3), 747218536);
        assert_eq!(rank(0xdead_beef_cafe_f00d, 3839, 2159), 1648965805);
    }

    #[test]
    fn order_sorts_by_rank_then_index() {
        let px: Vec<Pixel> = (0..5000u32)
            .map(|i| Pixel {
                x: (i % 3840) as u16,
                y: (i / 3840) as u16,
                ..Default::default()
            })
            .chain(std::iter::once(Pixel::default()))
            .collect();
        let o = order(42, &px);
        assert_eq!(o.len(), px.len());
        let key = |i: u32| ((rank(42, px[i as usize].x, px[i as usize].y) as u64) << 32) | i as u64;
        assert!(o.windows(2).all(|w| key(w[0]) < key(w[1])));
    }

    #[test]
    fn shifted_drops_pixels_moved_off_the_canvas() {
        let p = Pixel {
            x: 10,
            y: 10,
            placement: 1,
            ..Default::default()
        };
        let offs = [(0, 0), (-11, 0)];
        assert_eq!(p.shifted(&offs), None);
        assert_eq!(
            p.shifted(&[(0, 0), (5, -3)]).map(|q| (q.x, q.y)),
            Some((15, 7))
        );
        assert_eq!(p.shifted(&[]), Some(p));
    }
}
