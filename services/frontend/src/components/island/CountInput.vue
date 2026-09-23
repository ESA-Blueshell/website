<script lang="ts" setup>
/* A whole number with steppers, where empty is a state of its own that says what it means
   ("No limit"). The value kept is the number as text, or empty. */
import {computed} from "vue"

defineOptions({name: "CountInput", inheritAttrs: false})

const {
  invalid = false,
  disabled = false,
  controlId = undefined,
  describedBy = undefined,
  empty = "No limit",
  min = 1,
  testid = undefined,
} = defineProps<{
  invalid?: boolean
  disabled?: boolean
  controlId?: string
  describedBy?: string
  /** What an empty field means, shown in it. */
  empty?: string
  /** The smallest count there is; one fewer than it is empty again. */
  min?: number
  testid?: string
}>()

const value = defineModel<string>({default: ""})

const held = computed<number | null>(() => (value.value === "" ? null : Number(value.value)))

const onType = (event: Event) => {
  const raw = (event.target as HTMLInputElement).value.trim()
  if (raw === "") {
    value.value = ""
    return
  }
  // Only digits count: a letter typed is left in the box for the rule to refuse, not dropped.
  if (/^\d+$/.test(raw)) value.value = String(Number(raw))
}

const more = () => {
  value.value = String(held.value === null ? min : held.value + 1)
}

/* Pressable only while there is a count; one fewer than the smallest is empty again. */
const fewer = () => {
  const next = Number(value.value) - 1
  value.value = next < min ? "" : String(next)
}
</script>

<template>
  <span
    class="island-count"
    :class="{'island-count--wrong': invalid}"
  >
    <input
      :id="controlId"
      :aria-describedby="describedBy"
      :aria-invalid="invalid || undefined"
      class="island-count__typed"
      :data-testid="testid"
      :disabled="disabled"
      inputmode="numeric"
      :placeholder="empty"
      type="text"
      :value="value"
      v-bind="$attrs"
      @input="onType"
    >
    <button
      aria-label="One fewer"
      class="island-count__step"
      :data-testid="testid ? `${testid}-fewer` : undefined"
      :disabled="disabled || held === null"
      tabindex="-1"
      type="button"
      @click="fewer"
    >
      &minus;
    </button>
    <button
      aria-label="One more"
      class="island-count__step"
      :data-testid="testid ? `${testid}-more` : undefined"
      :disabled="disabled"
      tabindex="-1"
      type="button"
      @click="more"
    >
      +
    </button>
  </span>
</template>

<style scoped>
.island-count {
  position: relative;
  display: flex;
  align-items: stretch;
  width: 100%;
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border-bottom: 1px solid var(--color-ok);
}

.island-count:focus-within {
  background-color: color-mix(in oklab, var(--color-chalk) 10%, transparent);
  border-bottom-color: var(--color-brand);
}

.island-count--wrong {
  border-bottom-color: var(--color-wrong);
}

.island-count__typed {
  flex: 1 1 auto;
  min-width: 0;
  padding: 0.6rem 1rem;
  font-family: var(--font-body);
  font-size: 0.9rem;
  color: var(--color-chalk);
  background: none;
  border: 0;
  outline: none;
}

.island-count__typed::placeholder {
  color: var(--color-ash);
}

.island-count__step {
  display: grid;
  flex: none;
  place-items: center;
  width: 2.75rem;
  font-size: 1.15rem;
  line-height: 1;
  color: var(--color-ash);
  cursor: pointer;
  background-color: color-mix(in oklab, var(--color-chalk) 6%, transparent);
  border: 0;
}

.island-count__step + .island-count__step {
  margin-left: 1px;
}

.island-count__step:hover:not(:disabled) {
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 11%, transparent);
}

.island-count__step:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
