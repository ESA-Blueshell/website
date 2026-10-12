<script lang="ts" setup>
/* The row of filters over a list: the few pickers the page needs, and a way to clear them all once
   any is set. Inside a table's bar Clear filters stands last, after the search. */
import CutButton from "@/components/island/CutButton.vue"

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
    <span
      v-if="active"
      class="filter-bar__clear"
    >
      <cut-button
        small
        :testid="testid ? `${testid}-clear` : undefined"
        tone="quiet"
        @click="emit('clear')"
      >
        Clear filters
      </cut-button>
    </span>
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
  display: flex;
  align-items: center;
}
</style>
