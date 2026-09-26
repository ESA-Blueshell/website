<script lang="ts" setup>
import BackChevrons from "./BackChevrons.vue"

/**
 * The way back to the page above this one, inside a band that has a head of its own: the
 * chevrons and the page's name, as a [BackBar] draws them.
 */
defineOptions({name: "BackLink"})

defineProps<{to: string; label: string; testid?: string}>()
</script>

<template>
  <router-link
    class="back-link"
    :data-testid="testid"
    :to="to"
  >
    <back-chevrons />
    {{ label }}
  </router-link>
</template>

<style scoped>
.back-link {
  display: inline-flex;
  gap: 0.7rem;
  align-items: center;
  font-family: var(--font-display);
  font-size: 0.78rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: color-mix(in oklab, var(--color-chalk) 78%, transparent);
  text-decoration: none;
  transition: color 220ms ease;
}


.back-link:hover,
.back-link:focus-visible {
  color: var(--color-chalk);
}


@media (prefers-reduced-motion: reduce) {
  .back-link {
    transition: none;
  }
}
</style>
