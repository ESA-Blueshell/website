<script lang="ts" setup>
/**
 * One game on a ballot or in the results: its place, its cover, its name with the track under it,
 * its points, and what the row offers at its end. Cut on both sides at a list row's 12°, so its cut
 * is its own height times tan 12° (docs/design-language.md).
 */
import type {Image} from "@/services/api"
import {imageAttrs} from "../adapters/vote"

defineOptions({name: "VotePickRow"})

const {track = null, trackHref = undefined, podium = false, lazy = false} = defineProps<{
  place: number
  cover: Image | null
  game: string
  track?: string | null
  /** Where the track's name links to, if it links anywhere. */
  trackHref?: string
  points: number
  /** The top three, whose place stands out. */
  podium?: boolean
  /** Whether the cover waits until it is scrolled near, for a long list. */
  lazy?: boolean
}>()
</script>

<template>
  <div
    class="pick-row"
    :class="{'pick-row--podium': podium}"
  >
    <span class="pick-row__place">{{ place }}</span>
    <img
      v-if="cover"
      v-bind="imageAttrs(cover)"
      alt=""
      class="pick-row__cover"
      :loading="lazy ? 'lazy' : undefined"
      sizes="3rem"
    >
    <span
      v-else
      class="pick-row__cover pick-row__cover--none"
    />
    <span class="pick-row__words">
      <span class="pick-row__game">{{ game }}</span>
      <a
        v-if="track && trackHref"
        class="pick-row__track"
        :href="trackHref"
        rel="noopener"
        target="_blank"
      >{{ track }}</a>
      <span
        v-else-if="track"
        class="pick-row__track"
      >{{ track }}</span>
    </span>
    <span class="pick-row__points">{{ points }}<span class="pick-row__unit">pts</span></span>
    <span class="pick-row__end"><slot name="end" /></span>
  </div>
</template>

<style scoped>
.pick-row {
  /* The cover's height, 3:4 of its width, and the padding above and below it. */
  --row-h: calc(3rem * 4 / 3 + 1.2rem);
  --cut: calc(var(--row-h) * 0.2126);
  display: grid;
  grid-template-columns: 2.6rem 3rem minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 0 1rem;
  min-height: var(--row-h);
  padding: 0.6rem calc(var(--cut) + 0.8rem) 0.6rem calc(var(--cut) + 0.6rem);
  clip-path: polygon(var(--cut) 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
  background-color: var(--band-ground, var(--color-raised));
  color: var(--color-chalk);
}

.pick-row__place {
  font-family: var(--font-display);
  font-size: 1.5rem;
  text-align: center;
  color: var(--color-ash);
}

.pick-row--podium .pick-row__place {
  color: var(--color-acid);
}

.pick-row__cover {
  width: 3rem;
  aspect-ratio: 3 / 4;
  object-fit: cover;
}

.pick-row__cover--none {
  background-color: var(--color-surface);
}

.pick-row__words {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.pick-row__game {
  font-weight: 700;
}

.pick-row__track {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.85rem;
  color: var(--color-ash);
}

.pick-row__points {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.pick-row__unit {
  margin-left: 0.2rem;
  font-size: 0.75rem;
  font-weight: 400;
  color: var(--color-ash);
}

.pick-row__end {
  display: flex;
  gap: 0.25rem;
}

@media (max-width: 600px) {
  .pick-row {
    --row-h: calc(2.5rem * 4 / 3 + 1rem);
    grid-template-columns: 1.8rem 2.5rem minmax(0, 1fr) auto;
    padding: 0.5rem calc(var(--cut) + 0.5rem) 0.5rem calc(var(--cut) + 0.4rem);
  }

  .pick-row__cover {
    width: 2.5rem;
  }
}
</style>
