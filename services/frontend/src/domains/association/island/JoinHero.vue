<script lang="ts" setup>
import {computed} from "vue"
import {Motion} from "motion-v"
import {useMotionAllowed} from "@/components/island/useMotionAllowed"
import wide from "@/assets/membership/join-2000.webp"
import middle from "@/assets/membership/join-1400.webp"
import narrow from "@/assets/membership/join-800.webp"

/**
 * The membership pitch, over a photograph of the association enjoying itself.
 *
 * Full-bleed and deliberately short of the viewport: the pitch and the way in have to be on
 * screen on a phone without scrolling, so the picture takes about a third of the height rather
 * than all of it. The photograph goes to ground at its foot, so the band under it starts where
 * this one dissolves rather than at a line.
 */
defineOptions({name: "JoinHero"})

withDefaults(defineProps<{
  pitch: string
  action: string
  href: string
  testid?: string
}>(), {testid: "membership-hero"})

const motion = useMotionAllowed()

/** The one thing on the page that moves, and only for a reader who has not asked otherwise. */
const entrance = computed(() => ({
  initial: motion.decorative.value ? {opacity: 0, y: 16} : {opacity: 1},
  animate: {opacity: 1, y: 0},
  transition: {duration: motion.duration(0.5), ease: [0.22, 1, 0.36, 1] as const},
}))

const srcset = `${narrow} 800w, ${middle} 1400w, ${wide} 2000w`
</script>

<template>
  <section
    class="join-hero"
    :data-testid="testid"
  >
    <img
      alt="Blueshell members singing together at a karaoke night"
      class="join-hero__photo"
      :data-testid="`${testid}-photo`"
      decoding="async"
      height="1800"
      sizes="100vw"
      :src="middle"
      :srcset="srcset"
      width="3150"
    >

    <Motion
      class="join-hero__words"
      v-bind="entrance"
    >
      <p
        class="join-hero__pitch"
        :data-testid="`${testid}-pitch`"
      >
        {{ pitch }}
      </p>
      <router-link
        class="join-hero__cut"
        :data-testid="`${testid}-join`"
        :to="href"
      >
        <span>{{ action }}</span>
      </router-link>
    </Motion>
  </section>
</template>

<style scoped>
/*
 * The picture and the words are one band: the words sit on the foot of the photograph rather
 * than under it, so nothing between the header and the numbers reads as a gap.
 */
.join-hero {
  position: relative;
  isolation: isolate;
  width: 100%;
  /* The colour the picture and its scrim dissolve into, which is the band below's own ground:
     the two meet in a blend rather than on a line, in both themes. */
  background-color: var(--band-ground);
  /* Short enough that the pitch and the button clear the fold on a phone, tall enough that the
     photograph is a hero and not a strip. */
  height: clamp(15rem, 46vh, 27rem);
  overflow: hidden;
}

/*
 * The photograph covers the band and dissolves downwards into the page.
 *
 * The same dissolve the board band uses at its right edge, turned to run down: the picture goes
 * to ground and the band below is already that colour when it arrives.
 */
.join-hero__photo {
  position: absolute;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  max-width: none;
  object-fit: cover;
  /* The singers are left of centre, so a narrow window keeps them rather than the empty wall. */
  object-position: 32% 50%;
  mask-image: linear-gradient(to bottom, #000 0, #000 calc(100% - var(--photo-dissolve)), transparent 100%);
  -webkit-mask-image: linear-gradient(to bottom, #000 0, #000 calc(100% - var(--photo-dissolve)), transparent 100%);
}

/*
 * What the words are read on: near-black rising from the foot, in both themes.
 *
 * A scrim that lightens with the theme takes the contrast out from under near-white ink over a
 * photograph, which is why `--photo-scrim` is one colour in both halves.
 */
.join-hero::after {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background-image: linear-gradient(
    to top,
    color-mix(in oklab, var(--photo-scrim) 90%, transparent) 0%,
    color-mix(in oklab, var(--photo-scrim) 78%, transparent) 32%,
    color-mix(in oklab, var(--photo-scrim) 42%, transparent) 58%,
    transparent 86%
  );
  /* The same dissolve the photograph takes, so the scrim leaves with it: held to the foot it
     would end as a dark strip against the band below, which is what light made obvious. */
  mask-image: linear-gradient(to bottom, #000 0, #000 calc(100% - var(--photo-dissolve)), transparent 100%);
  -webkit-mask-image: linear-gradient(to bottom, #000 0, #000 calc(100% - var(--photo-dissolve)), transparent 100%);
}

.join-hero__words {
  position: absolute;
  z-index: 2;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem 2rem;
  width: 100%;
  max-width: 88rem;
  margin: 0 auto;
  padding: 1.5rem 1.5rem 3.25rem;
}

/* Read in the near-white the scrim is built for, whatever the theme is doing elsewhere. */
.join-hero__pitch {
  max-width: 34rem;
  font-family: var(--font-body);
  font-size: clamp(0.95rem, 2.2vw, 1.25rem);
  line-height: 1.4;
  color: #f2f4f6;
}

/* The one solid button on the page above the fold, cut on the island's own slant. */
.join-hero__cut {
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  overflow: hidden;
  padding: 0.72rem 1.6rem;
  clip-path: polygon(0.7rem 0, 100% 0, calc(100% - 0.7rem) 100%, 0 100%);
  background-color: var(--color-brand);
  font-family: var(--font-display);
  font-size: 0.82rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-void);
  white-space: nowrap;
}

.join-hero__cut::before {
  content: "";
  position: absolute;
  inset: 0;
  background-color: var(--color-acid);
  transform-origin: left center;
  scale: 0 1;
  transition: scale 320ms var(--ease-out-quint);
}

.join-hero__cut > span {
  position: relative;
}

.join-hero__cut:hover::before,
.join-hero__cut:focus-visible::before {
  scale: 1 1;
}

@media (max-width: 767px) {
  .join-hero__words {
    padding: 1.15rem 1.15rem 2.75rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .join-hero__cut::before {
    transition: none;
  }
}
</style>
