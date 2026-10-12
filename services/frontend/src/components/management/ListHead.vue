<template>
  <div class="list-head">
    <h2 class="list-head__title">
      {{ title }}
      <slot name="mark" />
    </h2>
    <p
      v-if="$slots.default"
      class="list-head__note"
    >
      <slot />
    </p>
  </div>
</template>

<script lang="ts" setup>
/* The line over a list further down a page: what it lists, and a note at its end. */
const {title} = defineProps<{title: string}>()
</script>

<style scoped>
.list-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem 2rem;
  margin: 1.5rem 0 0.7rem;
}

.list-head__title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.list-head__note {
  font-size: 0.85rem;
  color: var(--color-ash);
}
</style>
