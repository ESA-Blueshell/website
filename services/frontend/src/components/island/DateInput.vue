<script lang="ts" setup>
/* A date, typed as dd/mm/yyyy or taken off a calendar the island draws itself. The value kept
   is the ISO day the api stores. */
import {computed, onBeforeUnmount, nextTick, ref, watch} from "vue"

defineOptions({name: "DateInput", inheritAttrs: false})

const {
  invalid = false,
  disabled = false,
  controlId = undefined,
  describedBy = undefined,
  min = undefined,
  max = undefined,
  testid = undefined,
} = defineProps<{
  invalid?: boolean
  disabled?: boolean
  controlId?: string
  describedBy?: string
  /** The earliest and latest day that may be chosen, as the ISO days a form writes. */
  min?: string
  max?: string
  testid?: string
}>()

const value = defineModel<string>({default: ""})

const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August",
  "September", "October", "November", "December"]

/** Noon rather than midnight, so a timezone cannot move the day either way. */
const dayFrom = (iso: string): Date | null => {
  const parts = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
  if (!parts) return null
  const [, year, month, day] = parts
  return new Date(Number(year), Number(month) - 1, Number(day), 12)
}

const isoOf = (at: Date): string =>
  `${at.getFullYear()}`.padStart(4, "0")
  + `-${`${at.getMonth() + 1}`.padStart(2, "0")}`
  + `-${`${at.getDate()}`.padStart(2, "0")}`

const written = (at: Date): string =>
  `${`${at.getDate()}`.padStart(2, "0")}/${`${at.getMonth() + 1}`.padStart(2, "0")}/${at.getFullYear()}`

/* What the box shows: the day as it is written here, and whatever is being typed until it
   parses. */
const typed = ref("")
watch(value, (iso) => {
  const at = iso ? dayFrom(iso) : null
  typed.value = at ? written(at) : ""
}, {immediate: true})

/* Written as a reader writes it, or pasted as the api stores it. */
const read = (raw: string): [number, number, number] | null => {
  const iso = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(raw.trim())
  if (iso) return [Number(iso[1]), Number(iso[2]), Number(iso[3])]
  const said = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(raw.replaceAll(/[.\-\s]/g, "/"))
  return said ? [Number(said[3]), Number(said[2]), Number(said[1])] : null
}

const onType = (event: Event) => {
  const raw = (event.target as HTMLInputElement).value
  typed.value = raw
  const parts = read(raw)
  if (!parts) {
    if (raw.trim() === "") value.value = ""
    return
  }
  const [year, month, day] = parts
  const at = new Date(year, month - 1, day, 12)
  if (at.getMonth() !== month - 1) return
  value.value = isoOf(at)
}

const open = ref(false)
const anchor = ref<HTMLElement | null>(null)
const box = ref({top: 0, left: 0, width: 0, above: false})

/** The month the calendar is showing, which opens on the chosen day or on this one. */
const shownMonth = ref(new Date())
watch(open, (down) => {
  if (!down) return
  shownMonth.value = dayFrom(value.value) ?? new Date()
})

const place = () => {
  const at = (anchor.value as HTMLElement).getBoundingClientRect()
  const room = window.innerHeight - at.bottom
  const above = room < 340 && at.top > room
  box.value = {top: above ? at.top : at.bottom, left: at.left, width: at.width, above}
}

/* The panel stops its own presses, so a press reaching here came from outside the field. */
const elsewhere = (event: Event) => {
  if (anchor.value?.contains(event.target as Element | null)) return
  open.value = false
}

const onEscape = (event: KeyboardEvent) => {
  if (event.key === "Escape") open.value = false
}

// Called through `document`: a method taken off it and called bare is refused.
const watching = (on: boolean) => {
  if (on) {
    window.addEventListener("scroll", place, true)
    window.addEventListener("resize", place)
    document.addEventListener("pointerdown", elsewhere)
    document.addEventListener("keydown", onEscape)
    return
  }
  window.removeEventListener("scroll", place, true)
  window.removeEventListener("resize", place)
  document.removeEventListener("pointerdown", elsewhere)
  document.removeEventListener("keydown", onEscape)
}

watch(open, async (down) => {
  if (!down) {
    watching(false)
    return
  }
  place()
  await nextTick()
  place()
  watching(true)
})

onBeforeUnmount(() => watching(false))

const monthSaid = computed<string>(() =>
  `${MONTHS[shownMonth.value.getMonth()]} ${shownMonth.value.getFullYear()}`)

const moveMonth = (by: number) => {
  const at = shownMonth.value
  shownMonth.value = new Date(at.getFullYear(), at.getMonth() + by, 1, 12)
}

const today = isoOf(new Date())

/** Six weeks from the Monday on or before the first of the month, so the grid never reflows. */
const weeks = computed(() => {
  const first = new Date(shownMonth.value.getFullYear(), shownMonth.value.getMonth(), 1, 12)
  const lead = (first.getDay() + 6) % 7
  const from = new Date(first.getFullYear(), first.getMonth(), 1 - lead, 12)
  return Array.from({length: 6}, (_, week) => Array.from({length: 7}, (_, day) => {
    const at = new Date(from.getFullYear(), from.getMonth(), from.getDate() + week * 7 + day, 12)
    const iso = isoOf(at)
    return {
      iso,
      day: at.getDate(),
      thisMonth: at.getMonth() === shownMonth.value.getMonth(),
      weekend: day > 4,
      chosen: iso === value.value,
      today: iso === today,
      barred: (min !== undefined && iso < min) || (max !== undefined && iso > max),
    }
  }))
})

const take = (iso: string) => {
  value.value = iso
  open.value = false
}

const clear = () => {
  value.value = ""
  typed.value = ""
  open.value = false
}
</script>

<template>
  <span
    ref="anchor"
    class="island-date"
    :class="{'island-date--wrong': invalid}"
  >
    <input
      :id="controlId"
      :aria-describedby="describedBy"
      :aria-invalid="invalid || undefined"
      class="island-date__typed"
      :data-testid="testid"
      :disabled="disabled"
      inputmode="numeric"
      placeholder="dd/mm/yyyy"
      type="text"
      :value="typed"
      v-bind="$attrs"
      @input="onType"
    >

    <button
      aria-label="Open the calendar"
      :aria-expanded="open"
      class="island-date__open"
      :data-testid="testid ? `${testid}-open` : undefined"
      :disabled="disabled"
      tabindex="-1"
      type="button"
      @click="open = !open"
    >
      <svg
        aria-hidden="true"
        fill="none"
        stroke="currentColor"
        stroke-linecap="round"
        stroke-width="1.6"
        viewBox="0 0 24 24"
      >
        <rect
          height="15"
          rx="0"
          width="17"
          x="3.5"
          y="5"
        />
        <path d="M3.5 9.5h17M8 3.5V6M16 3.5V6" />
      </svg>
    </button>

    <Teleport to="body">
      <div
        v-if="open"
        class="island-date__panel"
        :class="{'island-date__panel--above': box.above}"
        :data-testid="testid ? `${testid}-panel` : undefined"
        :style="{top: `${box.top}px`, left: `${box.left}px`, minWidth: `${box.width}px`}"
        @mousedown.stop
        @pointerdown.stop
      >
        <div class="island-date__head">
          <button
            aria-label="The month before"
            class="island-date__step"
            :data-testid="testid ? `${testid}-back` : undefined"
            type="button"
            @click="moveMonth(-1)"
          >
            &#8249;
          </button>

          <span class="island-date__month">{{ monthSaid }}</span>

          <button
            aria-label="The month after"
            class="island-date__step"
            :data-testid="testid ? `${testid}-on` : undefined"
            type="button"
            @click="moveMonth(1)"
          >
            &#8250;
          </button>
        </div>

        <div class="island-date__grid">
          <span
            v-for="name in DAYS"
            :key="name"
            class="island-date__weekday"
          >{{ name }}</span>

          <template
            v-for="(week, at) in weeks"
            :key="at"
          >
            <button
              v-for="one in week"
              :key="one.iso"
              class="island-date__day"
              :class="{
                'island-date__day--outside': !one.thisMonth,
                'island-date__day--weekend': one.weekend,
                'island-date__day--on': one.chosen,
                'island-date__day--today': one.today,
              }"
              :data-testid="testid ? `${testid}-${one.iso}` : undefined"
              :disabled="one.barred"
              type="button"
              @click="take(one.iso)"
            >{{ one.day }}</button>
          </template>
        </div>

        <button
          class="island-date__clear"
          :data-testid="testid ? `${testid}-clear` : undefined"
          type="button"
          @click="clear"
        >
          Clear
        </button>
      </div>
    </Teleport>
  </span>
</template>

<style scoped>
.island-date {
  position: relative;
  display: flex;
  align-items: stretch;
  width: 100%;
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border-bottom: 1px solid var(--color-ok);
}

.island-date:focus-within {
  background-color: color-mix(in oklab, var(--color-chalk) 10%, transparent);
  border-bottom-color: var(--color-brand);
}

.island-date--wrong {
  border-bottom-color: var(--color-wrong);
}

.island-date__typed {
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

.island-date__typed::placeholder {
  color: var(--color-ash);
}

.island-date__open {
  display: grid;
  flex: none;
  place-items: center;
  padding: 0 0.7rem;
  color: var(--color-ash);
  cursor: pointer;
  background: none;
  border: 0;
}

.island-date__open:hover:not(:disabled) {
  color: var(--color-chalk);
}

.island-date__open svg {
  width: 1.1rem;
  height: 1.1rem;
}

/* Drawn at the end of the document: an ancestor that scrolls or is cut would clip it. */
.island-date__panel {
  position: fixed;
  z-index: 2500;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 0.8rem;
  background-color: var(--color-raised);
  border: 1px solid var(--color-hairline);
}

.island-date__panel--above {
  translate: 0 -100%;
}

.island-date__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.island-date__month {
  font-family: var(--font-body);
  font-size: 0.82rem;
  font-weight: 500;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.island-date__step {
  padding: 0.1rem 0.6rem;
  font-size: 1.1rem;
  line-height: 1;
  color: var(--color-ash);
  cursor: pointer;
  background: none;
  border: 0;
}

.island-date__step:hover {
  color: var(--color-brand);
}

.island-date__grid {
  display: grid;
  grid-template-columns: repeat(7, 2rem);
  gap: 0.15rem;
}

.island-date__weekday {
  font-family: var(--font-bitmap);
  font-size: 0.62rem;
  color: var(--color-ash);
  text-align: center;
}

.island-date__day {
  padding: 0.35rem 0;
  font-family: var(--font-body);
  font-size: 0.8rem;
  color: var(--color-chalk);
  cursor: pointer;
  background: none;
  border: 0;
}

.island-date__day:hover:not(:disabled) {
  background-color: color-mix(in oklab, var(--color-brand) 26%, transparent);
}

.island-date__day--outside {
  color: color-mix(in oklab, var(--color-ash) 60%, transparent);
}

.island-date__day--weekend:not(.island-date__day--outside) {
  color: var(--color-ash);
}

/* Marked down its edge, the way a chosen row in a list is. */
.island-date__day--today {
  box-shadow: inset 0 -2px 0 var(--color-ash);
}

.island-date__day--on {
  color: var(--color-void);
  background-color: var(--color-brand);
}

.island-date__day:disabled {
  color: color-mix(in oklab, var(--color-ash) 40%, transparent);
  cursor: not-allowed;
}

.island-date__clear {
  align-self: flex-start;
  padding: 0.3rem 0.7rem;
  font-family: var(--font-bitmap);
  font-size: 0.7rem;
  color: var(--color-ash);
  text-transform: uppercase;
  cursor: pointer;
  background: none;
  border: 1px solid var(--color-hairline);
}

.island-date__clear:hover {
  color: var(--color-chalk);
  border-color: var(--color-brand);
}
</style>
