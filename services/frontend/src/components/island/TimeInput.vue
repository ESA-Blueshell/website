<script lang="ts" setup>
/* A time of day, typed as hh:mm or taken off a list of the quarter hours. The value kept is
   the 24-hour clock the api stores. */
import {computed, onBeforeUnmount, nextTick, ref, watch} from "vue"

defineOptions({name: "TimeInput", inheritAttrs: false})

const {
  invalid = false,
  disabled = false,
  controlId = undefined,
  describedBy = undefined,
  /** How far apart the offered times sit, in minutes. */
  step = 15,
  testid = undefined,
} = defineProps<{
  invalid?: boolean
  disabled?: boolean
  controlId?: string
  describedBy?: string
  step?: number
  testid?: string
}>()

const value = defineModel<string>({default: ""})

const said = (minutes: number): string =>
  `${`${Math.floor(minutes / 60)}`.padStart(2, "0")}:${`${minutes % 60}`.padStart(2, "0")}`

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
const anchor = ref<HTMLElement | null>(null)
const list = ref<HTMLElement | null>(null)
const box = ref({top: 0, left: 0, width: 0, above: false})

const times = computed<string[]>(() =>
  Array.from({length: Math.ceil((24 * 60) / step)}, (_, at) => said(at * step)))

const place = () => {
  const at = (anchor.value as HTMLElement).getBoundingClientRect()
  const room = window.innerHeight - at.bottom
  const above = room < 260 && at.top > room
  box.value = {top: above ? at.top : at.bottom, left: at.left, width: at.width, above}
}

/* The list stops its own presses, so a press reaching here came from outside the field. */
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
  // The list opens where the hour already chosen is, rather than at midnight.
  const at = times.value.indexOf(value.value)
  if (list.value && at > 0) list.value.scrollTop = Math.max(0, at * 32 - 64)
  watching(true)
})

onBeforeUnmount(() => watching(false))

const take = (one: string) => {
  value.value = one
  open.value = false
}
</script>

<template>
  <span
    ref="anchor"
    class="island-time"
    :class="{'island-time--wrong': invalid}"
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
      aria-label="The times on offer"
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
      <ul
        v-if="open"
        ref="list"
        class="island-time__list"
        :class="{'island-time__list--above': box.above}"
        :data-testid="testid ? `${testid}-list` : undefined"
        role="listbox"
        :style="{top: `${box.top}px`, left: `${box.left}px`, width: `${box.width}px`}"
        @mousedown.stop
        @pointerdown.stop
      >
        <li
          v-for="one in times"
          :key="one"
        >
          <button
            :aria-selected="one === value"
            class="island-time__row"
            :class="{'island-time__row--on': one === value}"
            :data-testid="testid ? `${testid}-${one}` : undefined"
            role="option"
            type="button"
            @click="take(one)"
          >{{ one }}</button>
        </li>
      </ul>
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

.island-time:focus-within {
  background-color: color-mix(in oklab, var(--color-chalk) 10%, transparent);
  border-bottom-color: var(--color-brand);
}

.island-time--wrong {
  border-bottom-color: var(--color-wrong);
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

/* Drawn at the end of the document: an ancestor that scrolls or is cut would clip it. */
.island-time__list {
  position: fixed;
  z-index: 2500;
  max-height: 240px;
  margin: 0;
  overflow-y: auto;
  list-style: none;
  background-color: var(--color-raised);
  border: 1px solid var(--color-hairline);
}

.island-time__list--above {
  translate: 0 -100%;
}

.island-time__row {
  width: 100%;
  padding: 0.45rem 1rem;
  font-family: var(--font-body);
  font-size: 0.85rem;
  color: var(--color-chalk);
  text-align: left;
  cursor: pointer;
  background: none;
  border: 0;
}

.island-time__row:hover {
  background-color: color-mix(in oklab, var(--color-brand) 26%, transparent);
}

.island-time__row--on {
  box-shadow: inset 2px 0 0 var(--color-brand);
}
</style>
