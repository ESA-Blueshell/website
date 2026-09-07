<script setup lang="ts">
import arrow from "@/assets/association/arrow-brush.webp"
import flyers from "@/assets/association/placement-flyers.webp"
import newsletter from "@/assets/association/placement-newsletter.webp"
import poster from "@/assets/association/placement-poster.webp"
import jersey from "@/assets/association/placement-jersey.webp"

/**
 * Where a partner's name ends up, shown on the things it ends up on.
 *
 * The association's own artwork, taken from the sponsor pack rather than assembled here: the
 * poster and the newsletter as they were sent out, a photograph of the flyers on the bar, and
 * the jersey mock-up that already carries its own arrows. A frame with "your logo" written in
 * it says the same thing about a space nobody has seen; this says it about a space they can
 * point at.
 */
interface Placement {
  id: string
  picture: string
  alt: string
  /** Where this is, said before the artwork rather than after it. */
  eyebrow: string
  /** What a partner would put there, in the association's own words. */
  label: string
  where: string
  /** Whether the artwork already points at the spot itself. */
  pointsAtItself?: boolean
  /** Whether the artwork is a page rather than a photograph, and stands tall. */
  page?: boolean
}

const PLACEMENTS: Placement[] = [
  {
    id: "flyers",
    picture: flyers,
    alt: "Blueshell flyers spread along the bar at an event",
    eyebrow: "At our events",
    label: "Your flyers at our events",
    where: "Handed out at every event we run, to the people already at the table.",
  },
  {
    id: "newsletter",
    picture: newsletter,
    alt: "A Blueshell newsletter, with the sponsor's logo across the foot of it",
    eyebrow: "On our newsletter",
    label: "Your logo on our newsletter",
    where: "Sent to every member, every month, and read for what is on that month.",
    page: true,
  },
  {
    id: "poster",
    picture: poster,
    alt: "A Blueshell poster, with the sponsor's logo in the corner of it",
    eyebrow: "On our posters",
    label: "Your logo on our posters",
    where: "Up around the campus and in the lounge for as long as they stay up.",
    page: true,
  },
  {
    id: "jersey",
    picture: jersey,
    alt: "A Blueshell esports jersey, with the places a sponsor's logo goes marked on it",
    eyebrow: "On our jerseys",
    label: "Your logo on our jerseys",
    where: "Worn by the teams that play under our name, at every match they play.",
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
        In every room we are in
      </p>
      <h2 class="mt-2.5 max-w-2xl font-display text-2xl leading-[1.1] uppercase sm:text-4xl">
        Be present all across<br>
        <span class="text-brand">our association</span>
      </h2>

      <ul class="placements__grid mt-8">
        <li
          v-for="placement in PLACEMENTS"
          :key="placement.id"
          class="placement"
          :class="{
            'placement--own-arrow': placement.pointsAtItself,
            'placement--page': placement.page,
          }"
          :data-testid="testid ? `${testid}-${placement.id}` : undefined"
        >
          <p class="placement__eyebrow font-body">
            {{ placement.eyebrow }}
          </p>
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

/*
 * Two across rather than four: a poster and a newsletter are pages, and a page a quarter of the
 * page wide is a thumbnail nobody can read. Everything stands taller for the same reason.
 */
.placements__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 2.5rem 2.25rem;
  list-style: none;
}

.placement {
  display: flex;
  flex-direction: column;
}

@media (min-width: 640px) {
  .placements__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.placement__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

/*
 * One box, one shape, for all four.
 *
 * A page is taller than a photograph and the jersey is wider than both, and boxes cut to each
 * left the arrows and the calls beside them at four different heights down the page. Square
 * holds them level, and is tall enough that a poster in it is read rather than glanced at.
 */
.placement__picture {
  width: 100%;
  margin-top: 0.6rem;
  aspect-ratio: 1 / 1;
  object-fit: cover;
  border-radius: 0.9rem;
  background: var(--color-surface);
}

/* A page keeps all of its edges: cropping a poster is cropping what it says. */
.placement--page .placement__picture,
.placement--own-arrow .placement__picture {
  object-fit: contain;
  padding: 0.5rem;
}

/*
 * The jersey's own arrows are white, drawn on nothing. It keeps a dark plate under it in both
 * themes rather than losing them to a pale ground.
 */
.placement--own-arrow .placement__picture {
  background: #0d1015;
}

.placement__call {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-top: 0.9rem;
  min-height: 2.75rem;
}

/*
 * The arrow points up into the picture, which is where the space is. Drawn as the association
 * drew it — it already sweeps up and to the right — and leaned a little further, rather than
 * mirrored, which turned it away from the artwork it is meant to be pointing at.
 */
.placement__arrow {
  width: 3.4rem;
  flex: none;
  transform: rotate(-12deg);
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
