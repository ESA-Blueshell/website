<script setup lang="ts">
/**
 * The island's repeating pattern, as the ground a page stands on.
 *
 * Two layers: the shells, and the soft grey light thrown over them. It is laid down once per
 * page rather than once per band, so the shells run unbroken behind whatever the bands do over
 * them. The page sets `--motif-colour` and `--motif-haze` on the element carrying `.motif`;
 * see island.css.
 */
defineOptions({name: "MotifGround"})
</script>

<template>
  <div
    aria-hidden="true"
    class="motif__ground"
  >
    <div class="motif__ink" />
    <div class="motif__haze" />
  </div>
</template>
