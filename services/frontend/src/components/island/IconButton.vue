<script lang="ts" setup>
/**
 * A square icon button for a row's own actions: ash at rest, chalk under the pointer, and red
 * for one that removes something. The label is its name, since the glyph alone says nothing to
 * a reader being told the page.
 */
defineOptions({name: "IconButton"})

const {label, danger = false, disabled = false, testid = undefined} = defineProps<{
  label: string
  danger?: boolean
  disabled?: boolean
  testid?: string
}>()

defineEmits<{click: [event: MouseEvent]}>()
</script>

<template>
  <button
    :aria-label="label"
    class="icon-button"
    :class="{'icon-button--danger': danger}"
    :data-testid="testid"
    :disabled="disabled"
    :title="label"
    type="button"
    @click="$emit('click', $event)"
  >
    <slot />
  </button>
</template>

<style scoped>
.icon-button {
  display: grid;
  flex: none;
  place-items: center;
  width: 2.25rem;
  height: 2.25rem;
  padding: 0;
  color: var(--color-ash);
  cursor: pointer;
  background: none;
  border: 0;
}

.icon-button:hover:not(:disabled),
.icon-button:focus-visible {
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 8%, transparent);
}

.icon-button--danger:hover:not(:disabled),
.icon-button--danger:focus-visible {
  color: var(--color-danger);
}

.icon-button:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.icon-button :deep(svg) {
  width: 16px;
  height: 16px;
}
</style>
