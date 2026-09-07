<script setup lang="ts">
/**
 * The ground a band's words sit on.
 *
 * The island is built on a shell tile, and the tile is meant to be seen — but words laid
 * straight on it clash with it. A wash is the answer: a tint between the tile and the text,
 * light enough that the shells still read through it.
 *
 * Four things are chosen separately, because they answer different questions. [half] is whether
 * the band's ground is a step lighter or a step darker than the page — the ground only, since
 * the ink stays the page's; [veil] is how much of that ground it lays down over the pattern
 * behind it, and left unset each lays down the share that suits it.
 * [tone] is which
 * colour: the association's blue or its green, read from the tokens, and nothing that is not
 * the association's. Both are mixed at the same strength, so choosing one is not also choosing
 * a loudness.
 * [shape] is which corner the wash comes in from: the top left, the bottom right, both at once,
 * across the whole band on the diagonal, or `pair`, which takes a second colour in at the far
 * corner. Always a corner — a wash centred on the band read as a light behind the words rather
 * than as the band's own ground.
 *
 * Every band is full width. A wash held to the reading column put a straight cut down the
 * middle of the page, which is a harder edge than anything it was covering up.
 *
 * The classes themselves are in island.css, so a page that wants a wash on a band it already
 * has can name them directly rather than wrapping it in this.
 */
export type Tone = "plain" | "brand" | "green"
export type Shape = "plain" | "topleft" | "pair"
/** What a band's ground is: near-white, near-black, or the association's blue. */
export type Half = "light" | "dark" | "blue"
/** How much of its ground the band lays down over the pattern, `none` being no ground at all. */
export type Veil = "none" | "sheer" | "soft" | "firm" | "solid"
/** What that ground is made of, where the half's own is not wanted. */
export type VeilColour = "grey" | "ink"
/** Which edge of the band fades into what comes next, for blending two grounds together. */
export type Fade = "head" | "foot" | "both"
/** What that fade is made of. */
export type FadeInk = "dark" | "light"

withDefaults(defineProps<{
  tone?: Tone
  /** The far corner's colour, for `shape: "pair"`. The near one is used when it is not given. */
  toneAlt?: Tone
  shape?: Shape
  half?: Half
  /** Left unset, the band lays down whatever its half asks for, which is not the same figure. */
  veil?: Veil
  /** Left unset, the veil is the half's own ground rather than a grey or an ink. */
  veilColour?: VeilColour
  /** Which edge fades out. How far it reaches and how strong it is are the page's to set. */
  fade?: Fade
  fadeInk?: FadeInk
  testid?: string
}>(), {
  tone: "plain",
  toneAlt: undefined,
  shape: "topleft",
  half: "dark",
  veil: undefined,
  veilColour: undefined,
  fade: undefined,
  fadeInk: undefined,
  testid: undefined,
})
</script>

<template>
  <section
    class="wash w-full"
    :class="[
      `wash--${tone}`,
      `wash--${shape}`,
      `band--${half}`,
      toneAlt ? `wash-alt--${toneAlt}` : null,
      veil ? `band--${veil}` : null,
      veilColour ? `band--${veilColour}` : null,
      fade ? `wash--fade-${fade}` : null,
      fadeInk ? `wash--fade-${fadeInk}` : null,
    ]"
    :data-testid="testid"
  >
    <div class="wash__inner mx-auto w-full max-w-6xl px-5 py-10 sm:px-8">
      <slot />
    </div>
  </section>
</template>
