<script lang="ts" setup>
import BackChevrons from "./BackChevrons.vue"

/**
 * The way back as a band of its own, for a page whose first band is not a header. The whole band is
 * the link: under the pointer it takes the page's colour and a stream of chevrons runs back the way
 * it leads, and at rest it is the plain band ground.
 */
defineOptions({name: "BackBar"})

withDefaults(defineProps<{to: string; label: string; testid?: string; accent?: string}>(), {
  testid: undefined,
  accent: "var(--color-brand)",
})
</script>

<template>
  <router-link
    class="back-bar"
    :data-testid="testid"
    :style="{'--accent': accent}"
    :to="to"
  >
    <span
      aria-hidden="true"
      class="back-bar__stream"
    />
    <span class="back-bar__column">
      <back-chevrons />
      {{ label }}
    </span>
  </router-link>
</template>

<style scoped>
.back-bar {
  position: relative;
  isolation: isolate;
  display: block;
  overflow: hidden;
  font-family: var(--font-display);
  font-size: 0.78rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: color-mix(in oklab, var(--color-chalk) 78%, transparent);
  text-decoration: none;
  background-color: var(--band-ground);
  border-bottom: 1px solid var(--color-hairline);
  transition: color 220ms ease, background-color 220ms ease;
}

/* Chevrons pointing back, drawn by a mask so they take the page's colour, in the margin before the
   way back and fading out before it. */
.back-bar__stream {
  position: absolute;
  inset: 0;
  z-index: -1;
  background-color: var(--accent);
  opacity: 0;
  mask-image:
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 26 14'%3E%3Cpath d='M7.5 1.5 2 7l5.5 5.5' fill='none' stroke='%23000' stroke-width='1.6'/%3E%3C/svg%3E"),
    linear-gradient(90deg, #000 0%, #000 calc(50% - 41rem), transparent calc(50% - 36rem));
  mask-size: 26px 14px, 100% 100%;
  mask-repeat: repeat-x, no-repeat;
  mask-position: 0 50%, 0 0;
  mask-composite: intersect;
  transition: opacity 220ms ease;
}

.back-bar__column {
  display: flex;
  gap: 0.7rem;
  align-items: center;
  width: 100%;
  max-width: 72rem;
  margin: 0 auto;
  padding: 0.85rem 2rem;
}


.back-bar:hover,
.back-bar:focus-visible {
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--accent) 10%, var(--band-ground));
}

.back-bar:hover .back-bar__stream,
.back-bar:focus-visible .back-bar__stream {
  opacity: 0.35;
  animation: back-bar-stream 900ms linear infinite;
}


@keyframes back-bar-stream {
  from {
    mask-position: 0 50%, 0 0;
  }

  to {
    mask-position: -26px 50%, 0 0;
  }
}

/* The chevrons hang left of the column, so the label lines up with the page's own words below. */
@media (min-width: 768px) {
  .back-bar__column > :first-child {
    margin-left: calc(-30px - 0.7rem);
  }
}

@media (max-width: 767px) {
  .back-bar__column {
    padding-inline: 1.25rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .back-bar,
  .back-bar__stream {
    transition: none;
  }

  .back-bar:hover .back-bar__stream,
  .back-bar:focus-visible .back-bar__stream {
    animation: none;
  }
}
</style>
