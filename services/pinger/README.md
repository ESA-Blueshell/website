# Pinger

Paints the image the association chose on the [SNTPings](https://pings.utwente.io/) canvas, and
serves a public page that shows how it is going at `pings.esa-blueshell.nl`. The prefix, rate,
image and its box are owned by the api and set from the main site; this process only sends and
shows. Why it is Go and runs on the host network:
[architecture ADR-012](../../docs/adr/architecture/ADR-012-the-pinger-is-go-on-the-host-network.md).

## Terms

- **Canvas**: the event's 3840 × 2160 screen.
- **Prefix**: the /64 the event announces when it opens. Each address in it paints one
  pixel: `<prefix>:<X>:<Y>:<B><G>:<R><A>`, all in hex.
- **Paint job**: the prefix, rate, image and box the api holds, read from `GET /pinger/paint`.
  The sender fetches the image, fits it into the box and sends one echo request per opaque pixel.
- **Pass**: one echo request for every pixel in the placement, in shuffled order. The sender
  repeats passes, so it repaints whatever others draw over the image.
- **Event window**: Friday 9 October 2026 18:00 to Sunday 11 October 23:59, Amsterdam time.
  Nothing is sent outside it.

## The paint job

The pinger polls the api every couple of seconds and follows what it reads: an empty prefix keeps
it idle, and a new image or box is fetched and placed at once. The rate is one cap shared by every
worker, at most 200,000; the event never replies, so it is the only guard against flooding. A
local send error, such as a full socket buffer, backs that worker off from 1 ms up to 1 s. Set the
paint job on the main site — see the pinger admin page — never here.

Only the running totals live here, in Valkey under the `pinger:` namespace, so a restart resumes
the counts. `redis-cli --scan --pattern 'pinger:*' | xargs redis-cli del` wipes them.

## Running it

```sh
PINGER_TEST_VALKEY=localhost:6379 go test ./...   # point at a running Valkey, or those tests skip
docker compose --profile pinger up pinger         # dry run on the dev stack, http://localhost:8090
```

A dry run (`PINGER_DRY_RUN=1`) sends nothing and ignores the event window, so the page can be tried
before the event. It still reads the paint job from the api. Do not set it in the cluster.

| Variable | Default |
| --- | --- |
| `PINGER_LISTEN` | `:8090` |
| `PINGER_VALKEY` | `localhost:6379`, for the running totals |
| `PINGER_API_URL` | `http://localhost:8080`, where the paint job is read from |

The page is server-rendered Go with a hand-written island stylesheet, the site's fonts and the
shell tile, all embedded through `go:embed`. There is no Node or CSS build step. A WebSocket on
`/ws` pushes the live state a few times a second and the page binds it into the DOM; it falls back
to fetching `/live.json` when the socket cannot hold. The rest is plain HTML.

## The helper

`cmd/ping` is the distributable helper members run to paint from their own machines. It reads the
same paint job from the api, so it needs no configuration. See [its README](cmd/ping/README.md).
