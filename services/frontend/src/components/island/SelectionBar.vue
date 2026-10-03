<script lang="ts" setup>
/* Shown under a list once rows are ticked: how many, what can be done with them, and a way to clear. */
const {count, noun = "selected", testid = undefined} = defineProps<{
  count: number
  noun?: string
  testid?: string
}>()

const emit = defineEmits<{clear: []}>()
</script>

<template>
  <div
    v-if="count > 0"
    class="selection-bar"
    :data-testid="testid"
  >
    <span class="selection-bar__n">{{ count }} {{ noun }}</span>
    <span class="selection-bar__gap" />
    <slot />
    <button
      class="selection-bar__clear"
      :data-testid="testid ? `${testid}-clear` : undefined"
      type="button"
      @click="emit('clear')"
    >
      Clear
    </button>
  </div>
</template>

<style scoped>
.selection-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem 1rem;
  margin-top: 2px;
  padding: 0.75rem 1rem 0.75rem 1.4rem;
  background-color: var(--color-raised);
  box-shadow: inset 0 2px 0 var(--color-brand);
}

.selection-bar__n {
  font-family: "Shellhouse One", sans-serif;
  font-size: 0.95rem;
  text-transform: uppercase;
}

.selection-bar__gap {
  flex-grow: 1;
}

.selection-bar__clear {
  padding: 0.4rem 0.3rem;
  border: 0;
  background: none;
  font: inherit;
  font-size: 0.84rem;
  color: var(--color-ash);
  cursor: pointer;
}
</style>
