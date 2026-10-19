<script lang="ts" setup>
import {computed, nextTick, ref} from "vue"
import {fits, plain, type Slot} from "../bankAccount"

/**
 * A code with a fixed shape, such as an IBAN or a BIC, typed or pasted in any form. Each position
 * takes only what belongs there, the code is grouped as banks print it, and what is still to come
 * stays drawn in grey in its place.
 *
 * What is typed and what is still to come are drawn by one layer, so they cannot drift apart; the
 * input above it keeps the caret and the selection with its own text made clear.
 */
defineOptions({name: "ShapedInput", inheritAttrs: false})

const {
  shapeOf,
  sampleOf,
  groupOf,
  invalid = false,
  controlId = undefined,
  describedBy = undefined,
  testid = undefined,
} = defineProps<{
  /** Every position of the code, given what is typed so far, since a country's letters decide it. */
  shapeOf: (held: string) => Slot[]
  /** What each position shows while it is empty. */
  sampleOf: (held: string, shape: Slot[]) => string
  /** The code with its spaces, as banks print it. */
  groupOf: (held: string) => string
  invalid?: boolean
  controlId?: string
  describedBy?: string
  testid?: string
}>()

/** The code without spaces, as it is stored. */
const value = defineModel<string>({default: ""})
const emit = defineEmits<{blur: []}>()

const input = ref<HTMLInputElement | null>(null)

/** Keeps each character that fits the position it lands on, and drops the rest. */
const accepted = (typed: string): string => {
  let held = ""
  for (const character of plain(typed)) {
    const slot = shapeOf(held + character)[held.length]
    if (slot && fits(character, slot)) held += character
  }
  return held
}

const templateOf = (held: string) => groupOf(sampleOf(held, shapeOf(held)))

/* A finished group is followed by its gap, so the caret waits at the start of the next group. */
const displayed = (held: string) => {
  const grouped = groupOf(held)
  const incomplete = held.length > 0 && held.length < shapeOf(held).length
  return incomplete && templateOf(held)[grouped.length] === " " ? `${grouped} ` : grouped
}

const shown = computed(() => displayed(value.value))
const rest = computed(() => templateOf(value.value).slice(shown.value.length))

/* Where the caret goes in the grouped text: after the same number of characters it was after. */
const caretAfter = (grouped: string, count: number) => {
  let seen = 0
  let index = 0
  while (seen < count) {
    if (grouped[index] !== " ") seen++
    index++
  }
  return index
}

const typed = async (event: Event) => {
  const field = event.target as HTMLInputElement
  const atEnd = (field.selectionStart ?? field.value.length) >= field.value.length
  let before = accepted(field.value.slice(0, field.selectionStart ?? field.value.length)).length
  // The positions themselves stop the code at its length, so a pasted one is never cut short.
  let held = accepted(field.value)
  // Backspace on the gap after a group takes the character before it, or it would never go.
  if ((event as InputEvent).inputType?.startsWith("delete") && held === value.value && atEnd) {
    held = held.slice(0, -1)
    before = held.length
  }
  value.value = held
  field.value = displayed(held)
  await nextTick()
  const count = Math.min(before, held.length)
  const caret = count === held.length ? field.value.length : caretAfter(field.value, count)
  input.value?.setSelectionRange(caret, caret)
}
</script>

<template>
  <span
    class="shaped"
    :class="{'shaped--wrong': invalid}"
  >
    <span
      aria-hidden="true"
      class="shaped__lead"
    ><slot name="lead" /></span>
    <span class="shaped__field">
      <span
        aria-hidden="true"
        class="shaped__text shaped__drawn"
        :data-testid="testid ? `${testid}-template` : undefined"
      ><span class="shaped__typed">{{ shown }}</span><span class="shaped__rest">{{ rest }}</span></span>
      <input
        :id="controlId"
        ref="input"
        :aria-describedby="describedBy"
        :aria-invalid="invalid || undefined"
        autocapitalize="characters"
        autocomplete="off"
        class="shaped__text shaped__input"
        :data-testid="testid"
        inputmode="text"
        spellcheck="false"
        :value="shown"
        v-bind="$attrs"
        @blur="emit('blur')"
        @input="typed"
      >
    </span>
  </span>
</template>

<style scoped>
/* One box, as the phone field is: a deeper cell at the left, the code beside it. */
.shaped {
  display: flex;
  align-items: stretch;
  width: 100%;
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border-bottom: 1px solid var(--color-ok);
}

.shaped:focus-within {
  border-bottom-color: var(--color-brand);
  background-color: color-mix(in oklab, var(--color-chalk) 10%, transparent);
}

.shaped--wrong {
  border-bottom-color: var(--color-wrong);
}

.shaped__lead {
  display: grid;
  flex: none;
  place-items: center;
  width: var(--field-lead, 4.35rem);
  font-size: 0.6rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  color: var(--color-ash);
  background-color: color-mix(in oklab, var(--color-chalk) 6%, transparent);
}

.shaped__field {
  position: relative;
  flex: 1;
  min-width: 0;
}

/* Both layers set every part of their type themselves, so no site rule for inputs moves one of them. */
.shaped .shaped__text {
  box-sizing: border-box;
  display: block;
  width: 100%;
  margin: 0;
  padding: 1.35rem 1rem 0.45rem;
  border: 0;
  font-family: ui-monospace, "SFMono-Regular", Menlo, Consolas, monospace;
  font-size: 0.95rem;
  font-weight: 400;
  font-style: normal;
  line-height: 1.4;
  letter-spacing: 0.04em;
  text-transform: none;
  white-space: pre;
}

.shaped__drawn {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.shaped__typed {
  color: var(--color-chalk);
}

.shaped__rest {
  color: color-mix(in oklab, var(--color-chalk) 28%, transparent);
}

.shaped .shaped__input {
  position: relative;
  background: none;
  color: transparent;
  caret-color: var(--color-chalk);
  outline: none;
}

.shaped .shaped__input::selection {
  color: transparent;
  background-color: color-mix(in oklab, var(--color-brand) 45%, transparent);
}
</style>
