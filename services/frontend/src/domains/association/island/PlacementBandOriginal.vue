<script setup lang="ts">
import originalFlyers from "@/assets/association/original-flyers.webp"
import originalPosterNewsletter from "@/assets/association/original-poster-newsletter.webp"
import jersey from "@/assets/association/placement-jersey.webp"

/**
 * The same band, drawn with the artwork the association already made.
 *
 * Kept beside [PlacementBand] so the two can be looked at together: this one ships the pieces
 * as they were designed, arrows, lettering and all, and the other rebuilds that idea from
 * photographs the page crops itself. The difference is where the words live — baked into the
 * picture here, and in the markup there, where they can be read out, translated and searched.
 */
defineProps<{testid?: string}>()

const PIECES = [
  {
    id: "flyers",
    picture: originalFlyers,
    alt: "Blueshell flyers on the bar, with an arrow pointing at them reading “your flyers here”",
    where: "Handed out at every event we run",
  },
  {
    id: "poster-newsletter",
    picture: originalPosterNewsletter,
    alt: "A Blueshell poster and newsletter, with arrows pointing at where a sponsor's logo goes",
    where: "Up around the campus, and in every member's inbox",
    wide: true,
  },
  {
    id: "jersey",
    picture: jersey,
    alt: "A Blueshell esports jersey, with the places a sponsor's logo goes marked",
    where: "Worn by the teams that play under our name",
  },
]
</script>

<template>
  <section
    class="placements-original w-full"
    :data-testid="testid"
  >
    <div class="mx-auto w-full max-w-6xl px-5 py-12 sm:px-8">
      <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
        Where your name goes
      </p>
      <h2 class="mt-2.5 font-display text-2xl uppercase sm:text-4xl">
        Your logo here
      </h2>

      <ul class="placements-original__grid mt-8">
        <li
          v-for="piece in PIECES"
          :key="piece.id"
          class="piece"
          :class="{'piece--wide': piece.wide}"
          :data-testid="testid ? `${testid}-${piece.id}` : undefined"
        >
          <img
            :alt="piece.alt"
            class="piece__picture"
            loading="lazy"
            :src="piece.picture"
          >
          <p class="piece__where font-body">
            {{ piece.where }}
          </p>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.placements-original {
  background: var(--band-ground);
}

.placements-original__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
  gap: 2.25rem 1.75rem;
  list-style: none;
  align-items: start;
}

/* The poster and the newsletter were drawn as one picture and read badly at a column's width. */
.piece--wide {
  grid-column: span 2;
}

.piece__picture {
  width: 100%;
  height: auto;
}

.piece__where {
  margin-top: 0.6rem;
  font-size: 0.8rem;
  line-height: 1.45;
  color: var(--color-ash);
}

@media (max-width: 767px) {
  .piece--wide {
    grid-column: span 1;
  }
}
</style>
