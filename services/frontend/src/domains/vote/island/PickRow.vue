<script lang="ts" setup>
/**
 * One place on a ballot or in the results: its number, drawn large in the place's own colour, the
 * game's cover, its name with the track under it, what the place is worth, and what the row offers
 * at its end. A place nobody has filled yet shows its slot's own words instead of a game. Cut on
 * both sides at a list row's 12°, so its cut is its own height times tan 12° (docs/design-language.md).
 */
import {computed} from "vue"
import type {Image} from "@/services/api"
import {imageAttrs} from "../adapters/vote"

defineOptions({name: "VotePickRow"})

const {place, cover = null, game = null, track = null, trackHref = undefined, lazy = false, dragging = false} = defineProps<{
  place: number
  cover?: Image | null
  /** The game, or null for a place still free. */
  game?: string | null
  track?: string | null
  /** Where the track's name links to, if it links anywhere. */
  trackHref?: string
  points: number
  /** Whether the cover waits until it is scrolled near, for a long list. */
  lazy?: boolean
  /** Whether the row is held by a pointer, lifted over the others. */
  dragging?: boolean
}>()

// Gold, silver and bronze for the podium, then the blue and the green, so no one colour runs the list.
const RANKS = ["var(--color-warning)", "var(--color-ash)", "var(--color-game-league)", "var(--color-brand-ink)", "var(--color-ok)"]
const rank = computed<string>(() => RANKS[place - 1] ?? "var(--color-ash)")
</script>

<template>
  <div
    class="pick-row"
    :class="{'pick-row--free': !game, 'pick-row--far': place > RANKS.length, 'pick-row--dragging': dragging}"
    :style="{'--rank': rank}"
  >
    <span
      class="pick-row__place"
      :aria-label="`Place ${place}`"
    >{{ place }}</span>
    <img
      v-if="game && cover"
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
    <div class="pick-row__words">
      <template v-if="game">
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
      </template>
      <div
        v-else
        class="pick-row__free"
      >
        <slot name="free" />
      </div>
    </div>
    <span class="pick-row__points">
      <span class="pick-row__value">{{ points }}</span>
      <span class="pick-row__unit">pts</span>
    </span>
    <span
      v-if="$slots.end"
      class="pick-row__end"
    ><slot name="end" /></span>
  </div>
</template>

<style scoped>
.pick-row {
  /* The cover's height, 3:4 of its width, and the padding above and below it. */
  --row-h: calc(3rem * 4 / 3 + 1.2rem);
  --cut: calc(var(--row-h) * 0.2126);
  position: relative;
  display: grid;
  grid-template-columns: 3.4rem 3rem minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 0 1rem;
  min-height: var(--row-h);
  padding: 0.6rem calc(var(--cut) + 0.8rem) 0.6rem calc(var(--cut) + 0.4rem);
  clip-path: polygon(var(--cut) 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
  /* Ruled at its foot in the place's colour, which marks the whole place as its own. */
  background:
    linear-gradient(0deg, color-mix(in oklab, var(--rank) 70%, transparent) 0 2px, transparent 2px),
    linear-gradient(100deg, color-mix(in oklab, var(--rank) 22%, transparent) 0, transparent 11rem),
    var(--color-raised);
  color: var(--color-chalk);
  transition: background-color 160ms ease, scale 160ms ease;
}

/* A free place is the ground it waits on, darker than a filled one, under the same rule. */
.pick-row--free {
  background:
    linear-gradient(0deg, color-mix(in oklab, var(--rank) 55%, transparent) 0 2px, transparent 2px),
    var(--color-pit);
}

.pick-row--free .pick-row__words {
  align-self: center;
}

.pick-row--dragging {
  z-index: 1;
  scale: 1.02;
  background:
    linear-gradient(0deg, var(--rank) 0 2px, transparent 2px),
    linear-gradient(100deg, color-mix(in oklab, var(--rank) 30%, transparent) 0, transparent 12rem),
    var(--color-raised);
}

.pick-row__place {
  font-family: var(--font-display);
  font-size: 3rem;
  font-style: italic;
  line-height: 1;
  text-align: center;
  color: var(--rank);
}

.pick-row--far .pick-row__place {
  font-size: 1.9rem;
}

.pick-row--free .pick-row__place {
  color: transparent;
  -webkit-text-stroke: 1.5px color-mix(in oklab, var(--rank) 70%, transparent);
}

.pick-row__cover {
  width: 3rem;
  aspect-ratio: 3 / 4;
  object-fit: cover;
}

.pick-row__cover--none {
  background-color: var(--color-raised);
}

.pick-row--free .pick-row__cover--none {
  background-color: transparent;
  outline: 1.5px dashed var(--color-hairline);
  outline-offset: -1.5px;
}

.pick-row__words {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.pick-row__game {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
}

.pick-row__track {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.85rem;
  color: var(--color-ash);
}

.pick-row__free {
  font-size: 0.9rem;
  color: var(--color-ash);
}

/* What the place is worth, bracketed: ruled above and below in the place's colour and leaning at
   the row's 12°. Skewed rather than clipped, since a clip shaves whichever rule lands on its edge. */
.pick-row__points {
  display: inline-flex;
  align-items: baseline;
  gap: 0.2rem;
  min-width: 4.2rem;
  justify-content: center;
  padding: 0.3rem 0.8rem;
  transform: skewX(-12deg);
  background-color: color-mix(in oklab, var(--rank) 8%, transparent);
  border-block: 1px solid var(--rank);
  color: var(--color-chalk);
}

/* The words stand upright inside the lean. */
.pick-row__points > * {
  display: inline-block;
  transform: skewX(12deg);
}

.pick-row__value {
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-variant-numeric: tabular-nums;
}

.pick-row__unit {
  font-size: 0.7rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.pick-row--free .pick-row__points {
  background-color: transparent;
}

.pick-row__end {
  display: flex;
  align-items: center;
  gap: 0.15rem;
}

@media (max-width: 600px) {
  .pick-row {
    --row-h: calc(2.5rem * 4 / 3 + 1rem);
    grid-template-columns: 2.4rem 2.5rem minmax(0, 1fr) auto;
    gap: 0 0.7rem;
    padding: 0.5rem calc(var(--cut) + 0.4rem) 0.5rem calc(var(--cut) + 0.2rem);
  }

  /* With buttons at the end there is no room beside the name, so the points go under it. */
  .pick-row:has(.pick-row__end) {
    --row-h: calc(2.5rem * 4 / 3 + 1rem + 1.4rem);
    grid-template-areas:
      "place cover words end"
      "place cover points end";
    grid-template-columns: 2.4rem 2.5rem minmax(0, 1fr) auto;
    row-gap: 0.3rem;
  }

  .pick-row:has(.pick-row__end) .pick-row__place {
    grid-area: place;
  }

  .pick-row:has(.pick-row__end) .pick-row__cover {
    grid-area: cover;
  }

  .pick-row:has(.pick-row__end) .pick-row__words {
    grid-area: words;
    align-self: end;
  }

  .pick-row:has(.pick-row__end) .pick-row__points {
    grid-area: points;
    justify-self: start;
    align-self: start;
  }

  .pick-row:has(.pick-row__end) .pick-row__end {
    grid-area: end;
  }

  .pick-row__place {
    font-size: 2.2rem;
  }

  .pick-row--far .pick-row__place {
    font-size: 1.4rem;
  }

  .pick-row__cover {
    width: 2.5rem;
  }

  .pick-row__points {
    min-width: 0;
    padding: 0.25rem 0.6rem;
  }

  .pick-row__value {
    font-size: 1rem;
  }
}
</style>
