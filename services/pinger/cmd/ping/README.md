# Blueshell SNTPings helper

A tiny program that paints the image the association chose onto the [SNTPings](https://pings.utwente.io/)
canvas from your own machine, so lots of us can help at once. It asks the Blueshell site what to
paint — the prefix, the image and where it goes — so there is nothing to set up: run it and it
follows whatever the board set.

## What you need

- **IPv6.** SNTPings only takes IPv6 pings. If your connection has no IPv6 the program says so and
  does nothing harmful. Check at <https://test-ipv6.com/>.

## Run it

Download the file for your system and run it:

```
# macOS (runs without extra rights; with sudo it writes frames itself, past a network filter
# such as Microsoft Defender's, which otherwise holds it to a few thousand pings a second)
./blueshell-pinger-macos-apple

# Linux: fastest given the raw-network right once, which lets it send whole frames itself:
#   sudo setcap cap_net_raw+ep ./blueshell-pinger-linux-amd64
# Without it, if it says permission denied, either run with sudo, or once:
#   sudo sysctl -w net.ipv6.ping_group_range="0 2147483647"
./blueshell-pinger-linux-amd64

# Windows (runs without extra rights; Run as administrator sends faster):
blueshell-pinger-windows-amd64.exe
```

On Linux without that right, a firewall that tracks connections (ufw, firewalld, Docker) takes an
entry for every ping and drops pings once its table fills. The helper warns when this applies;
exempt the pings once with
`sudo ip6tables -t raw -A OUTPUT -p ipv6-icmp --icmpv6-type echo-request -j CT --notrack`.

It prints a line a second: state, packets a second, total sent, passes, errors. Until the board
sets a prefix it just says `idle` and waits. It only sends during the event window (Fri 9 Oct 18:00
to Sun 11 Oct 23:59, Amsterdam). Press Ctrl+C to stop.

## Options

- `-sector 3/10` — paint only your slice of the image, so ten helpers can split the work without
  all painting the same pixels. Everyone uses the same `M`, a different `N`. Leave it off to paint
  the whole image.
- `-rate 128` — packets a second, overriding what the site suggests. Keep it modest. SNTPings makes
  you lose more packets the harder you ping, and **blacklists prefixes that abuse the network** — do
  not crank this up.
- `-anytime` — send even outside the event window, for a local test.
- `-prefix 2001:db8:...::/64` — override the prefix the site reports, for a local test.
- `-server https://esa-blueshell.nl/api` — where to read the paint job from; the default is the
  live site.

## Build it yourself

`./cmd/ping/build.sh` from `services/pinger` cross-compiles the binaries into `dist/`.
