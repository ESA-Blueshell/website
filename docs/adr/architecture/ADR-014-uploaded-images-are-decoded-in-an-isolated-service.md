# ADR-014: Uploaded Images Are Decoded in an Isolated Service

## Status
Accepted

## Context

Every picture a member uploads for a public page is converted to WebP: brought under its kind's
ceiling as a master, then drawn again at each width it is served at. Until now the api did that
itself. ImageIO read the header, and `cwebp` decoded the picture. `cwebp` runs as a subprocess,
but inside the api's pod, so it reaches what the api reaches.

Decoding is where image libraries break. libpng, libjpeg and libwebp have all shipped memory
safety fixes for crafted files, and a picture is the one input any signed-in member can hand
the api. A decoder that a crafted file takes over, inside the api's pod, finds the api's Vault
token, its database login, the storage volume and every service the api may call.

The libwebp tools are also most of what the api image carries beyond the JRE, and they keep it
from the shell-less image that #2449 builds.

Alternatives considered:

- **imgproxy.** A maintained, sandboxable image server. Lossless WebP, which every icon kind is
  stored as, is a Pro feature (`IMGPROXY_WEBP_COMPRESSION`), and its open source build would
  store icons lossy.
- **libvips or ImageMagick in a service.** Both decode far more formats than the site admits,
  and with them more of the code that breaks. The site accepts PNG, JPEG and WebP stills.
- **`cwebp` in a service.** The same decoders as before, only moved. A subprocess also needs a
  writable filesystem and a shell or libc on the base image.

## Decision

**Uploaded stills are decoded by the imaging service, a separate pod that is handed bytes,
answers bytes and can reach nothing.**

- **Go decodes, libwebp encodes.** The service decodes PNG, JPEG and still WebP with Go's
  standard decoders and `golang.org/x/image/webp`, which are memory safe. Only the decoded pixels
  reach libwebp, linked in with cgo and called the way `cwebp` calls it: the same ARGB or YUV
  import, the same rescaler, the same quality and lossless settings. Given the same pixels, its
  output is byte for byte what the api's `cwebp` wrote, which the unit suite proves against
  `cwebp` itself. Go's JPEG and lossy WebP decoders round some pixels differently from libjpeg
  and libwebp, so a photograph's bytes can differ while its size and quality do not.
- **One static binary on a base with nothing else.** `distroless/static`, with no shell, no
  package manager and no libc, run as its non-root user. No conversion writes a file, so the root
  filesystem is read-only.
- **The pod can reach nothing.** No ServiceAccount token, no service links, every capability
  dropped and the RuntimeDefault seccomp profile. Its NetworkPolicy admits the api's pods, the
  canary's and the primary's, on 8080, and allows no egress at all, DNS included.
- **Limits before decoding.** A body over 20 MiB is refused unread, and a picture of more than
  50 megapixels is refused from its header before a bitmap is allocated for it. One conversion
  runs at a time and the next waits up to 30 seconds, which keeps the decoded picture and
  libwebp's copy of it inside the pod's 1 GiB.
- **The api speaks to it through the conversion port.** `ImagingServiceConverter` implements
  `ImageConverter` wherever `app.imaging.url` names the service, which production and the dev
  stack both do. The tests and a native run leave it blank and convert in process.
- **A service that is down is the site's problem, not the uploader's.** The api answers a still
  upload with 503. A master is converted before anything is stored, so a failed upload leaves no
  record. A picture the service refuses is a 400, as it was.
- **Animations stay in the api for now.** GIFs and animated WebP still go to the in-process
  converter, until the service takes them apart too (#2447).

## Consequences

- An upload makes one request per width as well as one for the master, so a ladder costs one
  decode per width rather than one in all. The master is at most 2560 pixels and already WebP.
- A still upload, and the widths of a WebP that needs no master, now depend on a second pod.
  Startup does not: the renditions backfill swallows the failure and tries again at the next
  start.
- A WebP that is already within its ceiling is stored before its widths are drawn. If the
  service fails between the two, the upload answers 503 and its record is rolled back, but the
  bytes stay in the content-addressed store, where the next upload of the same picture reuses
  them.
- The service is a Go module with its own lint, vulnerability check, unit suite held to every
  statement, container tests and Renovate rules; see `services/imaging/README.md` and
  `docs/agents/ci.md`.
- The api image keeps the libwebp tools until #2447 moves animations, after which #2448 drops
  them.
