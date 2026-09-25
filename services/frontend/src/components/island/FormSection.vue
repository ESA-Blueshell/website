<script lang="ts" setup>
/**
 * One section of an edit form, drawn the way the event form draws its sections: a small spaced
 * heading over the fields, a hairline between sections and none above the first.
 */
defineOptions({name: "FormSection"})

defineProps<{title: string, testid?: string}>()
</script>

<template>
  <section
    class="form-section"
    :data-testid="testid"
  >
    <h2 class="form-section__title">
      {{ title }}
    </h2>
    <slot />
  </section>
</template>

<style scoped>
.form-section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.1rem 0 1.25rem;
  border-top: 1px solid var(--color-hairline);
}

.form-section:first-child {
  padding-top: 0.75rem;
  border-top: 0;
}

.form-section__title {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}
</style>
