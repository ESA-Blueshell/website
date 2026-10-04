<template>
  <label class="row-check">
    <input
      ref="box"
      :aria-label="label"
      :checked="checked"
      class="row-check__box"
      :data-testid="testid"
      type="checkbox"
      @change="emit('toggle')"
    >
    <span
      aria-hidden="true"
      class="row-check__mark"
    >
      <svg
        fill="none"
        viewBox="0 0 12 12"
      >
        <path
          :d="indeterminate ? 'M2.5 6h7' : 'm2.5 6.2 2.3 2.3 4.7-5'"
          stroke="currentColor"
          stroke-width="1.8"
        />
      </svg>
    </span>
  </label>
</template>

<script lang="ts" setup>
/* The tick that selects one row, or every row from the head of a list. Its name is said, not
   shown: the row beside it names it. */
import {onMounted, ref, watch} from "vue"

const {checked, label, indeterminate = false, testid = undefined} = defineProps<{
  checked: boolean
  label: string
  /** Some of what it stands for is selected, not all: drawn as a dash. */
  indeterminate?: boolean
  testid?: string
}>()

const emit = defineEmits<{toggle: []}>()

// The browser keeps this state on the element, not in an attribute.
const box = ref<HTMLInputElement | null>(null)
const mark = () => {
  if (box.value) box.value.indeterminate = indeterminate
}
onMounted(mark)
watch(() => indeterminate, mark)
</script>

<style scoped>
.row-check {
  display: grid;
  width: 1.15rem;
  height: 1.15rem;
}

.row-check__box {
  grid-area: 1 / 1;
  width: 1.15rem;
  height: 1.15rem;
  margin: 0;
  appearance: none;
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  box-shadow: inset 0 0 0 1px var(--color-hairline);
  cursor: pointer;
}

.row-check__box:checked,
.row-check__box:indeterminate {
  background-color: var(--color-brand);
  box-shadow: none;
}

.row-check__box:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}

.row-check__mark {
  grid-area: 1 / 1;
  display: grid;
  place-items: center;
  width: 1.15rem;
  height: 1.15rem;
  color: var(--color-void);
  opacity: 0;
  pointer-events: none;
}

.row-check__box:checked + .row-check__mark,
.row-check__box:indeterminate + .row-check__mark {
  opacity: 1;
}

.row-check__mark svg {
  width: 0.85rem;
  height: 0.85rem;
}
</style>
