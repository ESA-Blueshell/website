//! Passes, pacing and counters, independent of how a packet leaves. It follows the Go sender
//! (paint/sender.go): each pass visits every pixel once in a seeded hash order, a new image keeps
//! the pass where it stood, and the rate is split evenly over the workers.

use std::collections::hash_map::RandomState;
use std::hash::{BuildHasher, Hasher};
use std::sync::atomic::{AtomicBool, AtomicU64, AtomicUsize, Ordering::*};
use std::sync::{Arc, Mutex, RwLock};
use std::thread;
use std::time::{Duration, Instant};

use crate::pixel::{self, Pixel};
use crate::proto::Command;

/// How much of a worker's share of the rate it sends per wake, capped at CHUNK_MAX packets: a wake
/// per few packets costs more than the sends.
const CHUNK_SPAN: Duration = Duration::from_millis(5);
pub const CHUNK_MAX: usize = 2048;
/// How far a worker may fall behind its schedule and still catch up; it also bounds the burst
/// after an idle spell.
const PACE_SLACK: Duration = Duration::from_millis(2);
const IDLE_PAUSE: Duration = Duration::from_millis(50);
const FAIL_PAUSE: Duration = Duration::from_millis(20);

/// How packets leave. `send` reports how many of `lows` went out and, when some did not, why;
/// none out and no error means the path is not ready yet, which is neither a send nor a failure.
pub trait Path {
    fn send(&mut self, prefix: &[u8; 8], lows: &[[u8; 8]]) -> (usize, Option<String>);
}

#[derive(Default)]
pub struct Config {
    pub prefix: Option<[u8; 8]>,
    pub pixels: Arc<Vec<Pixel>>,
    /// Grows with every new pixel list, so a pass tells a swapped image apart.
    pub target: u64,
    /// Empty while nothing moves.
    pub offsets: Arc<Vec<(i32, i32)>>,
}

struct Round {
    target: u64,
    pixels: Arc<Vec<Pixel>>,
    prefix: [u8; 8],
    seed: u64,
    order: Vec<u32>,
    next: AtomicUsize,
    done: AtomicUsize,
}

pub struct Engine {
    config: RwLock<Arc<Config>>,
    generation: AtomicU64,
    pub rate: AtomicU64,
    round: Mutex<Option<Arc<Round>>>,
    pub workers: usize,
    pub sent: AtomicU64,
    pub errors: AtomicU64,
    pub passes: AtomicU64,
    pub failing: AtomicBool,
    pub last_error: Mutex<String>,
    pub stop: AtomicBool,
}

pub struct Progress {
    pub done: usize,
    pub total: usize,
}

impl Engine {
    pub fn new(workers: usize) -> Engine {
        Engine {
            config: RwLock::new(Arc::new(Config::default())),
            generation: AtomicU64::new(0),
            rate: AtomicU64::new(0),
            round: Mutex::new(None),
            workers: workers.max(1),
            sent: AtomicU64::new(0),
            errors: AtomicU64::new(0),
            passes: AtomicU64::new(0),
            failing: AtomicBool::new(false),
            last_error: Mutex::new(String::new()),
            stop: AtomicBool::new(false),
        }
    }

    pub fn config(&self) -> Arc<Config> {
        self.config.read().unwrap().clone()
    }

    pub fn apply(&self, cmd: Command) {
        if let Command::Rate(r) = cmd {
            self.rate.store(r, Relaxed);
            return;
        }
        let mut guard = self.config.write().unwrap();
        let old = &**guard;
        let mut next = Config {
            prefix: old.prefix,
            pixels: old.pixels.clone(),
            target: old.target,
            offsets: old.offsets.clone(),
        };
        match cmd {
            Command::Prefix(p) => next.prefix = p,
            Command::Pixels(px) => {
                next.pixels = Arc::new(px);
                next.target += 1;
            }
            Command::Offsets(o) => {
                next.offsets = Arc::new(if o.iter().all(|&d| d == (0, 0)) {
                    Vec::new()
                } else {
                    o
                })
            }
            Command::Rate(_) => unreachable!(),
        }
        *guard = Arc::new(next);
        self.generation.fetch_add(1, Release);
    }

    /// How far the pass over the current image has come.
    pub fn progress(&self) -> Progress {
        let cfg = self.config();
        let done = match &*self.round.lock().unwrap() {
            Some(r) if r.target == cfg.target => r.done.load(Relaxed).min(cfg.pixels.len()),
            _ => 0,
        };
        Progress {
            done,
            total: cfg.pixels.len(),
        }
    }

    /// Sends until `stop` is set: one worker, pacing itself to its share of the rate.
    pub fn work(&self, path: &mut dyn Path) {
        let mut cfg = self.config();
        let mut seen = self.generation.load(Acquire);
        let mut pacer = Pacer::default();
        let mut lows: Vec<[u8; 8]> = Vec::with_capacity(CHUNK_MAX);
        while !self.stop.load(Relaxed) {
            let g = self.generation.load(Acquire);
            if g != seen {
                cfg = self.config();
                seen = g;
            }
            let rate = self.rate.load(Relaxed);
            let Some(prefix) = cfg.prefix.filter(|_| rate > 0 && !cfg.pixels.is_empty()) else {
                pacer.reset();
                thread::sleep(IDLE_PAUSE);
                continue;
            };
            let share = rate as f64 / self.workers as f64;
            pacer.wait();
            lows.clear();
            let (round, skipped) = self.claim(&cfg, prefix, chunk_size(share), &mut lows);
            let (ok, err) = if lows.is_empty() {
                (0, None)
            } else {
                path.send(&prefix, &lows)
            };
            pacer.book(ok + skipped, share);
            round.done.fetch_add(ok + skipped, Relaxed);
            self.sent.fetch_add(ok as u64, Relaxed);
            let failed = lows.len() - ok;
            if failed > 0 && err.is_some() {
                self.errors.fetch_add(failed as u64, Relaxed);
            }
            if ok > 0 || lows.is_empty() {
                if self.failing.load(Relaxed) {
                    self.failing.store(false, Relaxed);
                }
                continue;
            }
            // Nothing went out: a broken path, or one still learning its route.
            if let Some(e) = err {
                self.failing.store(true, Relaxed);
                *self.last_error.lock().unwrap() = e;
                thread::sleep(FAIL_PAUSE);
            } else {
                thread::sleep(Duration::from_millis(10));
            }
            pacer.reset();
        }
    }

    /// Claims up to n pixels of the current pass and writes the low address bits of the ones still
    /// on the canvas into `lows`, returning the round and how many it skipped. A claim never spans
    /// two passes, so a short one ends each pass.
    fn claim(
        &self,
        cfg: &Config,
        prefix: [u8; 8],
        n: usize,
        lows: &mut Vec<[u8; 8]>,
    ) -> (Arc<Round>, usize) {
        loop {
            let cur = self.round.lock().unwrap().clone();
            let r = match cur {
                Some(r) if r.target == cfg.target && r.prefix == prefix => r,
                stale => {
                    self.roll(stale.as_ref(), cfg, prefix, false);
                    continue;
                }
            };
            let from = r.next.fetch_add(n, Relaxed);
            if from >= r.order.len() {
                self.roll(Some(&r), cfg, prefix, true);
                continue;
            }
            let part = &r.order[from..(from + n).min(r.order.len())];
            let mut skipped = 0;
            if cfg.offsets.is_empty() {
                lows.extend(part.iter().map(|&i| r.pixels[i as usize].low64()));
            } else {
                for &i in part {
                    match r.pixels[i as usize].shifted(&cfg.offsets) {
                        Some(p) => lows.push(p.low64()),
                        None => skipped += 1,
                    }
                }
            }
            return (r, skipped);
        }
    }

    /// Replaces the spent or stale round `old` with a fresh one over the current image, counting a
    /// pass when it ran out. A round made stale by a new image keeps its seed and resumes past the
    /// last pixel it reached. Of the workers that find `old` spent, only the first rolls.
    fn roll(&self, old: Option<&Arc<Round>>, cfg: &Config, prefix: [u8; 8], finished: bool) {
        let mut cur = self.round.lock().unwrap();
        let same = match (&*cur, old) {
            (Some(a), Some(b)) => Arc::ptr_eq(a, b),
            (None, None) => true,
            _ => false,
        };
        if !same {
            return;
        }
        if finished {
            self.passes.fetch_add(1, Relaxed);
        }
        let carry = old.filter(|r| !finished && r.prefix == prefix);
        let seed = carry.map_or_else(random_seed, |r| r.seed);
        let order = pixel::order(seed, &cfg.pixels);
        let mut from = 0;
        if let Some(r) = carry {
            let at = r.next.load(Relaxed).min(r.order.len());
            if at > 0 {
                let last = r.pixels[r.order[at - 1] as usize];
                let reached = pixel::rank(r.seed, last.x, last.y);
                from = order.partition_point(|&i| {
                    let p = cfg.pixels[i as usize];
                    pixel::rank(seed, p.x, p.y) <= reached
                });
            }
        }
        *cur = Some(Arc::new(Round {
            target: cfg.target,
            pixels: cfg.pixels.clone(),
            prefix,
            seed,
            order,
            next: AtomicUsize::new(from),
            done: AtomicUsize::new(from),
        }));
    }
}

fn chunk_size(share: f64) -> usize {
    ((share * CHUNK_SPAN.as_secs_f64()) as usize).clamp(1, CHUNK_MAX)
}

fn random_seed() -> u64 {
    let mut h = RandomState::new().build_hasher();
    h.write_u128(Instant::now().elapsed().as_nanos());
    h.finish()
}

/// Holds one worker to its share of the rate on an absolute schedule, so a sleep that overshoots
/// is made up on the next chunk rather than lost.
#[derive(Default)]
struct Pacer {
    next: Option<Instant>,
}

impl Pacer {
    fn wait(&mut self) {
        let now = Instant::now();
        let floor = now.checked_sub(PACE_SLACK).unwrap_or(now);
        let next = self.next.get_or_insert(now);
        if *next < floor {
            *next = floor;
        }
        if *next > now {
            thread::sleep(*next - now);
        }
    }

    fn book(&mut self, packets: usize, share: f64) {
        if let Some(n) = self.next.as_mut() {
            *n += Duration::from_secs_f64(packets as f64 / share);
        }
    }

    fn reset(&mut self) {
        self.next = None;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    const PREFIX: [u8; 8] = [0x20, 0x01, 0x0d, 0xb8, 0, 0, 0, 1];

    /// Records every address and stops the engine after `limit` packets.
    struct Recorder<'a> {
        engine: &'a Engine,
        got: Vec<[u8; 8]>,
        limit: usize,
    }

    impl Path for Recorder<'_> {
        fn send(&mut self, prefix: &[u8; 8], lows: &[[u8; 8]]) -> (usize, Option<String>) {
            assert_eq!(*prefix, PREFIX);
            self.got.extend_from_slice(lows);
            if self.got.len() >= self.limit {
                self.engine.stop.store(true, Relaxed);
            }
            (lows.len(), None)
        }
    }

    fn pixels(n: usize) -> Vec<Pixel> {
        (0..n)
            .map(|i| Pixel {
                x: (i % 3840) as u16,
                y: (i / 3840) as u16,
                a: 0xff,
                ..Default::default()
            })
            .collect()
    }

    fn engine(px: Vec<Pixel>) -> Engine {
        let e = Engine::new(1);
        e.apply(Command::Prefix(Some(PREFIX)));
        e.apply(Command::Pixels(px));
        e.apply(Command::Rate(10_000_000));
        e
    }

    #[test]
    fn every_pass_paints_every_pixel_once() {
        let px = pixels(5000);
        let e = engine(px.clone());
        let mut rec = Recorder {
            engine: &e,
            got: Vec::new(),
            limit: 15_000,
        };
        e.work(&mut rec);
        let first: Vec<_> = rec.got[..5000].to_vec();
        let mut counts: HashMap<[u8; 8], usize> = HashMap::new();
        for l in &first {
            *counts.entry(*l).or_default() += 1;
        }
        assert_eq!(counts.len(), 5000);
        assert!(px.iter().all(|p| counts[&p.low64()] == 1));
        assert!(e.passes.load(Relaxed) >= 2);
        assert_ne!(first, rec.got[5000..10000], "each pass draws a fresh order");
    }

    #[test]
    fn a_new_image_resumes_the_pass() {
        let e = engine(pixels(10_000));
        let mut rec = Recorder {
            engine: &e,
            got: Vec::new(),
            limit: 4000,
        };
        e.work(&mut rec);
        let before = e.progress().done;
        assert!(before >= 4000);
        // The same positions in another colour: the pass carries on rather than starting over.
        let recoloured: Vec<Pixel> = pixels(10_000)
            .into_iter()
            .map(|p| Pixel { r: 9, ..p })
            .collect();
        e.apply(Command::Pixels(recoloured));
        e.stop.store(false, Relaxed);
        let mut rec = Recorder {
            engine: &e,
            got: Vec::new(),
            limit: 1,
        };
        e.work(&mut rec);
        assert!(rec.got.iter().all(|l| l[6] == 9));
        assert!(
            e.progress().done >= before,
            "pass restarted: {} < {before}",
            e.progress().done
        );
        assert_eq!(e.passes.load(Relaxed), 0);
    }

    #[test]
    fn offsets_move_pixels_and_skip_those_off_the_canvas() {
        let px = vec![
            Pixel {
                x: 0,
                y: 0,
                placement: 0,
                ..Default::default()
            },
            Pixel {
                x: 100,
                y: 100,
                placement: 1,
                ..Default::default()
            },
        ];
        let e = engine(px);
        e.apply(Command::Offsets(vec![(-1, 0), (5, 6)]));
        let mut rec = Recorder {
            engine: &e,
            got: Vec::new(),
            limit: 3,
        };
        e.work(&mut rec);
        assert!(rec.got.iter().all(|l| l[..4] == [0, 105, 0, 106]));
    }

    #[test]
    fn holds_the_rate() {
        let e = engine(pixels(1000));
        e.rate.store(50_000, Relaxed);
        let mut rec = Recorder {
            engine: &e,
            got: Vec::new(),
            limit: 25_000,
        };
        let t = Instant::now();
        e.work(&mut rec);
        let secs = t.elapsed().as_secs_f64();
        assert!(
            (0.4..0.7).contains(&secs),
            "25k packets at 50k/s took {secs:.3}s"
        );
    }
}
