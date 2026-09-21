<script lang="ts" setup>
/* The input is the control, sized and bared rather than replaced, so the keyboard keeps
   working; the tick is drawn over it. */
import {useId} from "vue"

defineOptions({name: "IslandCheck"})

const {label, hint = "", disabled = false, testid = undefined} = defineProps<{
  label: string
  /** Said under the box, for the line of small print a consent needs. */
  hint?: string
  disabled?: boolean
  testid?: string
}>()

const ticked = defineModel<boolean>({default: false})

const controlId = `${useId()}-check`
</script>

<template>
  <div class="island-check">
    <input
      :id="controlId"
      v-model="ticked"
      class="island-check__box"
      :data-testid="testid"
      :disabled="disabled"
      type="checkbox"
    >
    <span
      aria-hidden="true"
      class="island-check__mark"
    >
      <svg
        fill="none"
        stroke="currentColor"
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="2.4"
        viewBox="0 0 24 24"
      >
        <path d="m5 12.5 4.5 4.5L19 7" />
      </svg>
    </span>

    <label
      class="island-check__say"
      :for="controlId"
    >
      {{ label }}
      <span
        v-if="hint"
        class="island-check__hint"
      >{{ hint }}</span>
    </label>
  </div>
</template>

<style scoped>
.island-check {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: start;
  gap: 0.15rem 0.7rem;
}

.island-check__box {
  grid-area: 1 / 1;
  width: 1.15rem;
  height: 1.15rem;
  margin: 0.1rem 0 0;
  appearance: none;
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  box-shadow: inset 0 0 0 1px var(--color-hairline);
  cursor: pointer;
}

.island-check__box:checked {
  background-color: var(--color-brand);
  box-shadow: none;
}

.island-check__box:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}

.island-check__box:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

/* Over the box rather than inside it: an input draws no children of its own. */
.island-check__mark {
  grid-area: 1 / 1;
  display: grid;
  place-items: center;
  width: 1.15rem;
  height: 1.15rem;
  margin-top: 0.1rem;
  color: var(--color-void);
  opacity: 0;
  pointer-events: none;
}

.island-check__box:checked + .island-check__mark {
  opacity: 1;
}

.island-check__mark svg {
  width: 0.85rem;
  height: 0.85rem;
}

.island-check__say {
  grid-area: 1 / 2;
  font-family: var(--font-body);
  font-size: 0.85rem;
  line-height: 1.45;
  color: var(--color-chalk);
  cursor: pointer;
}

.island-check__hint {
  display: block;
  font-size: 0.72rem;
  color: var(--color-ash);
}
</style>
