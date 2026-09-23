<script lang="ts" setup>
/**
 * A heading band: the band ground under the section's own accent, laid from the top left.
 * At 7% it marks the band; stronger reads as a colour field with type lost on it.
 */
defineOptions({name: "LeadBand"})

withDefaults(defineProps<{
  /** The section's colour, as a token or a colour. Blue where the section has none of its own. */
  accent?: string
  testid?: string
}>(), {accent: "var(--color-brand)", testid: "lead-band"})
</script>

<template>
  <section
    class="lead-band"
    :data-testid="testid"
    :style="{'--accent': accent}"
  >
    <span
      aria-hidden="true"
      class="lead-band__wash"
    />
    <div class="lead-band__inner">
      <slot />
    </div>
    <!-- What runs the full width of the band under its words, such as a strip of posters. -->
    <slot name="bleed" />
  </section>
</template>

<style scoped>
.lead-band {
  position: relative;
  isolation: isolate;
  width: 100%;
  overflow: hidden;
  background-color: var(--band-ground);
}

/* Its own layer rather than a second background on the band, so a caller can give the band a
   picture without the wash going with it. */
.lead-band__wash {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    150deg,
    color-mix(in oklab, var(--accent) 7%, transparent),
    transparent 62%
  );
  pointer-events: none;
}

.lead-band__inner {
  position: relative;
  width: 100%;
  max-width: 88rem;
  margin: 0 auto;
  padding: 2.5rem 1.5rem;
}

@media (max-width: 767px) {
  .lead-band__inner {
    padding: 1.75rem 1.15rem;
  }
}
</style>
