<template>
  <p
    aria-level="2"
    class="section-title"
    role="heading"
  >
    <slot />
  </p>
</template>

<script lang="ts" setup>
/* The title over one part of an account page. A paragraph with a heading's role, not an h2: these
   pages stand outside the island, where the site's own heading styles would take an h2 over. */
defineOptions({name: "SectionTitle"})
</script>

<style scoped>
.section-title {
  margin: 0;
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
  line-height: 1.4;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}
</style>
