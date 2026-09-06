<script lang="ts" setup>
import {figureText, type Figure} from "../numbers"

/**
 * The association in a handful of numbers, across the page.
 *
 * Every figure is drawn the moment the band is: the ones the api counts start on the floors the
 * association publishes about itself and are replaced where the counts land, so there is
 * nothing pulsing on the page that is meant to sell membership. A figure that is a floor rather
 * than a count says so with a `+`. Usually four; a figure the api counted as none is not on the
 * band at all, so the band draws what it is given rather than a fixed set.
 */
defineOptions({name: "NumberBand"})

withDefaults(defineProps<{
  figures: Figure[]
  testid?: string
}>(), {testid: "membership-numbers"})
</script>

<template>
  <section
    class="number-band"
    :data-testid="testid"
  >
    <ul class="mx-auto grid w-full max-w-6xl grid-cols-2 gap-y-7 px-5 py-8 sm:px-8 md:grid-cols-4 md:py-10">
      <li
        v-for="figure in figures"
        :key="figure.id"
        class="number-band__cell"
        :data-testid="`${testid}-${figure.id}`"
      >
        <p
          class="number-band__value"
          :data-exact="figure.exact"
          :data-testid="`${testid}-${figure.id}-value`"
        >
          {{ figureText(figure) }}
        </p>
        <p class="number-band__label">
          {{ figure.label }}
        </p>
      </li>
    </ul>
  </section>
</template>

<style scoped>
/* A band, on the shared band ground: see island.css. */
.number-band {
  width: 100%;
  background-color: var(--band-ground);
}

/*
 * The figures are divided by the slant everything else on the island is cut on, rather than
 * boxed. A rule that leans is the page's own mark; four bordered cards would be anybody's.
 */
.number-band__cell {
  position: relative;
  padding-inline: 1.25rem;
}

.number-band__cell:first-child {
  padding-inline-start: 0;
}

.number-band__cell::before {
  content: "";
  position: absolute;
  top: 0.25rem;
  bottom: 0.25rem;
  left: 0;
  width: 1px;
  background-color: var(--color-hairline);
  transform: skewX(-12deg);
}

.number-band__cell:first-child::before {
  display: none;
}

/* Two to a row on a phone, so the left of each row opens with no rule in front of it. */
@media (max-width: 767px) {
  .number-band__cell:nth-child(odd) {
    padding-inline-start: 0;
  }

  .number-band__cell:nth-child(odd)::before {
    display: none;
  }
}

.number-band__value {
  font-family: var(--font-display);
  font-size: clamp(2.1rem, 6vw, 3.4rem);
  line-height: 1;
  color: var(--color-chalk);
}

.number-band__label {
  margin-top: 0.4rem;
  max-width: 11rem;
  font-family: var(--font-body);
  font-size: 0.82rem;
  line-height: 1.35;
  color: var(--color-ash);
}
</style>
