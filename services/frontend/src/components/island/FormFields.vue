<script lang="ts" setup>
/**
 * The event form's field grid: two columns, one on a phone. A field that needs the whole width
 * carries the `form-span` class.
 */
defineOptions({name: "FormFields"})
</script>

<template>
  <div class="form-fields">
    <slot />
  </div>
</template>

<style scoped>
.form-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.5rem 1.25rem;
  align-items: start;
}

.form-fields > :slotted(.form-span) {
  grid-column: 1 / -1;
}

@media (max-width: 767px) {
  .form-fields {
    grid-template-columns: 1fr;
  }
}
</style>
