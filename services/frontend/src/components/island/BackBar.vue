<script lang="ts" setup>
import BackChevrons from "./BackChevrons.vue"

/**
 * The way back as a band of its own, for a page whose first band is not a header. The whole band is
 * the link: under the pointer it takes the page's colour and its chevrons reach back to its left
 * edge, running the way it leads; at rest it is the plain band ground.
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
    <span class="back-bar__column">
      <back-chevrons />
      {{ label }}
    </span>
  </router-link>
</template>

<style scoped>
.back-bar {
  display: block;
  overflow: hidden;
  container-type: inline-size;
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

/*
 * The chevrons hang left of the column, so the label lines up with the page's own words below, and
 * under the pointer they reach back to the band's left edge.
 */
@media (min-width: 768px) {
  .back-bar__column > :first-child {
    --reach: calc(max(0px, (100cqw - 72rem) / 2) + 2rem);

    width: 30px;
    margin-left: calc(-30px - 0.7rem);
    transition: width 320ms var(--ease-out-quint), margin-left 320ms var(--ease-out-quint);
  }

  .back-bar:hover .back-bar__column > :first-child,
  .back-bar:focus-visible .back-bar__column > :first-child {
    width: calc(var(--reach) - 0.7rem);
    margin-left: calc(-1 * var(--reach));
  }
}

@media (max-width: 767px) {
  .back-bar__column {
    padding-inline: 1.25rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .back-bar,
  .back-bar__column > :first-child {
    transition: none;
  }
}
</style>
