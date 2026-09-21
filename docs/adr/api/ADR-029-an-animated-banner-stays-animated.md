# ADR-029: An Animated Banner Stays Animated

## Status
Accepted

## Context

`FileType.EVENT_BANNER` admits `image/gif`, because the endpoint that takes an event
banner does and refusing one would refuse what committees post. Its doc comment says
conversion keeps the first frame, so an animated banner is stored still.

That was never true. `WebpEncoder` shells out to `cwebp`, and `cwebp` cannot read GIF at
all — GIF is `gif2webp`'s format, not `cwebp`'s. A GIF reaching either the upload path
or the rendition path produces the same two lines:

```
Error! Could not process file /srv/storage/scratch-7675175123949037154.tmp
Error! Cannot read input picture file '/srv/storage/scratch-7675175123949037154.tmp'
```

So an upload of a GIF banner is refused with a 400, and the GIF banners already in
storage — uploaded before the WebP master pipeline existed — are retried by
`StoredImageRenditionsBackfill` at every width on every start, forever, because nothing
records that the converter refused them.

Deciding how to fix that means deciding what an animated banner is for. Three answers
were on the table.

**Drop GIF.** Remove `image/gif` from `allowedMediaTypes`, make the doc comment true by
making the format unsupported. Committees get a clear refusal instead of a confusing
400. But a banner is the picture drawn behind an event wherever it is listed, and an
animated one is a thing a committee deliberately made; refusing it is refusing the work,
not the format.

**Keep the first frame.** What the doc comment already claims. One `gif2webp` call per
picture, no frame handling anywhere, and the existing still-rendition ladder applies
unchanged. But the committee's animation is silently discarded, and nothing on screen
says so.

**Keep the animation.** `gif2webp` produces an animated WebP by default, so the master
is one command. The renditions are the cost: `gif2webp` has no resize flag, and the
`libwebp-tools` package this image installs ships only `cwebp`, `dwebp`, `gif2webp`,
`img2webp`, `webpinfo` and `webpmux` — no `anim_dump`. An animated rendition therefore
has to be split into frames, resized frame by frame, and reassembled with the original
durations.

A fourth shape was considered and rejected: animate at the small widths and serve stills
above. It is the cheapest in bytes, but the same banner would animate or not depending
on the viewport, which is a difference a visitor can see and cannot explain.

## Decision

**A banner posted as an animation is served as an animation, at every width.**

- The master is an animated WebP, produced by `gif2webp`. `image/gif` stays in
  `EVENT_BANNER.allowedMediaTypes`, and the doc comment that claims a still is rewritten.
- Renditions are animated too, at all six `LARGE_PUBLIC_IMAGE_WIDTHS`. There is one
  ladder, not an animated one and a still one.
- Resizing an animation is split, resize, reassemble: frames out, each resized, then
  `img2webp` with the original per-frame durations.
- Frames come from two decoders behind one seam, because the two stored formats differ.
  A `.gif` master — the legacy rows — is read by ImageIO, which decodes GIF frames and
  their delays natively. An animated WebP master is read with `webpmux -get frame N`
  and `dwebp`, because ImageIO has no WebP reader; this repo already works around that
  in `WebpDimensions`, which hand-parses the RIFF header for the same reason.
- `derive` reads the stored master, whatever format it is in. The original upload is not
  kept beside it. A rendition stays rebuildable from what storage holds, which is what
  makes a lost storage volume fill itself back in.
- A picture whose frames cannot be decoded or reassembled falls back to a **still
  first-frame rendition**, not to no rendition. A width ladder that exists matters more
  than the animation in it: without one, every visitor downloads the full-size master.
- Animated work does not run on the startup path. `StoredImageRenditionsBackfill` keeps
  the still ladder on `ApplicationReadyEvent` and enqueues one `AsyncJob` per animated
  source (ADR-023's machinery). A still costs one `cwebp` call per width; an animation
  costs a subprocess per frame per width, and a long GIF would hold readiness for as
  long as it took.

## Consequences

An animated banner costs frame-count times what a still costs, in encode time and in
stored bytes, at six widths. That is the price of the decision and it is accepted: the
alternative is either discarding the committee's work or serving every visitor the
full-size master.

There are two frame decoders to keep working, and only one of them is exercised by the
banners in storage today. The legacy `.gif` path stops being reachable once those rows
are re-derived, so both need tests that do not depend on which rows happen to exist.

Animated renditions arrive after the page does. A banner uploaded now has its still
ladder immediately and its animated ladder when the job runs, so the first requests
after an upload may be served the master.

The job is per source rather than one sweep, so one banner that cannot be processed
fails alone, retries through the job machinery, and is visible in the job manager
instead of only in a log line.

## Implementation status

Not implemented. `WebpEncoder` knows only `cwebp`, so both the upload path and the
rendition path still refuse GIF, and the legacy rows still produce the repeated refusal
on every start. The work is tracked as the encoder, pipeline and log-noise issues under
the animated-banner epic.

## Related

- ADR-023: Job Consolidation and Reliable Execution — the `AsyncJob` the animated work runs as
- `docs/CONTEXT.md`, **Banner and Icon** — master, rendition and animated banner
