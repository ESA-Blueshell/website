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
  and with them more of the code that breaks. The site accepts PNG, JPEG, WebP and GIF.
- **`cwebp` in a service.** The same decoders as before, only moved. A subprocess also needs a
  writable filesystem and a shell or libc on the base image.

## Decision

**Uploaded pictures are decoded by the imaging service, a separate pod that is handed bytes,
answers bytes and can reach nothing.**

- **Go decodes, libwebp encodes.** The service decodes PNG, JPEG and still WebP with Go's
  standard decoders and `golang.org/x/image/webp`, which are memory safe. Only the decoded pixels
  reach libwebp, linked in with cgo and called the way `cwebp` calls it: the same ARGB or YUV
  import, the same rescaler, the same quality and lossless settings. Given the same pixels, its
  output is byte for byte what the api's `cwebp` wrote, which the unit suite proves against
  `cwebp` itself. Go's JPEG and lossy WebP decoders round some pixels differently from libjpeg
  and libwebp, so a photograph's bytes can differ while its size and quality do not.
- **Animations are decoded by Go too.** A GIF's frame table is read without decoding it, and each
  frame is decoded alone by `image/gif`. An animated WebP's frames are each wrapped as a still
  and decoded by `x/image/webp`, which checks the frame's bitstream against the size its header
  gives before it allocates. Frames are replayed onto one canvas, with their offsets, blending and
  disposal, and each canvas goes to libwebp's animation encoder with the frame's duration. The
  encoder may store a frame as the rectangle that changed, which the api's own reader could not
  replay but this one does (api ADR-029). A frame that will not decode, or a frame table that
  breaks off, makes the picture a still of its first frame.
- **One static binary on a base with nothing else.** `distroless/static`, with no shell, no
  package manager and no libc, run as its non-root user. No conversion writes a file, so the root
  filesystem is read-only.
- **The pod can reach nothing.** No ServiceAccount token, no service links, every capability
  dropped and the RuntimeDefault seccomp profile. Its NetworkPolicy admits the api's pods, the
  canary's and the primary's, on 8080, and allows no egress at all, DNS included.
- **Limits before decoding.** A body over 20 MiB is refused unread, and a picture or animation
  canvas of more than 50 megapixels is refused from its header before a bitmap is allocated for
  it. An animation of more than 1000 frames, or whose frames times its canvas come to more than a
  billion pixels, is refused from its frame table; that budget is what keeps the longest
  conversion inside the api's 90 second wait. An animation holds its canvas and one frame, never
  every frame. One conversion runs at a time and the next waits up to 30 seconds, which keeps
  the decoded picture and libwebp's copy of it inside the pod's 1 GiB: a 50 megapixel animation
  peaks at about 610 MiB.
- **The api decodes nothing itself.** `ImagingServiceConverter` is the only `ImageConverter`,
  and the api reads a picture's size only from the WebP header the service hands back. A file
  stored as it was sent, in a format other than WebP, has no size. `app.imaging.url` defaults to
  the service's name on the compose network and in the cluster's namespace. The integration
  tests and CI's system-test stack run the image as the cluster does, and a native run points
  the api at a container it started. The api image carries only the JRE.
- **A service that is down is the site's problem, not the uploader's.** The api answers an
  upload with 503. A master is converted before anything is stored, so a failed upload leaves no
  record. A picture the service refuses is a 400, as it was. An animation's widths are a job,
  which fails and is retried with backoff.

## Consequences

- An upload makes one request per width as well as one for the master, so a ladder costs one
  decode per width rather than one in all. The master is at most 2560 pixels and already WebP.
- An upload, and the widths of a WebP that needs no master, now depend on a second pod. Startup
  does not: the renditions backfill swallows the failure and tries again at the next start.
- An animation's widths hold the one conversion slot for a request each, so a still upload made
  while they are drawn can wait for its turn, and is turned away with 503 past the 30 seconds.
- An animation the service draws can store a frame as the rectangle that changed, so its bytes
  can be smaller than the api's were, and the api's in-process reader cannot take it apart.
- A WebP that is already within its ceiling is stored before its widths are drawn. If the
  service fails between the two, the upload answers 503 and its record is rolled back, but the
  bytes stay in the content-addressed store, where the next upload of the same picture reuses
  them.
- The service is a Go module with its own lint, vulnerability check, unit suite held to every
  statement, container tests and Renovate rules; see `services/imaging/README.md` and
  `docs/agents/ci.md`.
- Every job that boots the api and uploads a picture needs the service running: CI's
  integration shards and system tests start it from the layers main's image build caches, and
  the integration tests start it with Testcontainers where CI has not.
