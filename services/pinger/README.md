# Pinger

Paints the Blueshell logo on the [SNTPings](https://pings.utwente.io/) canvas, and serves a
page that shows how it is going at `pings.esa-blueshell.nl`. Members can see the page.
Admins can change the settings. Why it is Go and runs on the host network:
[architecture ADR-012](../../docs/adr/architecture/ADR-012-the-pinger-is-go-on-the-host-network.md).

## Terms

- **Canvas**: the event's 3840 × 2160 screen.
- **Prefix**: the /64 the event announces when it opens. Each address in it paints one
  pixel: `<prefix>:<X>:<Y>:<B><G>:<R><A>`, all in hex.
- **Placement**: the logo cropped to its visible pixels, scaled to 600 × 480 and set in the
  bottom-right corner, at x 3240–3839 and y 1680–2159. Transparent pixels are not sent.
- **Pass**: one echo request for every pixel in the placement, in shuffled order. The sender
  repeats passes, so it repaints whatever others draw over the logo.
- **Event window**: Friday 9 October 2026 18:00 to Sunday 11 October 23:59, Amsterdam time.
  Nothing is sent outside it.

## Settings

Admins set these on the page. They live in Valkey (`pinger:prefix`, `pinger:rate_pps` and
`pinger:paused`), and the sender reads them every second.

| Setting | Default | Notes |
| --- | --- | --- |
| Prefix | none | With no prefix the sender stays idle. |
| Rate | 50,000 packets/s | One cap shared by every worker, at most 200,000. The event never replies, so this is the only guard against flooding. |
| Paused | off | Stops sending without clearing the prefix. |

A local send error, such as a full socket buffer, backs that worker off from 1 ms up to 1 s.

## Running it

```sh
go test ./...                                   # the settings tests start Valkey with testcontainers
docker compose --profile pinger up pinger       # dry run on the dev stack, http://localhost:8090
```

A dry run (`PINGER_DRY_RUN=1`) sends nothing, ignores the event window and treats every
visitor as an admin. Do not set it in the cluster.

| Variable | Default |
| --- | --- |
| `PINGER_LISTEN` | `:8090` |
| `PINGER_VALKEY` | `localhost:6379` |
| `PINGER_API_URL` | `http://localhost:8080`, asked on every settings change |
| `PINGER_HOST` | `pings.esa-blueshell.nl`, the host forward-auth gates |

The page is server-rendered Go with a hand-written island stylesheet, the site's fonts and the
shell tile, all embedded through `go:embed`. There is no Node or CSS build step. A WebSocket on
`/ws` pushes the live region once a second and the page swaps it in; between pushes a little
inline script glides the pass bar from the last count and rate. When the socket cannot hold, the
page falls back to fetching `/stats`. The rest is plain HTML.
