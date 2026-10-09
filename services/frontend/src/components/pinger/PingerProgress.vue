<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"

/**
 * The live progress under the canvas, restored from the old watch page: the numbers the cluster is
 * putting up, a meter that fills across the current pass, and a chart of the rate over the last
 * stretch of seconds. All of it tracks the total and rate the board pushes, so it moves on its own.
 */
defineOptions({name: "PingerProgress"})

const WINDOW_SECONDS = 60

const props = defineProps<{
  /** The cluster painter's cumulative pixels, as the board last pushed it. */
  sent: number
  /** Its live rate, pixels a second. */
  pps: number
  /** Pixels in one pass over the logo, measured from the image by the canvas. */
  passTotal: number
  running: boolean
}>()

const reduceMotion = typeof matchMedia === "function" && matchMedia("(prefers-reduced-motion: reduce)").matches

let baseSent = props.sent
let baseAt = now()
const liveSent = ref(props.sent)

function now(): number {
  return typeof performance !== "undefined" ? performance.now() : Date.now()
}

watch(() => props.sent, (value) => {
  baseSent = value
  baseAt = now()
})

/** The pixels within the current pass, and how far that is across it. */
const passDone = computed(() => (props.passTotal > 0 ? liveSent.value % props.passTotal : 0))
const passPercent = computed(() => (props.passTotal > 0 ? Math.min(100, (passDone.value / props.passTotal) * 100) : 0))
const passes = computed(() => (props.passTotal > 0 ? Math.floor(liveSent.value / props.passTotal) : 0))

const eta = computed<string>(() => {
  if (!props.running || props.pps <= 0 || props.passTotal <= 0) return "—"
  const left = props.passTotal - passDone.value
  const seconds = Math.max(0, Math.round(left / props.pps))
  if (seconds >= 60) return `${Math.floor(seconds / 60)}m ${seconds % 60}s left in this pass`
  return `${seconds}s left in this pass`
})

const compact = (n: number): string => {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`
  return `${Math.round(n)}`
}
const thousands = (n: number): string => Math.round(n).toLocaleString("en")

// The rate chart samples the live pps each second and keeps the last window of them.
const rates = ref<number[]>([])
const rateCeil = computed<number>(() => {
  const peak = Math.max(1, ...rates.value, props.pps)
  const step = peak > 20000 ? 5000 : peak > 2000 ? 1000 : 100
  return Math.ceil((peak * 1.15) / step) * step
})

const points = computed<string>(() => pointsFrom(rates.value, rateCeil.value))
const area = computed<string>(() => {
  const line = pointsFrom(rates.value, rateCeil.value)
  if (!line) return ""
  const n = rates.value.length
  const lastX = n <= 1 ? 100 : 100
  return `0,100 ${line} ${lastX},100`
})
const nowY = computed<number>(() => 100 - Math.min(100, (props.pps / rateCeil.value) * 100))

function pointsFrom(buf: number[], ceil: number): string {
  if (buf.length === 0) return ""
  const n = buf.length
  return buf
    .map((v, i) => {
      const x = n <= 1 ? 0 : (i / (n - 1)) * 100
      const y = 100 - Math.min(100, (v / ceil) * 100)
      return `${x.toFixed(2)},${y.toFixed(2)}`
    })
    .join(" ")
}

let interp = 0
let sampler = 0

onMounted(() => {
  const step = (): void => {
    interp = requestAnimationFrame(step)
    const elapsed = reduceMotion ? 0 : Math.max(0, now() - baseAt) / 1000
    liveSent.value = baseSent + Math.max(0, props.pps) * elapsed
  }
  interp = requestAnimationFrame(step)

  sampler = window.setInterval(() => {
    const next = [...rates.value, props.running ? Math.max(0, props.pps) : 0]
    rates.value = next.slice(-WINDOW_SECONDS)
  }, 1000)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(interp)
  clearInterval(sampler)
})
</script>

<template>
  <div class="progress">
    <div class="numbers">
      <div class="numbers__cell">
        <p class="numbers__value">
          {{ thousands(running ? pps : 0) }}
        </p>
        <p class="numbers__label">
          Packets per second
        </p>
      </div>
      <div class="numbers__cell">
        <p class="numbers__value">
          {{ compact(liveSent) }}
        </p>
        <p class="numbers__label">
          Pixels sent
        </p>
      </div>
      <div class="numbers__cell">
        <p class="numbers__value">
          {{ compact(passes) }}
        </p>
        <p class="numbers__label">
          Passes over the logo
        </p>
      </div>
    </div>

    <div class="meter">
      <div class="meter__head">
        <span class="meter__label">This pass</span>
        <b class="meter__pct">{{ Math.round(passPercent) }}%</b>
      </div>
      <div
        aria-label="This pass"
        aria-valuemax="100"
        aria-valuemin="0"
        :aria-valuenow="Math.round(passPercent)"
        class="meter__cut"
        role="progressbar"
      >
        <span
          class="meter__fill"
          :style="{width: `${passPercent}%`}"
        />
      </div>
      <p class="meter__eta">
        <b>{{ eta }}</b>
      </p>
    </div>

    <div class="rate">
      <div class="rate__head">
        <p class="rate__eyebrow">
          Rate · the last {{ WINDOW_SECONDS }} seconds
        </p>
        <p class="rate__legend">
          <i />Now {{ thousands(running ? pps : 0) }}/s
        </p>
      </div>
      <div class="rate__chart">
        <div class="rate__yaxis">
          <span>{{ thousands(rateCeil) }}</span>
          <span>0</span>
        </div>
        <div class="rate__plot">
          <svg
            aria-hidden="true"
            class="rate__line"
            preserveAspectRatio="none"
            viewBox="0 0 100 100"
          >
            <defs>
              <linearGradient
                id="snt-rate-fill"
                x1="0"
                x2="0"
                y1="0"
                y2="1"
              >
                <stop
                  class="rate__fill-top"
                  offset="0"
                />
                <stop
                  class="rate__fill-bottom"
                  offset="1"
                />
              </linearGradient>
            </defs>
            <polygon
              class="rate__area"
              :points="area"
            />
            <polyline
              class="rate__stroke"
              :points="points"
            />
          </svg>
          <span
            class="rate__now"
            :style="{top: `${nowY}%`}"
          ><span class="rate__dot" /></span>
        </div>
      </div>
      <div class="rate__axis">
        <span>{{ WINDOW_SECONDS }}s ago</span><span>Now</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.progress {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.numbers {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
}

.numbers__cell {
  position: relative;
  padding-inline: 1.25rem;
  min-width: 0;
}

.numbers__cell::before {
  content: "";
  position: absolute;
  top: 0.2rem;
  bottom: 0.2rem;
  left: 0;
  width: 1px;
  background-color: var(--color-hairline);
  transform: skewX(-12deg);
}

.numbers__cell:first-child {
  padding-inline-start: 0;
}

.numbers__cell:first-child::before {
  display: none;
}

/* The body face, not the display one: its figures are tabular, so a ticking number holds its width. */
.numbers__value {
  font-family: var(--font-body);
  font-weight: 700;
  font-size: clamp(1.9rem, 5vw, 2.8rem);
  line-height: 1;
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum" 1;
}

.numbers__label {
  margin-top: 0.4rem;
  max-width: 11rem;
  font-size: 0.8rem;
  line-height: 1.35;
  color: var(--color-ash);
}

.meter__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 0.55rem;
}

.meter__label {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.meter__pct {
  font-family: var(--font-body);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum" 1;
}

.meter__cut {
  position: relative;
  height: 0.55rem;
  overflow: hidden;
  background: color-mix(in oklab, var(--color-chalk) 6%, transparent);
  clip-path: polygon(0.3rem 0, 100% 0, calc(100% - 0.3rem) 100%, 0 100%);
}

/* No width transition: the fill tracks the per-frame interpolated total, so it climbs smoothly to
   the right and, when a pass wraps, snaps instantly back to the left rather than animating down. */
.meter__fill {
  position: absolute;
  inset: 0 auto 0 0;
  min-width: 0.3rem;
  background: linear-gradient(90deg, var(--color-brand), var(--color-brand-lit));
}

.meter__eta {
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum" 1;
  margin-top: 0.6rem;
  text-align: center;
  font-size: 0.82rem;
  color: var(--color-ash);
}

.meter__eta b {
  color: var(--color-chalk);
}

.rate__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
}

.rate__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.rate__legend {
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum" 1;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.82rem;
  color: var(--color-ash);
}

.rate__legend i {
  width: 18px;
  height: 0;
  border-top: 2px solid var(--color-brand);
}

.rate {
  --gutter: 3.5rem;
}

.rate__chart {
  display: flex;
  align-items: stretch;
  gap: 0.6rem;
}

.rate__yaxis {
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum" 1;
  flex: 0 0 calc(var(--gutter) - 0.6rem);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 0.1rem 0;
  text-align: right;
  font-family: var(--font-bitmap, ui-monospace, monospace);
  font-size: 8px;
  letter-spacing: 0.04em;
  color: var(--color-ash);
}

.rate__plot {
  position: relative;
  flex: 1 1 auto;
  height: 9rem;
  border-bottom: 1px solid var(--color-hairline);
}

.rate__line {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  overflow: visible;
}

.rate__area {
  fill: url(#snt-rate-fill);
  stroke: none;
}

.rate__fill-top {
  stop-color: var(--color-brand-lit);
  stop-opacity: 0.42;
}

.rate__fill-bottom {
  stop-color: var(--color-brand-lit);
  stop-opacity: 0.03;
}

.rate__stroke {
  fill: none;
  stroke: var(--color-brand);
  stroke-width: 2;
  vector-effect: non-scaling-stroke;
  stroke-linejoin: round;
  stroke-linecap: round;
}

.rate__now {
  position: absolute;
  right: 0.3rem;
  display: flex;
  align-items: center;
  transform: translateY(-50%);
  transition: top 900ms linear;
}

.rate__dot {
  width: 7px;
  height: 7px;
  border-radius: 9999px;
  background: var(--color-brand);
  box-shadow: 0 0 0 3px color-mix(in oklab, var(--color-brand) 30%, transparent);
}

.rate__axis {
  display: flex;
  justify-content: space-between;
  margin-top: 0.45rem;
  margin-left: var(--gutter);
  font-size: 0.75rem;
  color: var(--color-ash);
}

@media (max-width: 639px) {
  .numbers {
    gap: 0.6rem;
  }

  .numbers__value {
    font-size: clamp(1.5rem, 8vw, 2rem);
  }
}

@media (prefers-reduced-motion: reduce) {
  .meter__fill,
  .rate__now {
    transition: none;
  }
}
</style>
