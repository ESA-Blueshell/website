<script setup lang="ts">
/**
 * The ground a band's words sit on.
 *
 * The island is built on a shell tile, and the tile is meant to be seen — but words laid
 * straight on it clash with it. A wash is the answer: a tint between the tile and the text,
 * light enough that the shells still read through it.
 *
 * Two things are chosen separately, because they answer different questions. [tone] is which
 * colour, and every tone is mixed at the same strength so no band shouts louder than another.
 * [shape] is which corner the wash comes in from: the top left, the bottom right, both at once,
 * or across the whole band on the diagonal. Always a corner — a wash centred on the band read
 * as a light behind the words rather than as the band's own ground.
 *
 * Every band is full width. A wash held to the reading column put a straight cut down the
 * middle of the page, which is a harder edge than anything it was covering up.
 *
 * The classes themselves are in island.css, so a page that wants a wash on a band it already
 * has can name them directly rather than wrapping it in this.
 */
export type Tone = "plain" | "sky" | "mint" | "lime" | "lemon" | "coral" | "lilac"
export type Shape = "topleft" | "bottomright" | "corners" | "diagonal"

withDefaults(defineProps<{
  tone?: Tone
  shape?: Shape
  testid?: string
}>(), {tone: "plain", shape: "topleft", testid: undefined})
</script>

<template>
  <section
    class="wash w-full"
    :class="[`wash--${tone}`, `wash--${shape}`]"
    :data-testid="testid"
  >
    <div class="wash__inner mx-auto w-full max-w-6xl px-5 py-10 sm:px-8">
      <slot />
    </div>
  </section>
</template>
