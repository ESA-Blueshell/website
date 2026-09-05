<script setup lang="ts">
import arrow from "@/assets/association/arrow-brush.webp"
import flyers from "@/assets/association/placement-flyers.webp"
import newsletter from "@/assets/association/placement-newsletter.webp"
import poster from "@/assets/association/placement-poster.webp"
import jersey from "@/assets/association/placement-jersey.webp"

/**
 * Where a partner's name ends up, shown on the things it ends up on.
 *
 * Photographs of the association's own flyers, newsletter and posters, cropped to the corner
 * where the sponsor block sits, with the brush arrow from the association's own artwork
 * pointing at it. A frame with "your logo" written in it says the same thing about a space
 * nobody has seen; this says it about a space they can point at.
 *
 * The jersey is the one that carries its own arrows, so it is drawn as it was made.
 */
interface Placement {
  id: string
  picture: string
  alt: string
  /** What a partner would put there, in the association's own words. */
  label: string
  where: string
  /** Whether the artwork already points at the spot itself. */
  pointsAtItself?: boolean
}

const PLACEMENTS: Placement[] = [
  {
    id: "flyers",
    picture: flyers,
    alt: "Blueshell flyers spread along the bar at an event",
    label: "Your flyers here",
    where: "Handed out at every event we run",
  },
  {
    id: "newsletter",
    picture: newsletter,
    alt: "The foot of a Blueshell newsletter, where the sponsor's logo sits",
    label: "Your logo here",
    where: "Sent to every member, every month",
  },
  {
    id: "poster",
    picture: poster,
    alt: "The foot of a Blueshell poster, where the sponsor's logo sits",
    label: "Your logo here",
    where: "Up around the campus and in the lounge",
  },
  {
    id: "jersey",
    picture: jersey,
    alt: "A Blueshell esports jersey, with the places a sponsor's logo goes marked",
    label: "Your logo here",
    where: "Worn by the teams that play under our name",
    pointsAtItself: true,
  },
]

defineProps<{testid?: string}>()
</script>

<template>
  <section
    class="placements w-full"
    :data-testid="testid"
  >
    <div class="mx-auto w-full max-w-6xl px-5 py-12 sm:px-8">
      <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
        Where your name goes
      </p>
      <h2 class="mt-2.5 font-display text-2xl uppercase sm:text-4xl">
        Your logo here
      </h2>

      <ul class="placements__grid mt-8">
        <li
          v-for="placement in PLACEMENTS"
          :key="placement.id"
          class="placement"
          :class="{'placement--own-arrow': placement.pointsAtItself}"
          :data-testid="testid ? `${testid}-${placement.id}` : undefined"
        >
          <img
            :alt="placement.alt"
            class="placement__picture"
            loading="lazy"
            :src="placement.picture"
          >
          <p class="placement__call">
            <img
              v-if="!placement.pointsAtItself"
              aria-hidden="true"
              class="placement__arrow"
              :src="arrow"
            >
            <span class="placement__label font-display">{{ placement.label }}</span>
          </p>
          <p class="placement__where font-body">
            {{ placement.where }}
          </p>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.placements {
  background: var(--band-ground);
}

.placements__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
  gap: 2.25rem 1.75rem;
  list-style: none;
}

.placement__picture {
  width: 100%;
  aspect-ratio: 3 / 2;
  object-fit: cover;
  border-radius: 0.9rem;
  background: var(--color-surface);
}

/* The jersey was drawn on nothing and keeps its own ground rather than being cropped to fill. */
.placement--own-arrow .placement__picture {
  object-fit: contain;
}

.placement__call {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-top: 0.75rem;
  min-height: 2.75rem;
}

/*
 * The arrow points back up into the picture, which is where the space is. Mirrored rather than
 * redrawn: the association's own artwork points right, and this needs it pointing up and left.
 */
.placement__arrow {
  width: 3.4rem;
  flex: none;
  transform: scaleX(-1) rotate(-28deg);
}

.placement__label {
  font-size: 1rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-chalk);
  text-decoration: underline;
  text-decoration-thickness: 2px;
  text-underline-offset: 3px;
}

.placement__where {
  margin-top: 0.35rem;
  font-size: 0.8rem;
  line-height: 1.45;
  color: var(--color-ash);
}
</style>
