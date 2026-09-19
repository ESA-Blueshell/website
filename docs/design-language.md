# Design language

What the site looks like, and why. This document is authoritative: where it and a page
disagree, the page is wrong and gets fixed.

It covers the island, the design layer scoped to the `.island` root, described in
[CONTEXT.md](./CONTEXT.md#island). The hundred-odd Vuetify components outside the island are
not governed here and are not being restyled.

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

Corners are never rounded to soften a box. `--radius-*` is cleared in `island.css`, so
`rounded-md` and its siblings generate nothing. Three things are round on purpose and nothing
else is: a monogram or avatar, a count badge, and third-party chrome that has its own house
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
- **A count badge** is a round blue pill on the heading it counts, not a separate line.
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
art plates or a run of perk bars — never a grid of bordered cards.

Empty states are designed. An event with no poster gets a typographic date plate; a band with
nothing to show hides itself rather than drawing an empty frame.

## Copy

Say what somebody gets, not what they avoid. Present tense, no Oxford comma, no em dashes.
Placeholders are bracketed (`[ 000 ]`) so an unwired number is obvious. Plural where the
domain is plural: sign-ups open, not sign-up opens.

Artwork that already carries a name, a time and a place is not captioned with them again.

## Forms, tables and the management pages

These are not island yet. Until they are, a Vuetify input keeps Vuetify's default styling, no
`variant`, no `density`, no `hide-details`, because a half-restyled control reads worse than an
unstyled one.

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
