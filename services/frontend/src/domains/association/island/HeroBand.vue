<script setup lang="ts">
import {computed} from "vue"

/**
 * A photograph across the top of a page, with the words held over its foot.
 *
 * Tall rather than the whole viewport: the pitch has to be readable without scrolling, and a
 * phone browser's own chrome makes `100vh` a promise nobody can keep. The height is the one
 * BoardBand uses, so the pages sit at the same scale.
 *
 * The picture is imported by the page rather than named here, so Vite fingerprints it and the
 * band never has to know where the assets live.
 */
const props = withDefaults(
  defineProps<{
    /** The photograph, at its widest. */
    photo: string
    /** The stored widths, as a `srcset`, where there are any. */
    srcset?: string
    /** What the photograph is of, for somebody who cannot see it. Empty where it is decoration. */
    alt?: string
    eyebrow?: string
    headline: string
    body?: string
    testid?: string
  }>(),
  {srcset: undefined, alt: "", eyebrow: undefined, body: undefined, testid: undefined},
)

const sizes = computed(() => "100vw")
</script>

<template>
  <section
    class="hero-band relative w-full overflow-hidden"
    :data-testid="testid"
  >
    <img
      :alt="props.alt"
      class="hero-band__photo"
      :sizes="sizes"
      :src="props.photo"
      :srcset="props.srcset"
    >
    <!-- The words sit on the photograph, so the photograph is dimmed under them rather than
         the words being given a box of their own. -->
    <div class="hero-band__scrim" />
    <div class="hero-band__words mx-auto w-full max-w-6xl px-5 pb-8 sm:px-8 sm:pb-12">
      <p
        v-if="eyebrow"
        class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase"
      >
        {{ eyebrow }}
      </p>
      <h1 class="mt-2.5 max-w-3xl font-display text-3xl leading-[1.05] uppercase sm:text-6xl">
        <slot name="headline">
          {{ headline }}
        </slot>
      </h1>
      <p
        v-if="body"
        class="mt-4 max-w-xl font-body text-sm leading-relaxed sm:text-base"
      >
        {{ body }}
      </p>
      <div class="mt-6">
        <slot />
      </div>
    </div>
  </section>
</template>

<style scoped>
.hero-band {
  position: relative;
  height: clamp(20rem, 38vw, 36rem);
}

.hero-band__photo,
.hero-band__scrim {
  position: absolute;
  inset: 0;
}

.hero-band__photo {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/*
 * Dark under the words wherever the theme is, because the words are on a photograph rather
 * than on the page: a light theme does not make a photograph light.
 */
.hero-band__scrim {
  background: linear-gradient(
    to top,
    color-mix(in oklab, #0d1319 94%, transparent) 0%,
    color-mix(in oklab, #0d1319 82%, transparent) 34%,
    color-mix(in oklab, #0d1319 40%, transparent) 62%,
    transparent 92%
  );
}

/*
 * Pinned to the foot rather than aligned in a grid: the words are as tall as they are, and a
 * band that clips its own overflow must not be told to guess where they end.
 */
.hero-band__words {
  position: absolute;
  inset-inline: 0;
  bottom: 0;
  /* The scrim is what makes this legible, and it is dark in both themes, so the ink is too. */
  color: #f2f4f6;
}

.hero-band__words :deep(.text-ash) {
  color: #c3c9cf;
}

/* The eyebrow is the acid green everywhere else; over a photograph it needs to be brighter
   than the brightest thing behind it, so it is lifted rather than left to compete. */
.hero-band__words :deep(.text-eyebrow) {
  color: #c8ff4d;
  text-shadow: 0 1px 6px rgb(0 0 0 / 55%);
}

@media (max-width: 767px) {
  .hero-band {
    height: clamp(17rem, 62vw, 24rem);
  }
}
</style>
