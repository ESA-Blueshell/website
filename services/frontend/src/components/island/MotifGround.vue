<script setup lang="ts">
/**
 * The island's repeating pattern, as the ground a page stands on.
 *
 * Two layers, and a page asks for each of them on its own: the shells, and the soft grey light
 * thrown over them. Neither is drawn unless it is wanted, so this same ground serves a page
 * that wants only the pattern, only the light, both, or a plain ground with the bands' washes
 * and nothing behind them. It is laid down once per
 * page rather than once per band, so the shells run unbroken behind whatever the bands do over
 * them. The page sets `--motif-colour` and `--motif-haze` on the element carrying `.motif`;
 * see island.css.
 */
defineOptions({name: "MotifGround"})

withDefaults(defineProps<{
  /** Whether the repeating shells are drawn at all. */
  pattern?: boolean
  /** Whether the soft grey light is drawn over them at all. */
  haze?: boolean
}>(), {pattern: true, haze: false})
</script>

<template>
  <div
    aria-hidden="true"
    class="motif__ground"
  >
    <div
      v-if="pattern"
      class="motif__ink"
    />
    <div
      v-if="haze"
      class="motif__haze"
    />
  </div>
</template>
