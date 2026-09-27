<script lang="ts">
/** Which way the set moves: back towards its start, or on towards its end. */
export type PanWay = "back" | "on"
</script>

<script lang="ts" setup>
import {computed} from "vue"
import {useMotionAllowed} from "@/components/island/useMotionAllowed"

defineOptions({name: "PanChevron"})

const {way, live = false, testid = undefined} = defineProps<{
  way: PanWay
  /** What it does, since a glyph on its own says nothing to a reader being told the page. */
  label: string
  /** The set is travelling this way right now, so the chevron shows the same as under a pointer. */
  live?: boolean
  testid?: string
}>()

defineEmits<{pan: []}>()

/* Decorative: the chevron is as legible standing still, so a shortened change is honest. */
const motion = useMotionAllowed()
const settle = computed<string>(() => `${motion.duration(0.22)}s`)

const classes = computed(() => ["pan-chevron", `pan-chevron--${way}`, {"pan-chevron--live": live}])
</script>

<template>
  <button
    :aria-label="label"
    :class="classes"
    :data-testid="testid"
    :style="{'--settle': settle}"
    type="button"
    @click="$emit('pan')"
  >
    <svg
      aria-hidden="true"
      fill="none"
      stroke="currentColor"
      stroke-linecap="round"
      stroke-linejoin="round"
      stroke-width="1.6"
      viewBox="0 0 24 24"
    >
      <path :d="way === 'back' ? 'M14.5 5.5 8 12l6.5 6.5' : 'M9.5 5.5 16 12l-6.5 6.5'" />
    </svg>
  </button>
</template>

<style scoped>
/* Only the chevron takes a click, so what lies under the fade is still there to be pressed.
   The fade bleeds past the button by --pan-bleed, which the caller sets. */
.pan-chevron {
  --pan-bleed: 26px;
  --pan-fade: var(--color-ground);

  position: absolute;
  top: 50%;
  z-index: 3;
  translate: 0 -50%;
  display: grid;
  place-items: center;
  width: 44px;
  height: 52px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-chalk);
  cursor: pointer;
}

.pan-chevron::before {
  content: "";
  position: absolute;
  top: calc(var(--pan-bleed) * -1);
  bottom: calc(var(--pan-bleed) * -1);
  pointer-events: none;
  opacity: 0.72;
  transition: opacity var(--settle, 0.22s) ease;
}

.pan-chevron--live::before,
.pan-chevron:hover::before,
.pan-chevron:focus-visible::before {
  opacity: 1;
}

.pan-chevron svg {
  position: relative;
  width: 26px;
  height: 26px;
  opacity: 0.78;
  transition: scale var(--settle, 0.22s) ease, opacity var(--settle, 0.22s) ease;
}

.pan-chevron--live svg,
.pan-chevron:hover svg,
.pan-chevron:focus-visible svg {
  opacity: 1;
  scale: 1.24;
}

.pan-chevron--back {
  left: 0;
}

.pan-chevron--back::before {
  left: 0;
  right: -20px;
  background: linear-gradient(to right, color-mix(in oklab, var(--pan-fade) 58%, transparent), transparent);
}

.pan-chevron--on {
  right: 0;
}

.pan-chevron--on::before {
  left: -20px;
  right: 0;
  background: linear-gradient(to left, color-mix(in oklab, var(--pan-fade) 58%, transparent), transparent);
}
</style>
