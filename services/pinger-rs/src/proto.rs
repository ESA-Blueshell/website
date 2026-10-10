//! The control protocol on stdin. Each command is one text line; `pixels` and `offsets` are
//! followed by their records in binary, all integers big-endian:
//!
//! ```text
//! prefix 2001:db8:1:2::     the /64 to paint; `prefix -` clears it
//! rate 200000               packets a second; 0 idles
//! pixels <n>                then n records of x:u16 y:u16 b g r a:u8 placement:u16
//! offsets <k>               then k records of dx:i32 dy:i32, indexed by placement
//! ```
//!
//! End of input stops the sender, so it never outlives the process that drives it.

use std::io::{self, BufRead};
use std::net::Ipv6Addr;

use crate::pixel::Pixel;

pub const PIXEL_RECORD: usize = 10;
pub const OFFSET_RECORD: usize = 8;
/// Every canvas pixel once; more is a broken driver, not a bigger logo.
const MAX_PIXELS: usize = 3840 * 2160;
const MAX_OFFSETS: usize = 1 << 16;

#[derive(Debug, PartialEq)]
pub enum Command {
    Prefix(Option<[u8; 8]>),
    Rate(u64),
    Pixels(Vec<Pixel>),
    Offsets(Vec<(i32, i32)>),
}

fn invalid(msg: String) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, msg)
}

/// Reads the next command; None at end of input.
pub fn read_command(r: &mut impl BufRead) -> io::Result<Option<Command>> {
    let mut line = String::new();
    loop {
        line.clear();
        if r.read_line(&mut line)? == 0 {
            return Ok(None);
        }
        if !line.trim().is_empty() {
            break;
        }
    }
    let mut words = line.split_whitespace();
    let (verb, arg) = (words.next().unwrap_or(""), words.next().unwrap_or(""));
    let count = |max: usize| -> io::Result<usize> {
        let n: usize = arg
            .parse()
            .map_err(|_| invalid(format!("{verb}: bad count {arg:?}")))?;
        if n > max {
            return Err(invalid(format!("{verb}: {n} is more than {max}")));
        }
        Ok(n)
    };
    match verb {
        "prefix" if arg == "-" => Ok(Some(Command::Prefix(None))),
        "prefix" => {
            let a: Ipv6Addr = arg
                .trim_end_matches("/64")
                .parse()
                .map_err(|_| invalid(format!("prefix: bad address {arg:?}")))?;
            let o = a.octets();
            if o[8..].iter().any(|&b| b != 0) {
                return Err(invalid(format!("prefix: {arg} is not a /64")));
            }
            Ok(Some(Command::Prefix(Some(o[..8].try_into().unwrap()))))
        }
        "rate" => {
            Ok(Some(Command::Rate(arg.parse().map_err(|_| {
                invalid(format!("rate: bad number {arg:?}"))
            })?)))
        }
        "pixels" => {
            let n = count(MAX_PIXELS)?;
            let mut buf = vec![0u8; n * PIXEL_RECORD];
            r.read_exact(&mut buf)?;
            Ok(Some(Command::Pixels(
                buf.as_chunks::<PIXEL_RECORD>()
                    .0
                    .iter()
                    .map(decode_pixel)
                    .collect(),
            )))
        }
        "offsets" => {
            let n = count(MAX_OFFSETS)?;
            let mut buf = vec![0u8; n * OFFSET_RECORD];
            r.read_exact(&mut buf)?;
            let offs = buf
                .as_chunks::<OFFSET_RECORD>()
                .0
                .iter()
                .map(|c| {
                    (
                        i32::from_be_bytes(c[..4].try_into().unwrap()),
                        i32::from_be_bytes(c[4..].try_into().unwrap()),
                    )
                })
                .collect();
            Ok(Some(Command::Offsets(offs)))
        }
        _ => Err(invalid(format!("unknown command {verb:?}"))),
    }
}

fn decode_pixel(c: &[u8; PIXEL_RECORD]) -> Pixel {
    Pixel {
        x: u16::from_be_bytes([c[0], c[1]]),
        y: u16::from_be_bytes([c[2], c[3]]),
        b: c[4],
        g: c[5],
        r: c[6],
        a: c[7],
        placement: u16::from_be_bytes([c[8], c[9]]),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn all(input: &[u8]) -> io::Result<Vec<Command>> {
        let mut r = io::BufReader::new(input);
        let mut out = Vec::new();
        while let Some(c) = read_command(&mut r)? {
            out.push(c);
        }
        Ok(out)
    }

    #[test]
    fn reads_every_command() {
        let mut input = b"prefix 2001:610:5ea:221e::/64\nrate 200000\n\npixels 2\n".to_vec();
        input.extend_from_slice(&[0, 25, 0, 26, 0x00, 0xd1, 0xff, 0xff, 0, 1]);
        input.extend_from_slice(&[0x0e, 0xff, 0x08, 0x6f, 1, 2, 3, 4, 0, 0]);
        input.extend_from_slice(b"offsets 1\n");
        input.extend_from_slice(&(-3i32).to_be_bytes());
        input.extend_from_slice(&7i32.to_be_bytes());
        input.extend_from_slice(b"prefix -\n");
        let got = all(&input).unwrap();
        assert_eq!(
            got,
            vec![
                Command::Prefix(Some([0x20, 0x01, 0x06, 0x10, 0x05, 0xea, 0x22, 0x1e])),
                Command::Rate(200_000),
                Command::Pixels(vec![
                    Pixel {
                        x: 25,
                        y: 26,
                        b: 0,
                        g: 0xd1,
                        r: 0xff,
                        a: 0xff,
                        placement: 1
                    },
                    Pixel {
                        x: 3839,
                        y: 2159,
                        b: 1,
                        g: 2,
                        r: 3,
                        a: 4,
                        placement: 0
                    },
                ]),
                Command::Offsets(vec![(-3, 7)]),
                Command::Prefix(None),
            ]
        );
    }

    #[test]
    fn refuses_what_it_cannot_paint() {
        assert!(all(b"prefix 2001:db8::1\n").is_err());
        assert!(all(b"rate fast\n").is_err());
        assert!(all(b"pixels 99999999999\n").is_err());
        assert!(all(b"pixels 2\n\x00\x01").is_err());
        assert!(all(b"jump\n").is_err());
    }
}
