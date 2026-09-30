<script lang="ts" setup>
/* A column header that sorts its list: the chevron says which way, and only on the column sorted by. */
const {label, direction = null, testid = undefined} = defineProps<{
  label: string
  /** Null when the list is sorted by another column. */
  direction?: "asc" | "desc" | null
  testid?: string
}>()

const emit = defineEmits<{sort: []}>()
</script>

<template>
  <button
    :aria-label="direction ? `${label}, sorted ${direction === 'asc' ? 'ascending' : 'descending'}` : `Sort by ${label}`"
    class="sort-header"
    :class="{'sort-header--on': direction}"
    :data-testid="testid"
    type="button"
    @click="emit('sort')"
  >
    {{ label }}
    <svg
      v-if="direction"
      aria-hidden="true"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      viewBox="0 0 24 24"
    >
      <path :d="direction === 'asc' ? 'm6 14.5 6-6 6 6' : 'm6 9.5 6 6 6-6'" />
    </svg>
  </button>
</template>

<style scoped>
.sort-header {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0;
  border: 0;
  background: none;
  cursor: pointer;
  font: inherit;
  letter-spacing: inherit;
  text-transform: inherit;
  color: inherit;
}

.sort-header:hover,
.sort-header--on {
  color: var(--color-chalk);
}

.sort-header svg {
  width: 13px;
  height: 13px;
}
</style>
