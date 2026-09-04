<script lang="ts" setup>
/**
 * The band at the head of an island page: an eyebrow, a display heading, a line of body and
 * the accent blob behind them.
 *
 * The box this draws is the one island.css paints, keyed on `island-header`: it hands the
 * page's texture down to whatever band comes next. A page whose head is shaped otherwise — a
 * logo beside the heading, a body written in markdown — fills [head] rather than the strings,
 * and gets the same box.
 */
defineOptions({name: "HeaderBand"})

/**
 * How the blob is drawn: broad and faint, or the closer, stronger one a coloured page carries.
 *
 * Opacity travels with the geometry rather than being its own input, because the light theme
 * fills the blob in at one figure for every page (`--header-blob` in island.css) and only the
 * dark half reads these. Utilities rather than a style binding for the same reason: an inline
 * opacity would outrank that override and light would follow the page instead of the theme.
 */
const BLOBS = {
  broad: "island-header__blob pointer-events-none absolute -top-32 -left-24 h-80 w-[36rem] rounded-full opacity-[0.18] blur-[90px]",
  tight: "island-header__blob pointer-events-none absolute -top-28 -left-20 h-72 w-[34rem] rounded-full opacity-30 blur-[90px]",
} as const

withDefaults(defineProps<{
  /** The smaller line above the heading. */
  eyebrow?: string
  /** The display heading, or its first line where there is a second. */
  heading?: string
  /** The heading's second line, drawn under the first in the association's blue. */
  headingTail?: string
  /** The line under the heading, where the head is one sentence rather than markup. */
  body?: string
  /** The blob's colour: a page carrying one of its own says so, the rest get the blue. */
  accent?: string
  /** Which of the two blobs the page draws. */
  blob?: keyof typeof BLOBS
}>(), {
  eyebrow: "",
  heading: "",
  headingTail: "",
  body: "",
  accent: "var(--color-brand)",
  blob: "broad",
})
</script>

<template>
  <header class="island-header relative isolate overflow-hidden">
    <div
      aria-hidden="true"
      :class="BLOBS[blob]"
      :style="{backgroundColor: accent}"
    />
    <div class="relative mx-auto w-full max-w-6xl px-5 pt-7 pb-6 sm:px-8 sm:pt-9 sm:pb-7">
      <slot name="head">
        <p
          v-if="eyebrow"
          class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase"
        >
          {{ eyebrow }}
        </p>
        <h1
          v-if="heading"
          class="mt-2.5 max-w-2xl font-display text-2xl leading-[1.1] uppercase sm:text-4xl"
        >
          {{ heading }}<template v-if="headingTail">
            <br><span class="text-brand">{{ headingTail }}</span>
          </template>
        </h1>
        <p
          v-if="body"
          class="mt-3 max-w-xl font-body text-sm leading-relaxed text-ash"
        >
          {{ body }}
        </p>
      </slot>
    </div>

    <!-- What the band holds beside its head, which is a dialog the page opens from in here. -->
    <slot />
  </header>
</template>
