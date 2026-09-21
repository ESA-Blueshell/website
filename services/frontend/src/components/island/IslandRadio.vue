<script lang="ts" setup>
/* One of a few, all of them in front of the reader: a radio group, not a list to open. */
import {useId} from "vue"

defineOptions({name: "IslandRadio"})

const {options, name = undefined, disabled = false, testid = undefined} = defineProps<{
  options: Array<{key: string; label: string; hint?: string}>
  /** What the browser groups them by; one is made up where a form gives none. */
  name?: string
  disabled?: boolean
  testid?: string
}>()

const picked = defineModel<string | null>({default: null})

const group = name ?? `${useId()}-radio`
const idFor = (key: string) => `${group}-${key}`
</script>

<template>
  <div
    class="island-radio"
    :data-testid="testid"
  >
    <div
      v-for="one in options"
      :key="one.key"
      class="island-radio__row"
    >
      <input
        :id="idFor(one.key)"
        v-model="picked"
        class="island-radio__dot"
        :data-testid="testid ? `${testid}-${one.key}` : undefined"
        :disabled="disabled"
        :name="group"
        type="radio"
        :value="one.key"
      >
      <label
        class="island-radio__say"
        :for="idFor(one.key)"
      >
        {{ one.label }}
        <span
          v-if="one.hint"
          class="island-radio__hint"
        >{{ one.hint }}</span>
      </label>
    </div>
  </div>
</template>

<style scoped>
.island-radio {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.island-radio__row {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: start;
  gap: 0.15rem 0.7rem;
}

/* Round, because one of these is a choice among a few rather than a thing switched on. */
.island-radio__dot {
  width: 1.05rem;
  height: 1.05rem;
  margin: 0.15rem 0 0;
  appearance: none;
  border-radius: 9999px;
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  box-shadow: inset 0 0 0 1px var(--color-hairline);
  cursor: pointer;
}

.island-radio__dot:checked {
  background-color: var(--color-brand);
  box-shadow: inset 0 0 0 3px var(--color-ground);
}

.island-radio__dot:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}

.island-radio__dot:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.island-radio__say {
  font-family: var(--font-body);
  font-size: 0.85rem;
  line-height: 1.45;
  color: var(--color-chalk);
  cursor: pointer;
}

.island-radio__hint {
  display: block;
  font-size: 0.72rem;
  color: var(--color-ash);
}
</style>
