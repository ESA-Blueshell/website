<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, ref, watch} from "vue"
import {deburrLower} from "@/composables/countries"

/**
 * Several of a list, picked into the field itself: each choice is a chip left of the caret and
 * the list completes what is typed. A name typed out and closed with a comma becomes a chip when
 * it is on the list, and a pasted run of names ("#one #two", "@one, @two" or "one, two") becomes
 * a chip each. A leading # or @ is the list's own mark, so it is searched past.
 */
defineOptions({name: "ChipPicker"})

type Option = {key: string, label: string, note?: string}

const props = withDefaults(defineProps<{
  /** Every row there is; the chosen ones are left out of the list. */
  options: Option[]
  chosen: Option[]
  /** Drawn before every label: # for a channel, @ for a role, nothing for a name. */
  sigil?: string
  testidPrefix: string
  /** The test id a chip carries, which the pickers this replaces already gave their chips. */
  chipTestid?: (key: string) => string
  removeLabel?: (label: string) => string
  placeholder?: string
  emptyNote?: string
  disabled?: boolean
  controlId?: string
  labelledBy?: string
}>(), {
  sigil: "",
  chipTestid: undefined,
  removeLabel: (label: string) => `Take ${label} away`,
  placeholder: "Search",
  emptyNote: "There is nothing left to choose.",
  disabled: false,
  controlId: undefined,
  labelledBy: undefined,
})

const emit = defineEmits<{
  (event: "add", keys: string[]): void
  (event: "remove", key: string): void
}>()

const text = ref("")
const open = ref(false)
const active = ref(0)
const refused = ref<string[]>([])

const anchor = ref<HTMLElement | null>(null)
const field = ref<HTMLElement | null>(null)
const input = ref<HTMLInputElement | null>(null)
const list = ref<HTMLElement | null>(null)
const box = ref({top: 0, left: 0, width: 0, tall: 240, above: false})
const pinnedDark = ref(false)

/** What a name is looked up by: no mark in front, no case, no accents. */
const bare = (said: string) => deburrLower(said.trim().replace(/^[#@]+/u, ""))

const chosenKeys = computed(() => new Set(props.chosen.map(one => one.key)))
/* Names too: a choice kept from before may carry an id the list has since given another row. */
const chosenNames = computed(() => new Set(props.chosen.map(one => bare(one.label))))
const offered = computed(() => props.options.filter(one => !chosenKeys.value.has(one.key) && !chosenNames.value.has(bare(one.label))))

const matches = computed(() => {
  const asked = bare(text.value)
  if (asked === "") return offered.value
  return offered.value.filter(one => [one.label, one.note ?? ""].some(said => bare(said).includes(asked)))
})

watch(matches, () => { active.value = 0 })

const exact = (token: string) => {
  const asked = bare(token)
  if (asked === "") return undefined
  return offered.value.find(one => bare(one.label) === asked || one.key === token.trim())
}

/** Every name in [said], split on commas, spaces and new lines; the ones on the list become chips. */
const commit = (said: string) => {
  const tokens = said.split(/[\s,]+/u).map(one => one.trim()).filter(Boolean)
  const found: string[] = []
  const missing: string[] = []
  for (const token of tokens) {
    const one = exact(token)
    if (one) {
      if (!found.includes(one.key)) found.push(one.key)
    } else if (!props.chosen.some(held => bare(held.label) === bare(token))) {
      // A name already chosen is simply there; only a name the list does not know is said.
      missing.push(token)
    }
  }
  if (found.length > 0) emit("add", found)
  refused.value = missing
  text.value = missing.join(", ")
  return found.length > 0
}

const place = () => {
  const at = (field.value as HTMLElement).getBoundingClientRect()
  const room = window.innerHeight - at.bottom
  const above = room < 260 && at.top > room
  box.value = {
    top: above ? at.top : at.bottom,
    left: at.left,
    width: at.width,
    tall: Math.max(140, Math.min(240, (above ? at.top : room) - 8)),
    above,
  }
}

const elsewhere = (event: Event) => {
  if (anchor.value?.contains(event.target as Element | null)) return
  open.value = false
}

const watching = (on: boolean) => {
  const how = on ? "addEventListener" : "removeEventListener"
  window[how]("scroll", place, true)
  window[how]("resize", place)
  document[how]("pointerdown", elsewhere)
}

watch(open, async (down) => {
  if (!down) {
    watching(false)
    return
  }
  pinnedDark.value = anchor.value?.closest(".island-dark") != null
  place()
  await nextTick()
  place()
  watching(true)
})

/* A chip added or taken away moves the field's edge, and the list hangs off it. */
watch(() => props.chosen.length, async () => {
  await nextTick()
  if (open.value) place()
})

onBeforeUnmount(() => watching(false))

const take = (key: string) => {
  emit("add", [key])
  text.value = ""
  refused.value = []
  void nextTick(() => input.value?.focus())
}

const onType = (event: Event) => {
  const value = (event.target as HTMLInputElement).value
  // A comma typed on a phone keyboard may not raise a key event of its own.
  if (/[,\n]/u.test(value)) {
    commit(value)
    return
  }
  text.value = value
  refused.value = []
  open.value = true
}

const onPaste = (event: ClipboardEvent) => {
  const pasted = event.clipboardData?.getData("text") ?? ""
  if (!/[\s,#@]/u.test(pasted.trim())) return
  event.preventDefault()
  commit(`${text.value} ${pasted}`)
  open.value = text.value !== ""
}

const follow = async () => {
  await nextTick()
  list.value?.querySelector<HTMLElement>(".chips__row--active")?.scrollIntoView({block: "nearest"})
}

const onKey = (event: KeyboardEvent) => {
  if (event.key === ",") {
    event.preventDefault()
    commit(text.value)
    return
  }
  if (event.key === "ArrowDown" || event.key === "ArrowUp") {
    event.preventDefault()
    open.value = true
    const last = matches.value.length - 1
    if (last < 0) return
    active.value = Math.min(Math.max(active.value + (event.key === "ArrowDown" ? 1 : -1), 0), last)
    void follow()
    return
  }
  if (event.key === "Enter") {
    event.preventDefault()
    if (exact(text.value)) {
      commit(text.value)
      return
    }
    const one = open.value ? matches.value[active.value] : undefined
    if (one) take(one.key)
    return
  }
  if (event.key === "Backspace" && text.value === "" && props.chosen.length > 0) {
    const last = props.chosen[props.chosen.length - 1] as Option
    emit("remove", last.key)
    return
  }
  if (event.key === "Escape") open.value = false
}

const leave = (event: FocusEvent) => {
  const next = event.relatedTarget as Element | null
  if (!next) return
  if ((event.currentTarget as HTMLElement).contains(next)) return
  if (next.closest?.("[data-picker-list]")) return
  open.value = false
}

const chipId = (key: string) => props.chipTestid?.(key) ?? `${props.testidPrefix}-chip-${key}`
</script>

<template>
  <div
    ref="anchor"
    class="chips"
    @focusout="leave"
  >
    <!-- One control: the chips, the typing and the caret share one box and one rule, and a press
         anywhere on it puts the caret in the typing. -->
    <div
      ref="field"
      class="chips__field"
      :class="{'chips__field--chosen': chosen.length > 0, 'chips__field--off': disabled}"
      @click="disabled || input?.focus()"
    >
      <span
        v-for="one in chosen"
        :key="one.key"
        class="chips__chip"
        :data-testid="chipId(one.key)"
      >
        <span class="chips__chip-label">{{ sigil }}{{ one.label }}</span>
        <button
          v-if="!disabled"
          :aria-label="removeLabel(`${sigil}${one.label}`)"
          class="chips__chip-remove"
          type="button"
          @click.stop="emit('remove', one.key)"
        >
          <svg
            aria-hidden="true"
            fill="none"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-width="1.8"
            viewBox="0 0 24 24"
          ><path d="M7 7l10 10M17 7 7 17" /></svg>
        </button>
      </span>
      <input
        :id="controlId"
        ref="input"
        :aria-controls="`${testidPrefix}-list`"
        :aria-expanded="open"
        aria-haspopup="listbox"
        :aria-label="labelledBy ? undefined : placeholder"
        :aria-labelledby="labelledBy"
        autocomplete="off"
        class="chips__search"
        :data-testid="`${testidPrefix}-search`"
        :disabled="disabled"
        :placeholder="chosen.length > 0 ? '' : placeholder"
        role="combobox"
        type="text"
        :value="text"
        @focus="open = true"
        @input="onType"
        @keydown="onKey"
        @paste="onPaste"
      >
      <span
        aria-hidden="true"
        class="chips__caret"
      >
        <svg
          fill="none"
          stroke="currentColor"
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="1.6"
          viewBox="0 0 24 24"
        ><path d="m6 9.5 6 6 6-6" /></svg>
      </span>
    </div>

    <p
      v-if="refused.length > 0"
      class="chips__note"
      :data-testid="`${testidPrefix}-refused`"
      role="status"
    >
      Not on the list: {{ refused.join(", ") }}
    </p>
    <p
      v-else-if="offered.length === 0 && options.length > 0 && emptyNote"
      class="chips__note"
      :data-testid="`${testidPrefix}-none`"
    >
      {{ emptyNote }}
    </p>

    <Teleport to="body">
      <ul
        v-if="open && !disabled && matches.length > 0"
        :id="`${testidPrefix}-list`"
        ref="list"
        class="chips__list"
        :class="{'chips__list--above': box.above, 'island-dark': pinnedDark}"
        data-picker-list
        :data-testid="`${testidPrefix}-list`"
        role="listbox"
        :style="{top: `${box.top}px`, left: `${box.left}px`, width: `${box.width}px`, maxHeight: `${box.tall}px`}"
        @mousedown.stop.prevent
        @pointerdown.stop
      >
        <li
          v-for="(one, index) in matches"
          :key="one.key"
        >
          <button
            :aria-selected="index === active"
            class="chips__row"
            :class="{'chips__row--active': index === active}"
            :data-testid="`${testidPrefix}-${one.key}`"
            role="option"
            type="button"
            @click="take(one.key)"
            @mouseenter="active = index"
          >
            <span class="chips__label">{{ sigil }}{{ one.label }}</span>
            <span
              v-if="one.note"
              class="chips__row-note"
            >{{ one.note }}</span>
          </button>
        </li>
      </ul>
      <p
        v-else-if="open && !disabled && offered.length > 0 && text.trim() !== '' && refused.length === 0"
        class="chips__list chips__list--nothing"
        :class="{'island-dark': pinnedDark}"
        data-picker-list
        :data-testid="`${testidPrefix}-no-matches`"
        :style="{top: `${box.top}px`, left: `${box.left}px`, width: `${box.width}px`}"
        @mousedown.stop.prevent
        @pointerdown.stop
      >
        Nothing answers to that.
      </p>
    </Teleport>
  </div>
</template>

<style scoped>
.chips {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

/* The picker's box: its ground and its rule, with the chips wrapping inside it. */
.chips__field {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
  width: 100%;
  min-height: 2.6rem;
  padding: 0.35rem 2.3rem 0.35rem 0.5rem;
  position: relative;
  cursor: text;
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border-bottom: 1px solid var(--color-ash);
}

.chips__field--chosen,
.chips__field:focus-within {
  border-bottom-color: var(--color-brand);
  background-color: color-mix(in oklab, var(--color-chalk) 10%, transparent);
}

.chips__field--off {
  cursor: default;
  opacity: 0.7;
}

.chips__chip {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 0.2rem;
  max-width: 100%;
  padding: 0.18rem 0.3rem 0.18rem 0.55rem;
  font-family: var(--font-body);
  font-size: 0.85rem;
  line-height: 1.3;
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-brand) 24%, transparent);
  border: 1px solid color-mix(in oklab, var(--color-brand) 45%, transparent);
  border-radius: 999px;
}

.chips__chip-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chips__chip-remove {
  display: grid;
  flex: none;
  place-items: center;
  width: 1.2rem;
  height: 1.2rem;
  padding: 0;
  color: var(--color-chalk);
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: 50%;
  opacity: 0.75;
}

.chips__chip-remove:hover,
.chips__chip-remove:focus-visible {
  opacity: 1;
  background-color: color-mix(in oklab, var(--color-chalk) 16%, transparent);
  outline: none;
}

.chips__chip-remove svg {
  width: 0.8rem;
  height: 0.8rem;
}

.chips__search {
  flex: 1 1 6rem;
  min-width: 6rem;
  padding: 0.25rem 0.5rem;
  font-family: var(--font-body);
  font-size: 0.9rem;
  color: var(--color-chalk);
  background: none;
  border: 0;
}

.chips__search:focus-visible {
  outline: none;
}

.chips__search::placeholder {
  color: var(--color-ash);
}

.chips__caret {
  position: absolute;
  top: 50%;
  right: 0.7rem;
  display: grid;
  place-items: center;
  translate: 0 -50%;
  pointer-events: none;
}

.chips__caret svg {
  width: 14px;
  height: 14px;
  color: var(--color-ash);
}

.chips__note {
  margin: 0;
  font-family: var(--font-body);
  font-size: 0.78rem;
  color: var(--color-ash);
}

.chips__list {
  position: fixed;
  z-index: 2500;
  pointer-events: auto;
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
  background-color: var(--color-pit);
  border: 1px solid color-mix(in oklab, var(--color-chalk) 14%, transparent);
  border-top: 0;
  box-shadow: 0 1rem 2rem rgb(0 0 0 / 45%);
  overscroll-behavior: contain;
}

.chips__list--above {
  transform: translateY(-100%);
  border-top: 1px solid color-mix(in oklab, var(--color-chalk) 14%, transparent);
  border-bottom: 0;
}

.chips__list--nothing {
  padding: 0.6rem 0.75rem;
  font-family: var(--font-body);
  font-size: 0.85rem;
  color: var(--color-ash);
}

.chips__row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.55rem;
  width: 100%;
  padding: 0.55rem 0.9rem;
  overflow: hidden;
  font-family: var(--font-body);
  color: var(--color-chalk);
  text-align: left;
  cursor: pointer;
  background-color: transparent;
  border: 0;
  border-bottom: 1px solid color-mix(in oklab, var(--color-chalk) 8%, transparent);
}

.chips__row::before {
  position: absolute;
  inset: 0;
  content: "";
  background-color: color-mix(in oklab, var(--color-brand) 26%, transparent);
  scale: 0 1;
  transform-origin: left center;
  transition: scale 260ms cubic-bezier(0.22, 1, 0.36, 1);
}

.chips__row--active::before {
  scale: 1 1;
}

.chips__label,
.chips__row-note {
  position: relative;
}

.chips__label {
  flex-grow: 1;
  min-width: 0;
  font-size: 0.92rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chips__row-note {
  flex: none;
  font-family: var(--font-bitmap);
  font-size: 0.72rem;
  color: var(--color-ash);
}

@media (prefers-reduced-motion: reduce) {
  .chips__row::before {
    transition: none;
  }
}
</style>
