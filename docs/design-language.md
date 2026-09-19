# Design language

What the site looks like, and why. This document is authoritative: where it and a page
disagree, the page is wrong and gets fixed.

It covers the island — the design layer scoped to the `.island` root, described in
[CONTEXT.md](./CONTEXT.md#island). The hundred-odd Vuetify components outside the island
are not governed here and are not being restyled.

## The look, in one line

Flat, square, high contrast. Lines rather than boxes, type rather than ornament, one accent
doing the pointing.

Four things follow from that, and they are rules rather than preferences:

1. **Corners are square.** `--radius-*` is cleared in `island.css`, so `rounded-md` and its
   siblings generate nothing at all. A circle is still a circle: `rounded-full` survives for
   monograms and avatars, which are round objects rather than rounded rectangles.
2. **Edges are hairlines.** A panel is told apart from its ground by a 1px
   `--color-hairline`, not by a shadow and not by a gradient. Shadow is allowed only where
   something genuinely floats above the page and has to be read against unknown content —
   an open menu, a dialog. Nothing that sits *in* the page casts one.
3. **One accent.** `--color-eyebrow` marks the thing being pointed at: the current section,
   the open item, the primary action. A page with three accented things has no accent.
4. **Type carries the page.** Hierarchy comes from size, weight and space, in the faces
   already defined: `--font-display` for headlines, `--font-body` for everything read,
   `--font-bitmap` for small technical labels, `--font-name` for people and boards.

## The angle

The diagonal cut is the site's signature. A signature works by being rare.

**One angled band per page, on that page's focal section.** The home page's hero is angled
because the hero is what the home page is for; the events page angles its next-event block
for the same reason. Every other band on both pages is a rectangle. A page with three
angles has a texture, not a signature.

The angle belongs to bands. It never appears on a card, a button, an avatar, a menu or an
input.

## Colour

The tokens live in `services/frontend/src/styles/island.css` and they are semantic: a band
asks for `--color-surface`, never for a hex value or a Tailwind palette name like
`bg-zinc-800`. Adding a colour means adding a token and saying what it is for.

Dark is the house look and is tuned first. Light is not an afterthought: every band is
checked in both, because the light half is values-only (`:where([data-theme="light"])
.island`) and a band that hard-codes its own contrast breaks there silently. Both halves
are screenshotted before any surface is called done.

Text meets WCAG AA in both halves. The accent is exempt from *nothing*: `--color-acid` is
1.2:1 on white, which is why the light half resolves the accent to blue.

## Motion

One easing, `--ease-out-quint`, and the vocabulary that already ships: scroll reveals,
card interactions, the switch choreography of the bands. New surfaces adopt that
vocabulary; they do not invent animations of their own.

Every animation respects `useMotionAllowed`. A page that only makes sense once something
has moved is broken for the visitor who asked for less motion.

## Icons

One weight, one colour rule. Bar and band icons inherit `currentColor` so they take
`--color-ash` at rest and brighten with their neighbours on hover.

An icon that hard-codes a fill is a bug, not a variant. Custom SVGs carry `fill` as a
presentation attribute at most, never as an inline `style`, which no stylesheet can beat —
that is exactly how the management icon came to sit pure white among grey ones.

## Chrome

The bar is the island's top edge. It is built on `reka-ui` primitives and these tokens,
with no Vuetify, and it obeys the rules above plus two of its own:

- **A dropdown is exactly as wide as the thing it drops from.** Long labels wrap. A panel
  that grows past its trigger points at nothing.
- **The bar's indicators are drawn, not borrowed.** The dropdown caret is a hairline at the
  stroke weight of the rest of the chrome and rotates on open. No icon-font chevrons.

Navigation entries are declared once, as data, with their permission gates. The desktop bar
and the mobile drawer render that same declaration; neither keeps a list of its own.

## Pages

A public page is built from bands: full-width horizontal sections, composed from
`components/island`, named for the shape they draw rather than the domain that first needed
one. There is no generic `Button`/`Card`/`Container` kit and none is wanted.

Empty states are designed, not omitted. An event with no banner gets a typographic date
block; a band with nothing to show hides itself rather than drawing an empty frame.

## Forms, tables and the management pages

These are not island yet. Until they are, a Vuetify input keeps Vuetify's default styling —
no `variant`, no `density`, no `hide-details` — because a half-restyled control reads worse
than an unstyled one. Nothing cosmetic is worth investing there.

The end state is island field primitives: text, select, date, checkbox, file and their
validation states, built on `reka-ui` and these tokens, with tables and the management cards
on top of them. That work follows the public pages and is judged on the demo pages below
before it reaches a real form.

## Demo pages

`/design/*` renders every primitive, field, table and card in both themes, and exists so a
component can be argued about in isolation rather than inside the page that needed it. The
routes are registered only in development, so nothing of it ships.

A primitive lands on a demo page in the same change that introduces it. A kit whose gallery
lags behind it is a kit nobody can review.

## Changing this document

A new rule lands here before it lands in a page, so that the next page has something to be
judged against. A rule that cannot be stated in a sentence is a preference, and preferences
belong in the review of a single surface rather than in here.
