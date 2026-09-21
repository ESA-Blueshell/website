<script lang="ts" setup>
import {computed, onBeforeUnmount, nextTick, ref, watch} from "vue"
import CountryFlag from "@/components/island/CountryFlag.vue"
import {deburrLower} from "@/composables/countries"

/**
  * Picking one of a list: a scrolling panel that holds everything, narrowed by what is typed
  * rather than cut short.
  */
defineOptions({name: "IslandPicker"})

const props = withDefaults(defineProps<{
  /**
   * What can be chosen.
   *
   * [terms] are the other words a row answers to: a nationality is found by the country's name
   * as well as by its own, a person by their handle or their number as well as by their name.
   * The label and the note are always searched, so terms hold what is not drawn.
   */
  options: Array<{key: string; label: string; note?: string; flag?: string; terms?: string[]}>
  /**
   * The rows come from somewhere that does its own searching, so this one does none.
   *
   * What is typed is reported instead, and whatever comes back is what is drawn: a list built
   * from a page of answers must not then have that page filtered again here, or a row the
   * search found is hidden by a word it does not happen to carry.
   */
  remote?: boolean
  /** Said while the rows are being fetched. */
  loading?: boolean
  testidPrefix: string
  placeholder?: string
  /** Said where the list is empty, which is a different thing from nothing matching. */
  emptyNote?: string
  /**
   * The row already chosen, which stays in the list rather than replacing it.
   *
   * Choosing again is the same act as choosing, so it is the same control: swapping the list
   * for a name and a cross would mean two ways to do one thing, and the second only findable
   * once the first had been used.
   */
  selectedKey?: string | null
  disabled?: boolean
  /**
   * Shut, the field is a button showing what was chosen and nothing else.
   *
   * For a choice that sits inside another control, where a box wide enough to type in would be
   * the whole of it: the phone number's country is the case. Open, it is the same search and
   * the same list.
   */
  compact?: boolean
}>(), {
  placeholder: "Search",
  emptyNote: "There is nothing to choose from.",
  selectedKey: null,
  disabled: false,
  compact: false,
  remote: false,
  loading: false,
})

const emit = defineEmits<{
  (event: "pick", key: string): void
  /** What has been typed, for a list that is fetched rather than held. */
  (event: "search", term: string): void
  /** The list came down, which is when a list that is fetched is worth fetching. */
  (event: "opened"): void
}>()

const search = ref("")

/* Opened on an answer, the box keeps it and the list keeps every row until something is typed. */
const typedOnce = ref(false)

/* The row under the pointer: the trigger shows it, so a row can be tried without picking it. */
const hovered = ref<string | null>(null)

/* What Enter or Tab would take, held as a position: typing changes what matches under it. */
const active = ref(0)

/* Two marked rows say nothing about what Enter would take, so the pointer gives way. */
const byKeys = ref(false)

/* Down only while the field is used, over what follows rather than pushing it down. */
const open = ref(false)

/** The box the list hangs off, which is the whole control: in the compact shape the thing that
 * was pressed is a button, and the typing happens inside the list itself. */
const anchor = ref<HTMLElement | null>(null)
const field = ref<HTMLElement | null>(null)
const list = ref<HTMLElement | null>(null)

/* Drawn at the end of the document, since an ancestor that scrolls or is cut would clip it,
   and placed against the field's box instead. */
const box = ref({top: 0, left: 0, width: 0, tall: 240, above: false})

/* The list hangs at the end of the document, so a pinned-dark area's name is carried to it. */
const pinnedDark = ref(false)

const place = () => {
  const at = anchor.value?.getBoundingClientRect()
  if (!at) return
  const room = window.innerHeight - at.bottom
  // Opens upward where there is no room below it, which on a short window there often is not.
  const above = room < 260 && at.top > room
  box.value = {
    // Flush with the field: a gap reads as a panel that came from somewhere else.
    top: above ? at.top : at.bottom,
    left: at.left,
    width: at.width,
    // Only as tall as the side it opened on: a list taller than its room runs back over the
    // field it came from, and the search at its head lands on top of what was typed.
    tall: Math.max(140, Math.min(240, (above ? at.top : room) - 8)),
    above,
  }
}

/**
 * A press anywhere but the field and its list is a press elsewhere, so the list goes.
 *
 * The list is found by its mark rather than by the ref, because it is drawn at the end of the
 * document and this has to hold however it got there.
 */
const elsewhere = (event: Event) => {
  const target = event.target as Element | null
  if (anchor.value?.contains(target)) return
  if (target?.closest?.("[data-island-picker-list]")) return
  open.value = false
}

const watching = (on: boolean) => {
  const how = on ? window.addEventListener : window.removeEventListener
  how("scroll", place, true)
  how("resize", place)
  // Bubbling rather than capturing, so the list's own handler can stop a press on itself from
  // ever reading as a press elsewhere. Capturing would run first and close it under the click.
  const doc = on ? document.addEventListener : document.removeEventListener
  doc("pointerdown", elsewhere)
}

watch(open, async (down) => {
  if (!down) {
    hovered.value = null
    typedOnce.value = false
    search.value = ""
    watching(false)
    return
  }
  typedOnce.value = false
  emit("opened")
  // The row already chosen is where the list opens and what Enter would keep.
  active.value = Math.max(0, props.options.findIndex(one => one.key === props.selectedKey))
  pinnedDark.value = anchor.value?.closest(".island-dark") != null
  place()
  await nextTick()
  place()
  measureRows()
  // Opened from the button there is nothing under the cursor yet, and the search is the only
  // way through a list this long. Whatever is in the box is selected, so typing replaces the
  // answer rather than appending to it.
  if (props.compact) field.value?.focus()
  const box = field.value as HTMLInputElement | null
  box?.select?.()
  // The list opens where the answer is rather than at the top of a list of thousands.
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

const shownOption = computed(() =>
  props.options.find(one => one.key === (hovered.value ?? aimedKey.value ?? props.selectedKey)))

/** The key of the aimed row, where the typing has narrowed the list to something. */
const aimedKey = computed<string | null>(() =>
  (open.value && typedOnce.value ? matches.value[active.value]?.key : null) ?? null)

/* Shut, the box shows what was chosen; open, it shows what is being typed. Two facts, one line,
   and never both at once. */
const onType = (event: Event) => {
  search.value = (event.target as HTMLInputElement).value
  typedOnce.value = true
  active.value = 0
  open.value = true
  emit("search", search.value.trim())
}

/** Keeps the aimed row in the window, which is the only place it is drawn. */
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

const takeAimed = () => {
  const one = matches.value[active.value]
  if (one) pick(one.key)
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
  // Tab moves on, and takes the aimed row with it rather than leaving the typing behind.
  if (event.key === "Tab" && open.value && typedOnce.value) takeAimed()
  if (event.key === "Escape") open.value = false
}

const pick = (key: string) => {
  open.value = false
  hovered.value = null
  typedOnce.value = false
  search.value = ""
  emit("pick", key)
}

/* The list is part of this control though it is drawn elsewhere, so it is found by its mark. */
const leave = (event: FocusEvent) => {
  const next = event.relatedTarget as Element | null
  // Focus going nowhere is not focus leaving: opening a compact picker takes its button out of
  // the document mid-click, and a press outside is already answered by the pointer handler.
  if (!next) return
  if (next && (event.currentTarget as HTMLElement).contains(next)) return
  if (next?.closest?.("[data-island-picker-list]")) return
  open.value = false
}

const matches = computed(() => {
  const asked = typedOnce.value ? deburrLower(search.value.trim()) : ""
  if (props.remote || asked === "") return props.options
  // Accents are dropped on both sides: somebody typing "aland" is looking for Åland, and
  // somebody who types the ring is looking for the same row.
  return props.options.filter(one =>
    [one.label, one.note ?? "", ...(one.terms ?? [])]
      .some(said => deburrLower(said).includes(asked)))
})

/**
  * Under this every match is in the document, so tab reaches each row; past it only the window
  * is built and typing is the way to the rest.
  */
const WINDOWED_FROM = 120
/** Rows drawn either side of the window, so a scroll does not show a gap before it fills. */
const OVERSCAN = 6

const scrolled = ref(0)
const viewport = ref(0)
/** The height of one row plus the gap under it, measured off the first row that is drawn. */
const step = ref(48)

const measureRows = () => {
  const el = list.value
  if (!el) return
  viewport.value = el.clientHeight
  // Two rows rather than one, so whatever sits between them is measured with them: a row with
  // no height is a browser that has not laid the list out, and the last good step is kept.
  const rows = el.querySelectorAll<HTMLElement>("li:not([data-picker-pad])")
  const first = rows[0]
  const second = rows[1]
  if (first && second) {
    step.value = Math.round(second.offsetTop - first.offsetTop) || step.value
    return
  }
  const height = first?.getBoundingClientRect().height ?? 0
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

/* A new search is a new list, so it is read from the top rather than from where the last one
   had been scrolled to. */
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
    >
      <!-- Whatever stands for the answer before it is read: the country field puts its flag
           here, in the same shaded cell the phone field gives its own. It is not a control of
           its own, so a press anywhere opens the one list. -->
      <span
        v-if="$slots.lead"
        class="picker__lead"
      >
        <slot
          name="lead"
          :option="shownOption"
        />
      </span>

      <input
        ref="field"
        :aria-controls="`${testidPrefix}-list`"
        aria-haspopup="listbox"
        :aria-label="placeholder"
        class="picker__search"
        :data-testid="`${testidPrefix}-search`"
        :placeholder="placeholder"
        type="text"
        :value="open && typedOnce ? search : chosen"
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
      v-if="options.length === 0"
      class="picker__note"
      :data-testid="`${testidPrefix}-none`"
    >
      {{ emptyNote }}
    </p>

    <!--
      Drawn at the end of the document: over everything, and out of reach of the ancestors
      that scroll or are cut on the island's diagonal, every one of which would clip it.
    -->
    <Teleport to="body">
      <ul
        v-if="open && matches.length > 0"
        :id="`${testidPrefix}-list`"
        ref="list"
        class="picker__list"
        :class="{'picker__list--above': box.above, 'island-dark': pinnedDark}"
        role="listbox"
        data-island-picker-list
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
            :disabled="disabled"
            type="button"
            @click="pick(one.key)"
            @mouseenter="byKeys ? null : (hovered = one.key)"
            @mouseleave="hovered = null"
          >
            <country-flag
              v-if="one.flag"
              :code="one.flag"
            />
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
        data-island-picker-list
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

/* Square: the diagonal is a button's shape, and on a field it cut the corners off the text. */
/* One box holding whatever stands for the answer, the typing and the mark that says there is a
   list: it is one control, so it draws one line under all three. */
.picker__field {
  display: flex;
  align-items: stretch;
  width: 100%;
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border-bottom: 1px solid var(--color-hairline);
}

.picker__field:focus-within {
  border-bottom-color: var(--color-brand);
  background-color: color-mix(in oklab, var(--color-chalk) 10%, transparent);
}


/* A shade deeper than the rest of the box, the way the phone field tells its country apart, and
   the same width as that one so a column of fields lines up down its left edge. */
.picker__lead {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: var(--field-lead, 4.35rem);
  background-color: color-mix(in oklab, var(--color-chalk) 6%, transparent);
}

/* The mark belongs to the box, not to a cell of its own: only what stands for the answer is
   set apart. */
.picker__caret--field {
  display: grid;
  place-items: center;
  padding: 0 0.7rem;
  background: none;
  cursor: pointer;
}

/* The box around it carries the ground and the rule, so the input draws neither: two of each
   read as a field inside a field. */
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

/* The search the compact shape carries at the head of its own list, since its trigger is a
   button with no room to type in. */
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

/* Scrolls rather than being cut short: the box above is what narrows it. */
.picker__list {
  position: fixed;
  /* Above the dialog it is opened from, which sits at 2401: a menu belongs over the surface
     that raised it, the way every other overlay on the site stacks.
     
     And taking its own pointer events back. A modal dialog turns them off on the body while it
     is open, so that everything behind it is inert -- but this is drawn at the end of the body
     to escape the dialog's clipping, and would be made inert along with the page it is over. */
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
  box-shadow: 0 1rem 2rem rgb(0 0 0 / 45%);
  overscroll-behavior: contain;
}

/* Anchored by its bottom edge where it opens upward, so it grows away from the field. */
.picker__list--above {
  transform: translateY(-100%);
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
  /* A line between rows rather than a box around each: the list is read down, and a hairline
     is what keeps two rows from running together without drawing six boxes. */
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

/* Read with the keys, the pointer marks nothing: whatever it happens to rest on is not what
   Enter would take. */
.picker__row--quiet:hover:not(.picker__row--active)::before {
  scale: 0 1;
}

/* Chosen, and marked down its edge rather than filled: a row under the pointer is already
   washed in the same blue, and a filled row reads as that hover stuck on. */
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

.picker__label {
  flex-grow: 1;
  min-width: 0;
  font-size: 0.92rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* At the end of the row rather than under the label: it is a second fact about the row, and a
   column of them is read down. */
.picker__note-inline {
  flex: none;
  font-family: var(--font-bitmap);
  font-size: 0.72rem;
  color: var(--color-ash);
}

.picker__note {
  margin: 0;
  font-family: var(--font-body);
  font-size: 0.85rem;
  color: var(--color-ash);
}

@media (prefers-reduced-motion: reduce) {
  .picker__row::before {
    transition: none;
  }
}
</style>
