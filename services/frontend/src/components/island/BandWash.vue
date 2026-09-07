<script setup lang="ts">
/**
 * The ground a band's words sit on.
 *
 * The island is built on a shell tile, and the tile is meant to be seen — but words laid
 * straight on it clash with it. A wash is the answer: a tint between the tile and the text,
 * light enough that the shells still read through it.
 *
 * Three things are chosen separately, because they answer different questions. [tone] is which
 * colour, and every tone is mixed at the same strength so no band shouts louder than another.
 * [shape] is where the wash comes in from: across the whole band, down the middle with the
 * edges left bare, in on the diagonal from the top left, in from the two far corners, or a glow
 * with no edge at all. [width] is whether the band reaches the sides of the window or stops at
 * the column the page reads in.
 *
 * The classes themselves are in island.css, so a page that wants a wash on a band it already
 * has can name them directly rather than wrapping it in this.
 */
export type Tone = "plain" | "brand" | "lime" | "ember" | "amber" | "cool"
export type Shape = "flat" | "centre" | "diagonal" | "corners" | "glow"
export type Width = "full" | "inset"

withDefaults(defineProps<{
  tone?: Tone
  shape?: Shape
  width?: Width
  testid?: string
}>(), {tone: "plain", shape: "centre", width: "full", testid: undefined})
</script>

<template>
  <section
    class="wash w-full"
    :class="[`wash--${tone}`, `wash--${shape}`, `wash--${width}`]"
    :data-testid="testid"
  >
    <div class="wash__inner mx-auto w-full max-w-6xl px-5 py-10 sm:px-8">
      <slot />
    </div>
  </section>
</template>
