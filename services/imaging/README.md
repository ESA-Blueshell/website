# Imaging service

Converts the pictures members upload into WebP, away from the api, so that decoding an uploaded
picture reaches nothing the api can. Why it exists and how it is fenced in is architecture
ADR-014; why an animated banner stays animated is api ADR-029.

Go decodes every picture: PNG, JPEG and still WebP with the standard decoders and
`golang.org/x/image/webp`, a GIF frame by frame with `image/gif`, and an animated WebP frame by
frame by wrapping each frame as a still for `x/image/webp`. Only decoded pixels reach libwebp,
which is linked in statically with cgo and only encodes.

## API

`POST /convert` takes the picture's bytes as the body and answers its WebP, with the size it was
drawn at in `Image-Width` and `Image-Height`. The query names the target:

| Parameter | Meaning |
|---|---|
| `max-edge` | A master: brought under this edge, keeping its shape and never upscaled |
| `width`, `height` | A rendition: drawn at exactly this size |
| `quality` | Lossy, at 0 to 100 |
| `lossless=true` | Lossless, in place of `quality` |

A GIF or an animated WebP comes back animated, with its frames and their durations, at whatever
size is asked for. One whose frames will not all decode comes back as a still of its first frame,
and the service logs why.

| Status | Meaning |
|---|---|
| 200 | The WebP |
| 400 | A query the api should not have sent |
| 413 | A body over the byte limit |
| 422 | A picture the service will not decode, or one over its pixel or frame limits |
| 500 | libwebp refused to encode it |
| 503 | No conversion came free within the wait |

`GET /healthz` answers 204.

## Settings

Every setting is a positive whole number with a default sized for a pod of 1 GiB and one CPU.

| Variable | Default | Meaning |
|---|---|---|
| `IMAGING_LISTEN` | `:8080` | The address it listens on |
| `IMAGING_MAX_BYTES` | 20 MiB | The largest body read |
| `IMAGING_MAX_PIXELS` | 50,000,000 | The largest still, or animation canvas, decoded |
| `IMAGING_MAX_FRAMES` | 1000 | The most frames an animation may have |
| `IMAGING_MAX_ANIMATION_PIXELS` | 1,000,000,000 | The most pixels an animation may draw: its canvas times its frames |
| `IMAGING_WAIT_SECONDS` | 30 | How long a request waits for a free conversion |
| `IMAGING_CONCURRENCY` | 1 | How many conversions run at once |

Every limit is checked from the picture's header or frame table before a pixel is allocated.
An animation holds only its canvas and the frame being drawn, never every frame. The animation
budget is what keeps one conversion inside the api's 90 second wait: at one CPU, a 1920 by 1080
banner of 150 frames, 311 million pixels, takes about 11 seconds as a master, and a 50 megapixel
canvas peaks at about 610 MiB.

## Running it

The root `docker-compose.yml` includes this directory's, and the api's compose service points
`IMAGING_URL` at it. On its own:

```sh
docker build -t imaging services/imaging
docker run --rm --read-only --cap-drop ALL --security-opt no-new-privileges -p 8090:8080 imaging
curl --data-binary @picture.png 'http://localhost:8090/convert?max-edge=2560&quality=82' -o picture.webp
```

Building on the host needs libwebp: `brew install webp` on macOS, `libwebp-dev` on Debian.

## Tests

- **Unit**: `go test ./internal/...`, held to every statement. The image's `test` stage runs it
  against the libwebp the image links and with `cwebp` and `webpmux` installed, which the parity
  tests need, and fails under 100%. `FuzzConvert` runs its seeds with the suite; fuzz it with
  `go test -fuzz FuzzConvert ./internal/convert`.
- **Container**: `integration/` drives a running container over HTTP, as the api does. Start the
  image as above, then `IMAGING_URL=http://localhost:8090 go test -tags integration ./integration`.
- **Through the api**: `ImagingServiceIT` and `ImagingServiceDownIT` in the api's integration
  suite build this Dockerfile with Testcontainers and upload through it.

CI runs all three, plus golangci-lint, govulncheck and hadolint; see `docs/agents/ci.md`.
