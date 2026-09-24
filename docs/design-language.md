# Design language

What the site looks like, and why. This document is authoritative: where it and a page
disagree, the page is wrong and gets fixed.

It covers the island, the design layer scoped to the `.island` root, described in
[CONTEXT.md](./CONTEXT.md#island). The hundred-odd Vuetify components outside the island are
not governed here and are not being restyled.

The surfaces it covers are the public pages (home, about, membership, partners, esports,
boards), the events pages (the upcoming list, one event's page and the archive of past
events) and the organising pages (adding and editing an event, its sign-up form and its
sign-ups).

## The look, in one line

Flat, cut on the diagonal, high contrast. Lines and washes rather than boxes, type rather
than ornament, one accent doing the pointing.

## The cut

Every edge the island draws itself leans the same way. The shape is one polygon, cutting the
vertical edges and leaving the horizontals straight:

```css
clip-path: polygon(var(--cut) 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
```

It is the island's signature, and it is not rationed: buttons take it at `0.7rem`, fee stubs
at `0.9rem`, slices at `30px` (`22px` under 768px), dialogs at `10px`. A slice band interlocks
its panes with `margin-left: calc(var(--cut) * -1)` and draws the seam as a 1.5px hairline
sliver clipped to the same diagonal.

Nothing that carries art is cut. A poster, a banner and the band that holds one stay square
at every width, because the artwork carries the name, the time and the place and a cut makes
them unreadable. The cut stays on buttons, segmented choices and slices.

Corners are never rounded to soften a box. `--radius-*` is cleared in `island.css`, so
`rounded-md` and its siblings generate nothing. Three things are round on purpose and nothing
else is: a monogram or avatar, a count badge and third-party chrome that has its own house
style, which the Discord widget does.

## Grounds

Four grounds, in order of how much of the page they cover:

- **The tile.** The shell laid at `135px 77px` under a flat `--tile-veil`, both rules already
  in `island.css`. It shows through on heading blocks and behind slice bands. It is texture,
  never a backdrop for dense text.
- **`--band-ground`**, `color-mix(in oklab, var(--color-pit) 86%, transparent)`. The default
  for a band carrying content: numbers, fees, partners, posters' feet, the footer.
- **A lead wash.** A heading band takes its own accent at about 7% from the top left over the
  band ground, a different accent per band. It marks a section without shouting; anything
  stronger reads as a colour field.
- **A board wash.** Where a band belongs to something with its own colour, the accent is laid
  as the radial pair `BoardBand` uses. The Discord band takes blurple this way.

Photography dissolves with `mask-image` rather than sitting in a frame, at `--photo-dissolve`.

Image bands stay dark. Slice bands, poster scrollers and poster grids keep the dark token set
in light mode, with their chevrons, edge fades, glows and captions. The dark values are pinned
on the band, as `.band-swipe--pinned` and `.island-dark` already do, so a light page is light
everywhere except where the art is.

Third-party chrome keeps its own palette. The Discord widget looks the same in both themes.

## Seams

`BandRule` marks a band change: a leaning tick run gathering into a fading line, `--rule: 2px`,
negative margin so it costs no height. Mirrored on alternate seams. Bands otherwise alternate
ground rather than borrow borders.

## Buttons and marks

- **The cut button** is the only button shape. Solid is `--color-brand` with `--color-acid`
  sweeping in from the left on hover; plain is an 8% chalk tint under a brand sweep; quiet is
  4% chalk with ash ink. The sweep is `scale: 0 1 → 1 1` over 320ms.
- **Pan chevrons** come from `Timeline.vue`: 26px stroke glyphs at 0.78 opacity scaling to
  1.24, centred on the content they move, with the `--color-ground` edge fade under them.
  They sit over the artwork, not beside the heading.
- **A count badge** is a round blue pill on the heading it counts, not a separate line. Every
  list heading that counts something carries one, and on a phone it follows the heading's
  last word. The exception is a list of games or committees: the reel's rail already shows
  every one of them, so their headings carry no count.
- **Social glyphs** are filled and uncoloured: one `currentColor` fill with the details cut
  out, like the Discord mark.
- Icons inherit `currentColor` and are drawn, never typed: no emoji, and no markup carried in
  data, because a data hole renders as text.

## Type

`--font-display` for headlines and short labels, `--font-body` for everything read,
`--font-bitmap` for small technical labels, `--font-name` for people and boards. Eyebrows are
11px at `0.3em` tracking in `--color-eyebrow`. A heading's second line takes `text-brand` when
it earns the emphasis.

## Colour

Colours are asked for by token, never as a hex value or a Tailwind palette name. Dark is the
house look and is tuned first; light is values-only and every band is checked in both, which
is why the accent resolves to blue there: `--color-acid` is 1.2:1 on white. Text meets AA in
both halves.

The ok colour is the house acid. `--color-ok` is `#a8ff00` in dark and a darker acid in
light, so a tick or a saved notice reads as the house rather than as a stock green.

## Motion

One curve, `cubic-bezier(0.22, 1, 0.36, 1)`, and the durations the components already use:
850ms for a band pass, 620ms for a slice opening, 900ms for a picture settling, 560ms for a
reveal, 320ms for a button sweep. Every animation respects `useMotionAllowed`.

A set of things swapping for another set travels: the arriving set slides in from the side the
visitor pressed while the leaving set slides out, the same pass `BandSwipe` runs when a
timeline stop changes.

## Pages

A public page is built from bands: full-width horizontal sections composed from
`components/island`, named for the shape they draw. There is no `Button`/`Card`/`Container`
kit and none is wanted. A list of things is a slice band, a row of leaning cells, a grid of
art plates or a run of perk bars, never a grid of bordered cards.

**The flick reel** (`FlickReel.vue`) is the list that travels: games and committees, more of
them than fit a slice band. Its slices are laid end to end from their live widths, so the one
nearing the middle grows while the one leaving it shrinks and no gap ever opens. The belt is
endless and drifts slowly until a hand takes it; drag, flick and a sideways trackpad swipe
move it with momentum that settles on a slice, and a slice fades out before it wraps round.
Pan chevrons sit over both ends with the edge fade stretched to the band's height. Under it
runs the rail, one cell per slice showing its icon or its name, the cell of the slice in the
middle lit in its colour; there is no progress marker and no counter. A press on the open
slice follows it; a press on any other brings it to the middle. The reel is an image band and
stays dark in light mode. Positions are written to the slices every frame rather than
rendered, which is what keeps a drag smooth.

The phone mirrors desktop. It carries the same bands, copy and parts; only the layout folds.
A checked list keeps its description under its title at every width.

On a phone a band head keeps its button beside the heading. The heading wraps and the button
stays to its right, aligned to the heading's last line. Buttons keep their own width and never
stretch across the screen.

One way in per band. A band whose widget already offers the action carries no second button
for it.

Empty states are designed. An event with no poster gets a typographic date plate; a band with
nothing to show hides itself rather than drawing an empty frame.

## Copy

Say what somebody gets, not what they avoid. Present tense, no Oxford comma, no em dashes.
Placeholders are bracketed (`[ 000 ]`) so an unwired number is obvious. Plural where the
domain is plural: sign-ups open, not sign-up opens.

Artwork that already carries a name, a time and a place is not captioned with them again.

Words are plain. A heading names what is under it: Preview, Upcoming, Also coming up, Past
events, All upcoming events, Responses, Attendees, Add a sign-up form. No teaser eyebrows such
as "After that", "Missed one?" or "While you are here".

A status line is shown only to the role it concerns. "The event will be hidden until the board
re-approves it" is for a committee member saving an edit; the board and admins see the
Approved tick instead.

## Forms, tables and the management pages

A form not yet on island fields keeps Vuetify's default styling, no `variant`, no `density`, no
`hide-details`, because a half-restyled control reads worse than an unstyled one.

A form on island fields follows these rules:

- **Forms are compact.** A section is a small label over a hairline, not a numbered block.
  There are no explanatory hints under fields. Related fields share a row, and the fields a
  preview shows sit next to that preview.
- **Field panels are attached.** A calendar, a time panel or a `SearchPicker` list hangs flush
  from its field, as wide as the box, with the box's bottom rule as its top edge.
- **A calendar offers Today and Clear; a time panel steps.** The hour and the minute each have
  an up and a down button, the minute stepping by the field's step, with Clear and Now.
- **A save bar is set apart from the footer.** It is raised, on the surface colour, with a
  brand top edge and narrower than the page.
- **Notices are a tint only.** The tone's colour at a low mix over the ground, with no leaning
  bar.

The end state is island field primitives on `reka-ui` and these tokens, with tables and the
management cards on top of them. That work follows the public pages and is judged on the demo
pages below before it reaches a real form.

## Demo pages

`/design/*` renders every primitive, field, table and card in both themes, so a component can
be argued about outside the page that needed it. The routes register only in development.

A primitive lands on a demo page in the same change that introduces it.

## Changing this document

A new rule lands here before it lands in a page, so the next page has something to be judged
against. A rule that cannot be stated in a sentence is a preference, and preferences belong in
the review of a single surface.
