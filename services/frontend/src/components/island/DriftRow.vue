<script lang="ts">
/** One tile in the row. */
export interface DriftItem {
  id: string | number
  title: string
  href: string
  accent: string
  /** A line under the name, such as a game's channels. */
  sub?: string
  banner?: string | null
  srcset?: string
  /** The letters a plate shows where there is no banner. */
  initials: string
}
</script>

<script lang="ts" setup>
import {useElementSize, useIntersectionObserver, useRafFn} from "@vueuse/core"
import {computed, nextTick, onBeforeUnmount, ref, watch} from "vue"
import {DRAG} from "./dragAxis"
import {DriftMotion} from "./driftMotion"
import {REST_MS} from "./reelMotion"
import {useMotionAllowed} from "./useMotionAllowed"

defineOptions({name: "DriftRow"})

const {items, testidPrefix} = defineProps<{
  items: DriftItem[]
  testidPrefix: string
}>()

const emit = defineEmits<{go: [item: DriftItem]}>()

/** Enough copies that one full pass of the row is always wider than the band. */
const PASS_TILES = 8

/*
 * The row is two identical passes side by side and wraps after one pass, so where it wraps looks
 * exactly like where it started and the loop has no seam. A short list is repeated until one
 * pass is wide enough to fill the band on its own.
 */
const pass = computed<DriftItem[]>(() => {
  if (items.length === 0) return []
  const repeats = Math.ceil(PASS_TILES / items.length)
  return Array.from({length: repeats}, () => items).flat()
})

/** Only the first pass is announced; the second is the same tiles again, for the loop. */
const tiles = computed(() => [
  ...pass.value.map((item, at) => ({item, key: `a${at}`, echo: at >= items.length})),
  ...pass.value.map((item, at) => ({item, key: `b${at}`, echo: true})),
])

const row = ref<HTMLElement | null>(null)
const run = ref<HTMLElement | null>(null)
const motion = new DriftMotion()
const {decorative} = useMotionAllowed()
let suppressClick = false

/* The row is moved by writing its transform, not by rendering: a drag moves it every frame. */
function paint() {
  if (run.value) run.value.style.transform = `translate3d(${(-motion.offset).toFixed(1)}px,0,0)`
}

/** One pass is as wide as the distance from the first tile to its copy in the second pass. */
function measure() {
  const children = run.value?.children
  const echo = children?.[pass.value.length] as HTMLElement | undefined
  motion.loop = echo ? echo.offsetLeft - (children![0] as HTMLElement).offsetLeft : 0
}

const inView = ref(true)
useIntersectionObserver(row, ([entry]) => {
  inView.value = entry?.isIntersecting ?? true
  wake()
})

// Frames run only while the row moves by itself and is on screen; at rest it asks for none.
const clock = useRafFn(({delta, timestamp}) => {
  if (motion.tick(Math.min(48, delta), timestamp, decorative.value)) paint()
  if (!inView.value || !motion.wantsFrames(timestamp, decorative.value)) clock.pause()
}, {immediate: false})

let alarm = 0
function wake() {
  window.clearTimeout(alarm)
  if (!inView.value) return
  if (motion.wantsFrames(performance.now(), decorative.value)) return clock.resume()
  clock.pause()
  // A hand that let go leaves the row resting; it drifts again once the rest is over.
  if (motion.loop > 0 && !motion.held && decorative.value) alarm = window.setTimeout(wake, REST_MS)
}
watch(decorative, wake)
onBeforeUnmount(() => window.clearTimeout(alarm))

const width = useElementSize(run).width
watch([width, () => items.map(item => item.id).join()], async () => {
  await nextTick()
  measure()
  paint()
  wake()
}, {immediate: true})

let pressedAt = 0
function press(event: PointerEvent) {
  pressedAt = event.clientX
  motion.press(event.clientX, performance.now())
  clock.pause()
}

function drag(event: PointerEvent) {
  motion.drag(event.clientX, performance.now())
  // Captured only once it is a drag, so a press on a tile still reaches the tile as a click.
  const target = event.currentTarget as HTMLElement
  if (Math.abs(event.clientX - pressedAt) > DRAG.slop && !target.hasPointerCapture?.(event.pointerId)) {
    target.setPointerCapture?.(event.pointerId)
  }
  paint()
}

function release() {
  if (motion.release(performance.now())) suppressClick = true
  wake()
}

function swipe(event: WheelEvent) {
  if (Math.abs(event.deltaX) <= Math.abs(event.deltaY)) return
  event.preventDefault()
  motion.swipe(event.deltaX, performance.now())
  paint()
  wake()
}

/** The pointer and focus stop the row, so a tile can be read and pressed; a finger does not. */
function hold(held: boolean, event?: PointerEvent) {
  if (event && event.pointerType !== "mouse") return
  motion.held = held
  wake()
}

function follow(event: MouseEvent, item: DriftItem) {
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return
  event.preventDefault()
  if (suppressClick) {
    suppressClick = false
    return
  }
  emit("go", item)
}
</script>

<template>
  <div
    v-if="items.length > 0"
    ref="row"
    class="drift-row"
    :data-testid="`${testidPrefix}-drift`"
    @focusin="hold(true)"
    @focusout="hold(false)"
    @pointercancel="release"
    @pointerdown="press"
    @pointerenter="hold(true, $event)"
    @pointerleave="hold(false, $event)"
    @pointermove="drag"
    @pointerup="release"
    @wheel="swipe"
  >
    <span
      aria-hidden="true"
      class="drift-row__fade drift-row__fade--back"
    />
    <span
      aria-hidden="true"
      class="drift-row__fade drift-row__fade--on"
    />
    <div
      ref="run"
      class="drift-row__run"
    >
      <a
        v-for="tile in tiles"
        :key="tile.key"
        :aria-hidden="tile.echo ? 'true' : undefined"
        class="drift-row__tile"
        :data-testid="tile.echo ? undefined : `${testidPrefix}-tile-${tile.item.id}`"
        :href="tile.item.href"
        :style="{'--accent': tile.item.accent}"
        draggable="false"
        :tabindex="tile.echo ? -1 : undefined"
        @click="follow($event, tile.item)"
      >
        <img
          v-if="tile.item.banner"
          alt=""
          class="drift-row__art"
          draggable="false"
          loading="lazy"
          sizes="336px"
          :src="tile.item.banner"
          :srcset="tile.item.srcset"
        >
        <span
          v-else
          aria-hidden="true"
          class="drift-row__plate"
        ><span>{{ tile.item.initials }}</span></span>
        <span
          aria-hidden="true"
          class="drift-row__shade"
        />
        <span class="drift-row__caption">
          <span class="drift-row__name">{{ tile.item.title }}</span>
          <span
            v-if="tile.item.sub"
            class="drift-row__sub"
          >{{ tile.item.sub }}</span>
        </span>
      </a>
    </div>
  </div>
</template>

<style scoped>
.drift-row {
  --cut: 14px;
  --tile-width: 21rem;
  --tile-gap: 14px;

  position: relative;
  overflow: hidden;
  padding: 0.25rem 0;
  cursor: grab;
  user-select: none;
  touch-action: pan-y;
}

.drift-row:active {
  cursor: grabbing;
}

.drift-row__run {
  display: flex;
  gap: var(--tile-gap);
  width: max-content;
  will-change: transform;
}

.drift-row__tile {
  position: relative;
  flex: none;
  width: var(--tile-width);
  height: 12rem;
  overflow: hidden;
  background-color: var(--color-surface);
  clip-path: polygon(var(--cut) 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
}

.drift-row__tile:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: -4px;
}

.drift-row__art {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(0.5);
  transition: filter 400ms ease, scale 700ms cubic-bezier(0.22, 1, 0.36, 1);
}

.drift-row__tile:hover .drift-row__art,
.drift-row__tile:focus-visible .drift-row__art {
  filter: none;
  scale: 1.05;
}

.drift-row__plate {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background:
    radial-gradient(110% 90% at 0 0, color-mix(in oklab, var(--accent) 30%, transparent), transparent 70%),
    repeating-linear-gradient(-62deg, transparent 0 22px, color-mix(in oklab, var(--accent) 7%, transparent) 22px 24px),
    var(--color-pit);
}

.drift-row__plate > span {
  position: absolute;
  top: -1rem;
  right: 0;
  font-family: var(--font-display);
  font-size: 7rem;
  line-height: 1;
  color: color-mix(in oklab, var(--accent) 22%, transparent);
}

.drift-row__shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, color-mix(in oklab, var(--color-ground) 92%, transparent) 6%, transparent 60%);
}

.drift-row__caption {
  position: absolute;
  right: 1.4rem;
  bottom: 0.9rem;
  left: 1.4rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.drift-row__name {
  font-family: var(--font-display);
  font-size: 1.15rem;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.drift-row__sub {
  font-size: 0.82rem;
  color: var(--color-ash);
}

.drift-row__fade {
  position: absolute;
  top: 0;
  bottom: 0;
  z-index: 2;
  width: 10rem;
  pointer-events: none;
}

.drift-row__fade--back {
  left: 0;
  background: linear-gradient(to right, var(--color-ground), transparent);
}

.drift-row__fade--on {
  right: 0;
  background: linear-gradient(to left, var(--color-ground), transparent);
}

@media (width < 768px) {
  .drift-row {
    --tile-width: 15rem;
  }

  .drift-row__tile {
    height: 8.6rem;
  }

  .drift-row__fade {
    width: 3rem;
  }
}
</style>
