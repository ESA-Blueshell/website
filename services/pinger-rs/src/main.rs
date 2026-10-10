//! pinger-rs sends the Go pinger's paint job as Ethernet frames on Linux. The Go process keeps
//! the api, the share and the watch page; it drives this one over stdin (see `proto`) and reads a
//! `stats` line from stdout every second:
//!
//! ```text
//! stats sent=<n> errors=<n> passes=<n> done=<n> total=<n> failing=<0|1> cpu=<percent> err=<last error>
//! ```
//!
//! Counters run from zero for this process; `err=` is last and runs to the end of the line.

mod engine;
mod frame;
mod pixel;
mod proto;

#[cfg(target_os = "linux")]
mod linux;

#[cfg(not(target_os = "linux"))]
fn main() {
    eprintln!("pinger-rs sends on Linux only");
    std::process::exit(2);
}

#[cfg(target_os = "linux")]
fn main() {
    if let Err(e) = run() {
        eprintln!("pinger-rs: {e}");
        std::process::exit(1);
    }
}

#[cfg(target_os = "linux")]
struct Args {
    mode: linux::Mode,
    workers: usize,
    mmsg_batch: usize,
    synthetic: Option<usize>,
    prefix: Option<String>,
    rate: u64,
    duration: u64,
}

#[cfg(target_os = "linux")]
fn parse_args() -> Result<Args, String> {
    let mut a = Args {
        mode: linux::Mode::Ring,
        workers: 1,
        mmsg_batch: 64,
        synthetic: None,
        prefix: None,
        rate: 0,
        duration: 10,
    };
    let mut it = std::env::args().skip(1);
    while let Some(flag) = it.next() {
        let mut val = || it.next().ok_or(format!("{flag} needs a value"));
        let num = |v: String| v.parse::<u64>().map_err(|_| format!("bad number {v:?}"));
        match flag.as_str() {
            "--mode" => {
                a.mode = match val()?.as_str() {
                    "ring" => linux::Mode::Ring,
                    "mmsg" => linux::Mode::Mmsg,
                    m => return Err(format!("unknown mode {m:?}; ring or mmsg")),
                }
            }
            "--workers" => a.workers = num(val()?)? as usize,
            "--mmsg-batch" => a.mmsg_batch = num(val()?)? as usize,
            "--synthetic" => a.synthetic = Some(num(val()?)? as usize),
            "--prefix" => a.prefix = Some(val()?),
            "--rate" => a.rate = num(val()?)?,
            "--duration" => a.duration = num(val()?)?,
            _ => {
                return Err(format!(
                    "unknown flag {flag:?}\nusage: pinger-rs [--mode ring|mmsg] [--workers N] [--mmsg-batch N]\n       \
                     pinger-rs --synthetic PIXELS --prefix P --rate PPS [--duration S]   (benchmark)"
                ))
            }
        }
    }
    Ok(a)
}

#[cfg(target_os = "linux")]
fn cpu_seconds() -> f64 {
    // SAFETY: getrusage fills ru.
    let mut ru: libc::rusage = unsafe { std::mem::zeroed() };
    unsafe { libc::getrusage(libc::RUSAGE_SELF, &mut ru) };
    let t = |tv: libc::timeval| tv.tv_sec as f64 + tv.tv_usec as f64 / 1e6;
    t(ru.ru_utime) + t(ru.ru_stime)
}

#[cfg(target_os = "linux")]
fn run() -> Result<(), String> {
    use std::io::Write;
    use std::sync::atomic::Ordering::Relaxed;
    use std::sync::Arc;
    use std::time::{Duration, Instant};

    let args = parse_args()?;
    // Die with the Go process even if it never closes stdin.
    // SAFETY: plain prctl.
    unsafe { libc::prctl(libc::PR_SET_PDEATHSIG, libc::SIGTERM) };

    let engine = Arc::new(engine::Engine::new(args.workers));
    let route = Arc::new(linux::Route::default());
    for _ in 0..engine.workers {
        let mut path = linux::FramePath::open(route.clone(), args.mode, args.mmsg_batch)
            .map_err(|e| format!("open packet socket: {e}"))?;
        let e = engine.clone();
        std::thread::spawn(move || e.work(&mut path));
    }
    {
        let (e, r) = (engine.clone(), route.clone());
        std::thread::spawn(move || r.keep_learning(&e));
    }

    if let Some(n) = args.synthetic {
        let prefix = args.prefix.ok_or("--synthetic needs --prefix")?;
        let cmd = proto::read_command(&mut format!("prefix {prefix}\n").as_bytes())
            .map_err(|e| e.to_string())?
            .ok_or("no prefix")?;
        engine.apply(cmd);
        let px = (0..n)
            .map(|i| pixel::Pixel {
                x: (i % 3840) as u16,
                y: (i / 3840 % 2160) as u16,
                a: 0xff,
                ..Default::default()
            })
            .collect();
        engine.apply(proto::Command::Pixels(px));
        engine.rate.store(args.rate, Relaxed);
        // The first second learns the route and fills the ring; measure after it.
        std::thread::sleep(Duration::from_secs(1));
        let (s0, c0, t0) = (engine.sent.load(Relaxed), cpu_seconds(), Instant::now());
        std::thread::sleep(Duration::from_secs(args.duration));
        let (s1, c1, wall) = (
            engine.sent.load(Relaxed),
            cpu_seconds(),
            t0.elapsed().as_secs_f64(),
        );
        println!(
            "result pps={:.0} cpu={:.1}% sent={} errors={} err={}",
            (s1 - s0) as f64 / wall,
            100.0 * (c1 - c0) / wall,
            s1 - s0,
            engine.errors.load(Relaxed),
            engine.last_error.lock().unwrap()
        );
        return Ok(());
    }

    {
        let e = engine.clone();
        std::thread::spawn(move || {
            let mut last = (cpu_seconds(), Instant::now());
            let stdout = std::io::stdout();
            loop {
                std::thread::sleep(Duration::from_secs(1));
                let now = (cpu_seconds(), Instant::now());
                let cpu = 100.0 * (now.0 - last.0) / now.1.duration_since(last.1).as_secs_f64();
                last = now;
                let p = e.progress();
                let err = e.last_error.lock().unwrap().replace('\n', " ");
                let line = format!(
                    "stats sent={} errors={} passes={} done={} total={} failing={} cpu={cpu:.1} err={err}\n",
                    e.sent.load(Relaxed),
                    e.errors.load(Relaxed),
                    e.passes.load(Relaxed),
                    p.done,
                    p.total,
                    e.failing.load(Relaxed) as u8,
                );
                let mut out = stdout.lock();
                if out
                    .write_all(line.as_bytes())
                    .and_then(|_| out.flush())
                    .is_err()
                {
                    std::process::exit(0);
                }
            }
        });
    }

    let mut stdin = std::io::BufReader::with_capacity(1 << 20, std::io::stdin().lock());
    loop {
        match proto::read_command(&mut stdin) {
            Ok(Some(cmd)) => engine.apply(cmd),
            Ok(None) => return Ok(()),
            Err(e) => return Err(format!("control input: {e}")),
        }
    }
}
