<script lang="ts" setup>
import {computed, onBeforeUnmount, nextTick, ref, watch} from "vue"
import CountryFlag from "@/components/island/CountryFlag.vue"
import {deburrLower} from "@/composables/countries"

/** One of a list: a panel holding every row, narrowed by what is typed rather than cut short. */
defineOptions({name: "SearchPicker"})

const props = withDefaults(defineProps<{
  /** `terms` are the extra words a row is found by; the label and the note are searched anyway. */
  options: Array<{
    key: string
    label: string
    note?: string
    flag?: string
    /** A picture drawn before the label, such as somebody's avatar. */
    avatar?: string
    terms?: string[]
    /** Drawn, and said to be out of reach: a row a rule refuses rather than one it hides. */
    disabled?: boolean
  }>
  /** The caller searches, so this filters nothing: a fetched page must not be filtered twice. */
  remote?: boolean
  loading?: boolean
  testidPrefix: string
  placeholder?: string
  /** Said where there is nothing to choose from, which is not the same as nothing matching; empty says nothing. */
  emptyNote?: string
  /** The chosen row stays in the list, so choosing again is the same control. */
  selectedKey?: string | null
  disabled?: boolean
  /** Picking empties the box and leaves the list open, so another row can follow; a comma takes
   *  the row Enter would. */
  stayOpen?: boolean
  /** Shut, the field is a button: for a choice inside another control, such as the dial code. */
  compact?: boolean
  /** Typed for the reader each time the list opens with nothing chosen, and searched. Shut, it
   *  stands in the box, marked as no choice yet. */
  firstSearch?: string
  /** The id the field's label points at, so the label names this control. */
  controlId?: string
  labelledBy?: string
}>(), {
  placeholder: "Search",
  emptyNote: "There is nothing to choose from.",
  selectedKey: null,
  disabled: false,
  compact: false,
  stayOpen: false,
  remote: false,
  loading: false,
  firstSearch: undefined,
  controlId: undefined,
  labelledBy: undefined,
})

const emit = defineEmits<{
  (event: "pick", key: string): void
  (event: "search", term: string): void
  (event: "opened"): void
  /** The box was emptied and left: the reader took the choice away. */
  (event: "clear"): void
}>()

const search = ref("")

/* Opened on an answer, the box keeps it and the list keeps every row until something is typed. */
const typedOnce = ref(false)

/* The trigger shows this row, so one can be tried without picking it. */
const hovered = ref<string | null>(null)

/* What Enter or Tab would take, held as a position: typing changes what matches under it. */
const active = ref(0)

/* Two marked rows say nothing about what Enter would take, so the pointer gives way. */
const byKeys = ref(false)

const open = ref(false)

/* The box the list hangs off; compact, the thing pressed is a button and the typing is in the list. */
const anchor = ref<HTMLElement | null>(null)
const field = ref<HTMLElement | null>(null)
const list = ref<HTMLElement | null>(null)

/* Drawn at the end of the document, since an ancestor that scrolls or is cut would clip it,
   and placed against the field's box instead. */
const box = ref({top: 0, left: 0, width: 0, tall: 240, above: false})

/* The list hangs at the end of the document, so a pinned-dark area's name is carried to it. */
const pinnedDark = ref(false)

const place = () => {
  const at = (anchor.value as HTMLElement).getBoundingClientRect()
  const room = window.innerHeight - at.bottom
  // Opens upward where there is no room below it, which on a short window there often is not.
  const above = room < 260 && at.top > room
  box.value = {
    top: above ? at.top : at.bottom,
    left: at.left,
    width: at.width,
    // Only as tall as the side it opened on: a list taller than its room runs back over the
    // field it came from, and the search at its head lands on top of what was typed.
    tall: Math.max(140, Math.min(240, (above ? at.top : room) - 8)),
    above,
  }
}

/* The list stops its own presses, so a press reaching here came from outside the control. */
const elsewhere = (event: Event) => {
  const target = event.target as Element | null
  if (anchor.value?.contains(target)) return
  open.value = false
}

// Called through `document`: a method taken off it and called bare is refused. Bubbling, not
// capturing, or the list's own press would close it under the click.
const watching = (on: boolean) => {
  if (on) {
    window.addEventListener("scroll", place, true)
    window.addEventListener("resize", place)
    document.addEventListener("pointerdown", elsewhere)
    return
  }
  window.removeEventListener("scroll", place, true)
  window.removeEventListener("resize", place)
  document.removeEventListener("pointerdown", elsewhere)
}

watch(open, async (down) => {
  if (!down) {
    if (typedOnce.value && search.value.trim() === "") emit("clear")
    hovered.value = null
    typedOnce.value = false
    search.value = ""
    watching(false)
    return
  }
  typedOnce.value = false
  if (props.firstSearch && !props.selectedKey) {
    search.value = props.firstSearch
    typedOnce.value = true
    emit("search", props.firstSearch)
  }
  emit("opened")
  active.value = Math.max(0, props.options.findIndex(one => one.key === props.selectedKey))
  pinnedDark.value = anchor.value?.closest(".island-dark") != null
  place()
  await nextTick()
  place()
  measureRows()
  // Whatever is in the box is selected, so typing replaces the answer rather than appending.
  if (props.compact) field.value?.focus()
  const box = field.value as HTMLInputElement | null
  box?.select?.()
  if (list.value && active.value > 0) {
    list.value.scrollTop = Math.max(0, active.value * step.value - list.value.clientHeight / 2)
    await nextTick()
    measureRows()
    list.value.scrollTop = Math.max(0, active.value * step.value - list.value.clientHeight / 2)
  }
  watching(true)
})

onBeforeUnmount(() => watching(false))

const chosen = computed(() =>
  props.options.find(one => one.key === props.selectedKey)?.label ?? "")

/* Shut with nothing chosen, the first search stands in the box: a name, not yet anybody. */
const unmatched = computed<boolean>(() =>
  !open.value && !props.selectedKey && Boolean(props.firstSearch))

const shownOption = computed(() =>
  props.options.find(one => one.key === (hovered.value ?? aimedKey.value ?? props.selectedKey)))

const aimedKey = computed<string | null>(() =>
  (open.value && typedOnce.value ? matches.value[active.value]?.key : null) ?? null)

/* Shut, the box shows what was chosen; open, what is being typed. Never both at once. */
const onType = (event: Event) => {
  search.value = (event.target as HTMLInputElement).value
  typedOnce.value = true
  active.value = 0
  open.value = true
  emit("search", search.value.trim())
}

const follow = async () => {
  await nextTick()
  const el = list.value?.querySelector<HTMLElement>(".picker__row--active")
  el?.scrollIntoView({block: "nearest"})
}

const aim = (by: number) => {
  byKeys.value = true
  hovered.value = null
  const last = matches.value.length - 1
  if (last < 0) return
  active.value = Math.min(Math.max(active.value + by, 0), last)
  const box = list.value
  if (box) {
    // Windowed, the row may not be built yet, so the list is moved to where it will be.
    box.scrollTop = Math.max(0, active.value * step.value - box.clientHeight / 2)
  }
  void follow()
}

const takeAimed = (stay = props.stayOpen) => {
  const one = matches.value[active.value]
  if (one && !one.disabled) pick(one.key, stay)
}

const onKey = (event: KeyboardEvent) => {
  if (event.key === "ArrowDown") {
    event.preventDefault()
    open.value = true
    aim(1)
    return
  }
  if (event.key === "ArrowUp") {
    event.preventDefault()
    aim(-1)
    return
  }
  if (event.key === "Enter") {
    event.preventDefault()
    takeAimed()
    return
  }
  if (event.key === "," && props.stayOpen && typedOnce.value && matches.value[active.value]) {
    event.preventDefault()
    takeAimed()
    return
  }
  // Tab moves on, so the list closes behind it even where it would stay open.
  if (event.key === "Tab" && open.value && typedOnce.value) takeAimed(false)
  if (event.key === "Escape") open.value = false
}

const pick = (key: string, stay = props.stayOpen) => {
  if (!stay) open.value = false
  hovered.value = null
  typedOnce.value = false
  search.value = ""
  emit("pick", key)
  // A pressed row takes the focus, and may leave the list with it; the next search is typed here.
  if (stay) field.value?.focus()
}

const leave = (event: FocusEvent) => {
  const next = event.relatedTarget as Element | null
  // Focus going nowhere is not focus leaving: a compact picker loses its button mid-click.
  if (!next) return
  if (next && (event.currentTarget as HTMLElement).contains(next)) return
  if (next?.closest?.("[data-picker-list]")) return
  open.value = false
}

const matches = computed(() => {
  const asked = typedOnce.value ? deburrLower(search.value.trim()) : ""
  if (props.remote || asked === "") return props.options
  // Accents are dropped on both sides, so "aland" and "Åland" find the same row.
  return props.options.filter(one =>
    [one.label, one.note ?? "", ...(one.terms ?? [])]
      .some(said => deburrLower(said).includes(asked)))
})

/* Under this every match is in the document, so Tab reaches each row. */
const WINDOWED_FROM = 120
/* Rows drawn either side of the window, so a scroll shows no gap before it fills. */
const OVERSCAN = 6

const scrolled = ref(0)
const viewport = ref(0)
/* One row plus the gap under it, measured off the rows that are drawn. */
const step = ref(48)

const measureRows = () => {
  const el = list.value
  if (!el) return
  viewport.value = el.clientHeight
  // Two rows, so the gap between them is measured too; a height of zero means no layout yet.
  const rows = el.querySelectorAll<HTMLElement>("li:not([data-picker-pad])")
  // Measured only while the list holds rows, so there is always a first one.
  const first = rows[0] as HTMLElement
  const second = rows[1]
  if (second) {
    step.value = Math.round(second.offsetTop - first.offsetTop) || step.value
    return
  }
  const height = first.getBoundingClientRect().height
  if (height > 0) step.value = Math.round(height)
}

const onScroll = (event: Event) => {
  scrolled.value = (event.target as HTMLElement).scrollTop
}

const windowed = computed(() => {
  const all = matches.value
  if (all.length <= WINDOWED_FROM) {
    return {rows: all, above: 0, below: 0}
  }
  const fits = Math.ceil((viewport.value || 240) / step.value)
  const first = Math.max(0, Math.floor(scrolled.value / step.value) - OVERSCAN)
  const last = Math.min(all.length, first + fits + OVERSCAN * 2)
  return {
    rows: all.slice(first, last),
    above: first * step.value,
    below: (all.length - last) * step.value,
  }
})

/* A new search is a new list, so it is read from the top. */
watch(matches, () => {
  active.value = 0
  scrolled.value = 0
  if (list.value) list.value.scrollTop = 0
})
</script>

<template>
  <div
    ref="anchor"
    class="picker"
    @focusout="leave"
  >
    <button
      v-if="compact"
      :aria-label="placeholder"
      class="picker__shut"
      :data-testid="`${testidPrefix}-shut`"
      :disabled="disabled"
      type="button"
      @click="open = !open"
    >
      <slot
        name="chosen"
        :option="shownOption"
      />
      <span
        aria-hidden="true"
        class="picker__caret"
      >
        <svg
          fill="none"
          stroke="currentColor"
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="1.6"
          viewBox="0 0 24 24"
        >
          <path d="m6 9.5 6 6 6-6" />
        </svg>
      </span>
    </button>

    <div
      v-if="!compact"
      class="picker__field"
      :class="{
        'picker__field--chosen': selectedKey !== null && selectedKey !== undefined,
        'picker__field--unmatched': unmatched,
      }"
    >
      <!-- Not a control of its own, so a press anywhere in the box opens the one list. -->
      <span
        v-if="$slots.lead"
        class="picker__lead"
      >
        <slot
          name="lead"
          :option="shownOption"
        />
      </span>
      <img
        v-else-if="shownOption?.avatar"
        alt=""
        class="picker__avatar picker__avatar--field"
        :data-testid="`${testidPrefix}-avatar`"
        :src="shownOption.avatar"
      >

      <input
        :id="controlId"
        ref="field"
        :aria-controls="`${testidPrefix}-list`"
        :aria-expanded="open"
        aria-haspopup="listbox"
        :aria-label="labelledBy ? undefined : placeholder"
        :aria-labelledby="labelledBy"
        class="picker__search"
        role="combobox"
        :data-testid="`${testidPrefix}-search`"
        :disabled="disabled"
        :placeholder="placeholder"
        type="text"
        :value="open && typedOnce ? search : unmatched ? firstSearch : chosen"
        @click="open = true"
        @input="onType"
        @keydown="onKey"
      >

      <span
        aria-hidden="true"
        class="picker__caret picker__caret--field"
        @click="open = !open"
      >
        <svg
          fill="none"
          stroke="currentColor"
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="1.6"
          viewBox="0 0 24 24"
        >
          <path d="m6 9.5 6 6 6-6" />
        </svg>
      </span>
    </div>

    <p
      v-if="options.length === 0 && emptyNote"
      class="picker__note"
      :data-testid="`${testidPrefix}-none`"
    >
      {{ emptyNote }}
    </p>

    <!-- At the end of the document: an ancestor that scrolls or is cut would clip the list. -->
    <Teleport to="body">
      <ul
        v-if="open && (matches.length > 0 || $slots.missing)"
        :id="`${testidPrefix}-list`"
        ref="list"
        class="picker__list"
        :class="{'picker__list--above': box.above, 'island-dark': pinnedDark}"
        role="listbox"
        data-picker-list
        :data-testid="`${testidPrefix}-list`"
        :style="{
          top: `${box.top}px`,
          left: `${box.left}px`,
          width: `${box.width}px`,
          minWidth: compact ? '19rem' : undefined,
          maxHeight: `${box.tall}px`,
        }"
        @mousedown.stop
        @mousemove="byKeys = false"
        @pointerdown.stop
        @scroll="onScroll"
      >
        <li
          v-if="loading"
          class="picker__waiting"
        >
          Looking
        </li>

        <li
          v-if="compact"
          class="picker__seek"
        >
          <input
            ref="field"
            :aria-label="placeholder"
            class="picker__search picker__search--inlist"
            :data-testid="`${testidPrefix}-search`"
            placeholder="Search"
            type="text"
            :value="typedOnce ? search : chosen"
            @input="onType"
            @keydown="onKey"
          >
        </li>

        <!-- What the list says when a search found nothing, given by the caller only then. -->
        <li
          v-if="$slots.missing && matches.length === 0"
          class="picker__missing"
          :data-testid="`${testidPrefix}-missing`"
        >
          <slot name="missing" />
        </li>

        <li
          v-if="windowed.above > 0"
          data-picker-pad
          :style="{height: `${windowed.above}px`, flex: '0 0 auto'}"
        />

        <li
          v-for="one in windowed.rows"
          :key="one.key"
        >
          <button
            :aria-selected="one.key === selectedKey"
            role="option"
            class="picker__row"
            :class="{
              'picker__row--on': one.key === selectedKey,
              'picker__row--active': byKeys && matches[active]?.key === one.key,
              'picker__row--quiet': byKeys,
            }"
            :data-testid="`${testidPrefix}-${one.key}`"
            :disabled="disabled || one.disabled"
            type="button"
            @click="pick(one.key)"
            @mouseenter="byKeys ? null : (hovered = one.key)"
            @mouseleave="hovered = null"
          >
            <country-flag
              v-if="one.flag"
              :code="one.flag"
            />
            <img
              v-else-if="one.avatar"
              alt=""
              class="picker__avatar"
              loading="lazy"
              :src="one.avatar"
            >
            <span class="picker__label">{{ one.label }}</span>
            <span
              v-if="one.note"
              class="picker__note-inline"
            >{{ one.note }}</span>
          </button>
        </li>

        <li
          v-if="windowed.below > 0"
          data-picker-pad
          :style="{height: `${windowed.below}px`, flex: '0 0 auto'}"
        />
      </ul>

      <p
        v-else-if="open && options.length > 0"
        ref="list"
        class="picker__note picker__note--over"
        :class="{'island-dark': pinnedDark}"
        data-picker-list
        :data-testid="`${testidPrefix}-no-matches`"
        :style="{
          top: `${box.top}px`,
          left: `${box.left}px`,
          width: `${box.width}px`,
        }"
        @mousedown.stop
        @pointerdown.stop
      >
        Nothing answers to that.
      </p>
    </Teleport>
  </div>
</template>

<style scoped>
.picker {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

/* One control, so the lead, the typing and the caret share one box and one rule. */
.picker__field {
  display: flex;
  align-items: stretch;
  width: 100%;
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  /* Nothing chosen rests on a quiet line; a choice rests on the brand blue, where green is for an
     answer somebody typed. */
  border-bottom: 1px solid var(--color-ash);
}

.picker__field--chosen,
.picker__field:focus-within {
  border-bottom-color: var(--color-brand);
  background-color: color-mix(in oklab, var(--color-chalk) 10%, transparent);
}


/* Same width as the phone field's dial cell, so a column of fields lines up down its edge. */
.picker__field--unmatched .picker__search {
  color: var(--color-danger);
}

.picker__lead {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: var(--field-lead, 4.35rem);
  background-color: color-mix(in oklab, var(--color-chalk) 6%, transparent);
}

.picker__caret--field {
  display: grid;
  place-items: center;
  padding: 0 0.7rem;
  background: none;
  cursor: pointer;
}

/* The box carries the ground and the rule, so the input draws neither. */
.picker__search {
  flex: 1 1 auto;
  min-width: 0;
  padding: 0.6rem 1rem;
  font-family: var(--font-body);
  font-size: 0.9rem;
  color: var(--color-chalk);
  background: none;
  border: 0;
}

.picker__search:focus-visible {
  outline: none;
}

.picker__waiting {
  padding: 0.6rem 0.9rem;
  font-family: var(--font-bitmap);
  font-size: 0.7rem;
  color: var(--color-ash);
}

/* Compact, the trigger is a button, so the typing happens at the head of the list. */
.picker__seek {
  position: sticky;
  top: 0;
  z-index: 1;
  background-color: var(--color-pit);
  border-bottom: 1px solid var(--color-hairline);
}

.picker__search--inlist {
  width: 100%;
  background: none;
  border-bottom: 0;
}

/* Shut and compact: what was chosen, and the mark that says there is more. */
.picker__shut {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.6rem 0.5rem 0.6rem 0.6rem;
  border: 0;
  background: none;
  color: var(--color-chalk);
  cursor: pointer;
}

.picker__shut:disabled {
  cursor: default;
  opacity: 0.55;
}

.picker__caret svg {
  width: 14px;
  height: 14px;
  color: var(--color-ash);
}

.picker__search::placeholder {
  color: var(--color-ash);
}

.picker__list {
  position: fixed;
  /* Over the dialog that raised it, which sits at 2401, and taking back the pointer events a
     modal dialog turns off on the body, since this is drawn outside that dialog. */
  z-index: 2500;
  pointer-events: auto;
  display: flex;
  flex-direction: column;
  gap: 0;
  max-height: 15rem;
  padding: 0;
  margin: 0;
  overflow-y: auto;
  list-style: none;
  background-color: var(--color-pit);
  border: 1px solid color-mix(in oklab, var(--color-chalk) 14%, transparent);
  /* Flush from the field: its top edge is the field's bottom rule. */
  border-top: 0;
  box-shadow: 0 1rem 2rem rgb(0 0 0 / 45%);
  overscroll-behavior: contain;
}

/* Anchored by its bottom edge where it opens upward, so it grows away from the field. */
.picker__list--above {
  transform: translateY(-100%);
  border-top: 1px solid color-mix(in oklab, var(--color-chalk) 14%, transparent);
  border-bottom: 0;
}

.picker__note--over {
  position: fixed;
  z-index: 2500;
  margin: 0;
  pointer-events: auto;
  padding: 0.6rem 0.75rem;
  background-color: var(--color-pit);
  border: 1px solid color-mix(in oklab, var(--color-chalk) 14%, transparent);
}

.picker__row {
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

.picker__row::before {
  position: absolute;
  inset: 0;
  content: "";
  background-color: color-mix(in oklab, var(--color-brand) 26%, transparent);
  scale: 0 1;
  transform-origin: left center;
  transition: scale 260ms cubic-bezier(0.22, 1, 0.36, 1);
}

.picker__row:hover:not(:disabled)::before,
.picker__row:focus-visible::before,
.picker__row--active::before {
  scale: 1 1;
}

/* Read with the keys, the pointer marks nothing: what it rests on is not what Enter takes. */
.picker__row--quiet:hover:not(.picker__row--active)::before {
  scale: 0 1;
}

/* Marked down its edge, not filled: filled reads as the hover wash stuck on. */
.picker__row--on {
  box-shadow: inset 2px 0 0 var(--color-brand);
}

.picker__row--on .picker__label {
  color: var(--color-brand-lit);
}

.picker__row:disabled {
  cursor: default;
  opacity: 0.55;
}

.picker__label,
.picker__note-inline {
  position: relative;
}

.picker__missing {
  padding: 0.6rem 0.75rem;
  font-family: var(--font-body);
  font-size: 0.85rem;
  line-height: 1.4;
  color: var(--color-chalk);
}

.picker__missing a {
  color: var(--color-brand-lit);
  font-weight: 600;
}

.picker__avatar {
  flex: 0 0 auto;
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 50%;
  object-fit: cover;
}

.picker__avatar--field {
  align-self: center;
  margin-left: 0.75rem;
}

.picker__avatar--field + .picker__search {
  padding-left: 0.6rem;
}

.picker__label {
  flex-grow: 1;
  min-width: 0;
  font-size: 0.92rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picker__note-inline {
  flex: none;
  font-family: var(--font-bitmap);
  font-size: 0.72rem;
  color: var(--color-ash);
}

/* Out of the flow, so a field with nothing to choose from is the same height as one with
   rows: in a form the label rests on the middle of the box. */
.picker__note {
  position: absolute;
  top: 100%;
  left: 0;
  margin: 0;
  font-family: var(--font-body);
  font-size: 0.78rem;
  color: var(--color-ash);
}


@media (prefers-reduced-motion: reduce) {
  .picker__row::before {
    transition: none;
  }
}
</style>
