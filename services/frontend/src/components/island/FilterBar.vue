<script lang="ts" setup>
/* The row of filters over a list: its search box, the few pickers the page needs, and a way to
   clear them all once any is set. */
const {active = false, testid = undefined} = defineProps<{
  /** Some filter is set, so clearing them has something to do. */
  active?: boolean
  testid?: string
}>()

const emit = defineEmits<{clear: []}>()
</script>

<template>
  <div
    class="filter-bar"
    :data-testid="testid"
  >
    <slot />
    <button
      v-if="active"
      class="filter-bar__clear"
      :data-testid="testid ? `${testid}-clear` : undefined"
      type="button"
      @click="emit('clear')"
    >
      Clear filters
    </button>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  gap: 0.5rem;
  margin-top: 1.2rem;
}

.filter-bar__clear {
  align-self: center;
  padding: 0.4rem 0.3rem;
  border: 0;
  background: none;
  font: inherit;
  font-size: 0.84rem;
  color: var(--color-brand);
  cursor: pointer;
  white-space: nowrap;
}
</style>
