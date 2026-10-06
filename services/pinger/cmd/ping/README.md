# Blueshell SNTPings helper

A tiny program that paints the Blueshell logo onto the [SNTPings](https://pings.utwente.io/)
canvas from your own machine, so lots of us can help at once. It is the same painting the
server does, run from your connection.

## What you need

- **IPv6.** SNTPings only takes IPv6 pings. If your connection has no IPv6 the program says so and
  does nothing harmful. Check at <https://test-ipv6.com/>.
- The **prefix** SNT announces when the event opens on Friday — see <https://pings.utwente.io/>.

## Run it

Download the file for your system and run it with the announced prefix:

```
# macOS (runs without extra rights)
./blueshell-pinger-macos-apple -prefix 2001:db8:b317:a000::/64

# Linux (if it says permission denied, either run with sudo, or once:
#   sudo sysctl -w net.ipv6.ping_group_range="0 2147483647")
./blueshell-pinger-linux-amd64 -prefix 2001:db8:b317:a000::/64

# Windows: right-click → Run as administrator, then in the window:
blueshell-pinger-windows-amd64.exe -prefix 2001:db8:b317:a000::/64
```

It prints a line a second: state, packets a second, total sent, passes, errors. Press Ctrl+C to
stop. It only sends during the event window (Fri 9 Oct 18:00 to Sun 11 Oct 23:59, Amsterdam).

## Options

- `-rate 128` — packets a second. Keep it modest. SNTPings makes you lose more packets the harder
  you ping, and **blacklists prefixes that abuse the network** — do not crank this up.
- `-sector 3/10` — paint only your slice of the logo, so ten helpers can split the work without
  all painting the same pixels. Everyone uses the same `M`, a different `N`. Leave it off to paint
  the whole logo.
- `-anytime` — send even outside the event window, for a local test against a test prefix.

## Build it yourself

`./cmd/ping/build.sh` from `services/pinger` cross-compiles the binaries into `dist/`.
