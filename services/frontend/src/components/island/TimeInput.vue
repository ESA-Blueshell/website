<script lang="ts" setup>
/* A time of day, typed as hh:mm or stepped to on a panel of an hour and a minute. The value kept
   is the 24-hour clock the api stores. */
import {computed, ref, watch} from "vue"
import {useAnchoredPanel} from "./useAnchoredPanel"

defineOptions({name: "TimeInput", inheritAttrs: false})

const {
  invalid = false,
  disabled = false,
  controlId = undefined,
  describedBy = undefined,
  /** How far the minute steps, in minutes. */
  step = 15,
  bare = false,
  anchorTo = undefined,
  testid = undefined,
} = defineProps<{
  invalid?: boolean
  disabled?: boolean
  controlId?: string
  describedBy?: string
  step?: number
  /** Drawn without its own box, inside one it shares with another control. */
  bare?: boolean
  /** The box the panel hangs from, where that is the shared box rather than this control. */
  anchorTo?: HTMLElement | null
  testid?: string
}>()

const value = defineModel<string>({default: ""})

const DAY = 24 * 60

const said = (minutes: number): string =>
  `${`${Math.floor(minutes / 60)}`.padStart(2, "0")}:${`${minutes % 60}`.padStart(2, "0")}`

const minutesOf = (held: string): number | null => {
  const parts = /^(\d{2}):(\d{2})$/.exec(held)
  return parts ? Number(parts[1]) * 60 + Number(parts[2]) : null
}

const typed = ref("")
watch(value, (held) => {
  typed.value = held
}, {immediate: true})

const onType = (event: Event) => {
  const raw = (event.target as HTMLInputElement).value
  typed.value = raw
  const parts = /^(\d{1,2})[:.]?(\d{2})$/.exec(raw.trim())
  if (!parts) {
    if (raw.trim() === "") value.value = ""
    return
  }
  const hour = Number(parts[1])
  const minute = Number(parts[2])
  if (hour > 23 || minute > 59) return
  value.value = said(hour * 60 + minute)
}

const open = ref(false)
const own = ref<HTMLElement | null>(null)
const anchor = computed<HTMLElement | null>(() => anchorTo ?? own.value)
const box = useAnchoredPanel(anchor, open, 220)

/** The time now, on the minute step, which is where stepping from nothing starts. */
const now = (): number => {
  const at = new Date()
  const minutes = at.getHours() * 60 + at.getMinutes()
  return minutes - (minutes % step)
}

const held = computed<number>(() => minutesOf(value.value) ?? now())
const hour = computed<string>(() => said(held.value).slice(0, 2))
const minute = computed<string>(() => said(held.value).slice(3))

const wrap = (minutes: number): number => ((minutes % DAY) + DAY) % DAY

const stepHour = (by: number) => {
  value.value = said(wrap(held.value + by * 60))
}

/* A minute off the step lands on the step first, rather than keeping its odd remainder. */
const stepMinute = (by: number) => {
  const off = held.value % step
  const onStep = off === 0 ? held.value + by * step : by > 0 ? held.value + step - off : held.value - off
  value.value = said(wrap(onStep))
}

const setNow = () => {
  value.value = said(now())
  open.value = false
}

const clear = () => {
  value.value = ""
  typed.value = ""
  open.value = false
}

const stepSaid = computed<string>(() => `${step} minute${step === 1 ? "" : "s"}`)
</script>

<template>
  <span
    ref="own"
    class="island-time"
    :class="{'island-time--wrong': invalid, 'island-time--bare': bare, 'island-time--open': open}"
  >
    <input
      :id="controlId"
      :aria-describedby="describedBy"
      :aria-invalid="invalid || undefined"
      class="island-time__typed"
      :data-testid="testid"
      :disabled="disabled"
      inputmode="numeric"
      placeholder="hh:mm"
      type="text"
      :value="typed"
      v-bind="$attrs"
      @input="onType"
    >

    <button
      :aria-expanded="open"
      aria-label="Pick a time"
      class="island-time__open"
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
        <circle
          cx="12"
          cy="12"
          r="8.5"
        />
        <path d="M12 7.5V12l3 2" />
      </svg>
    </button>

    <Teleport to="body">
      <div
        v-if="open"
        aria-label="Pick a time"
        class="island-time__panel"
        :class="{'island-time__panel--above': box.above}"
        :data-testid="testid ? `${testid}-panel` : undefined"
        role="group"
        :style="{top: `${box.top}px`, left: `${box.left}px`, width: `${box.width}px`}"
        @mousedown.stop
        @pointerdown.stop
      >
        <div class="island-time__column">
          <button
            aria-label="An hour later"
            class="island-time__step"
            :data-testid="testid ? `${testid}-hour-up` : undefined"
            type="button"
            @click="stepHour(1)"
          >
            <svg
              aria-hidden="true"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              viewBox="0 0 24 24"
            ><path d="M6 14.5 12 8.5l6 6" /></svg>
          </button>
          <span
            aria-live="polite"
            class="island-time__number"
            :data-testid="testid ? `${testid}-hour` : undefined"
          >{{ hour }}</span>
          <span class="island-time__unit">hour</span>
          <button
            aria-label="An hour earlier"
            class="island-time__step"
            :data-testid="testid ? `${testid}-hour-down` : undefined"
            type="button"
            @click="stepHour(-1)"
          >
            <svg
              aria-hidden="true"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              viewBox="0 0 24 24"
            ><path d="M6 9.5 12 15.5l6-6" /></svg>
          </button>
        </div>

        <span
          aria-hidden="true"
          class="island-time__colon"
        >:</span>

        <div class="island-time__column">
          <button
            :aria-label="`${stepSaid} later`"
            class="island-time__step"
            :data-testid="testid ? `${testid}-minute-up` : undefined"
            type="button"
            @click="stepMinute(1)"
          >
            <svg
              aria-hidden="true"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              viewBox="0 0 24 24"
            ><path d="M6 14.5 12 8.5l6 6" /></svg>
          </button>
          <span
            aria-live="polite"
            class="island-time__number"
            :data-testid="testid ? `${testid}-minute` : undefined"
          >{{ minute }}</span>
          <span class="island-time__unit">minutes</span>
          <button
            :aria-label="`${stepSaid} earlier`"
            class="island-time__step"
            :data-testid="testid ? `${testid}-minute-down` : undefined"
            type="button"
            @click="stepMinute(-1)"
          >
            <svg
              aria-hidden="true"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              viewBox="0 0 24 24"
            ><path d="M6 9.5 12 15.5l6-6" /></svg>
          </button>
        </div>

        <div class="island-time__foot">
          <button
            class="island-time__clear"
            :data-testid="testid ? `${testid}-clear` : undefined"
            type="button"
            @click="clear"
          >
            Clear
          </button>
          <button
            class="island-time__now"
            :data-testid="testid ? `${testid}-now` : undefined"
            type="button"
            @click="setNow"
          >
            Now
          </button>
        </div>
      </div>
    </Teleport>
  </span>
</template>

<style scoped>
.island-time {
  position: relative;
  display: flex;
  align-items: stretch;
  width: 100%;
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border-bottom: 1px solid var(--color-ok);
}

.island-time:focus-within,
.island-time--open {
  background-color: color-mix(in oklab, var(--color-chalk) 10%, transparent);
  border-bottom-color: var(--color-brand);
}

.island-time--wrong {
  border-bottom-color: var(--color-wrong);
}

/* Inside a box it shares, the box is the shared one's. */
.island-time--bare,
.island-time--bare:focus-within,
.island-time--bare.island-time--open {
  background: none;
  border-bottom: 0;
}

.island-time__typed {
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

.island-time__typed::placeholder {
  color: var(--color-ash);
}

.island-time__open {
  display: grid;
  flex: none;
  place-items: center;
  padding: 0 0.7rem;
  color: var(--color-ash);
  cursor: pointer;
  background: none;
  border: 0;
}

.island-time__open:hover:not(:disabled) {
  color: var(--color-chalk);
}

.island-time__open svg {
  width: 1.1rem;
  height: 1.1rem;
}

/* Flush from the field: as wide as it, and its top edge is the field's bottom rule. */
.island-time__panel {
  position: fixed;
  z-index: 2500;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  row-gap: 0.6rem;
  align-items: center;
  padding: 0.8rem;
  background-color: var(--color-raised);
  border: 1px solid var(--color-hairline);
  border-top: 0;
  box-shadow: 0 1rem 2rem rgb(0 0 0 / 35%);
}

.island-time__panel--above {
  translate: 0 -100%;
  border-top: 1px solid var(--color-hairline);
  border-bottom: 0;
}

.island-time__column {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.15rem;
}

.island-time__step {
  display: grid;
  place-items: center;
  width: 2.25rem;
  height: 2.25rem;
  padding: 0;
  color: var(--color-ash);
  cursor: pointer;
  background: none;
  border: 0;
}

.island-time__step:hover,
.island-time__step:focus-visible {
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 8%, transparent);
}

.island-time__step svg {
  width: 16px;
  height: 16px;
}

.island-time__number {
  font-family: var(--font-display);
  font-size: 2.2rem;
  line-height: 1.1;
  color: var(--color-chalk);
}

.island-time__unit {
  font-family: var(--font-bitmap);
  font-size: 0.62rem;
  color: var(--color-ash);
  text-transform: uppercase;
}

.island-time__colon {
  padding-bottom: 1.1rem;
  font-family: var(--font-display);
  font-size: 2rem;
  color: var(--color-ash);
}

.island-time__foot {
  display: flex;
  grid-column: 1 / -1;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.island-time__clear,
.island-time__now {
  padding: 0.3rem 0.7rem;
  font-family: var(--font-bitmap);
  font-size: 0.7rem;
  text-transform: uppercase;
  cursor: pointer;
}

.island-time__clear {
  color: var(--color-ash);
  background: none;
  border: 1px solid var(--color-hairline);
}

.island-time__clear:hover {
  color: var(--color-chalk);
  border-color: var(--color-brand);
}

.island-time__now {
  color: var(--color-void);
  background: var(--color-brand);
  border: 0;
}

.island-time__now:hover {
  background: var(--color-brand-lit);
}
</style>
