<script lang="ts">
/** One slice on the reel. */
export interface ReelItem {
  id: string | number
  title: string
  href: string
  /** The colour that carries it: the tick, the lit rail cell and a plate. */
  accent: string
  banner?: string | null
  srcset?: string
  icon?: string | null
  /** The letters a plate shows where there is no banner. */
  initials: string
  /** What its rail cell says where there is no icon; the initials otherwise. */
  railLabel?: string
  /** Discord channels, drawn with Discord's mark in the open slice. */
  notes?: string[]
  /** Names drawn as small tags in the open slice, such as the committees behind a game. */
  chips?: string[]
  /** The way to add one more, drawn as a plus rather than as art. */
  plus?: boolean
}
</script>

<script lang="ts" setup>
import {useElementSize} from "@vueuse/core"
import {computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch} from "vue"
import PanChevron from "./PanChevron.vue"
import {DRAG} from "./dragAxis"
import {placeSlices, ReelMotion, type ReelShape} from "./reelMotion"
import {useMotionAllowed} from "./useMotionAllowed"

defineOptions({name: "FlickReel"})

const {
  items,
  testidPrefix,
  drift = 0.22,
  panBackLabel = "Previous",
  panOnLabel = "Next",
} = defineProps<{
  items: ReelItem[]
  testidPrefix: string
  /** Slices a second the belt moves on its own; 0 holds it still. */
  drift?: number
  panBackLabel?: string
  panOnLabel?: string
}>()

const emit = defineEmits<{go: [item: ReelItem]}>()

/** Below this the band is a phone's, and the slices narrow to suit. */
const NARROW_PX = 600
const WIDE: ReelShape & {unit: number} = {rest: 240, open: 608, cut: 30, unit: 300}
/** The widest the reel is drawn. Wider, it is drawn at this width and scaled up whole. */
const WIDEST_PX = 1920

const reel = ref<HTMLElement | null>(null)
const band = ref<HTMLElement | null>(null)
/** How much larger than drawn the reel is shown. Pointer travel is divided by it. */
const outer = useElementSize(reel, undefined, {box: "border-box"}).width
// Before the reel is laid out it measures nothing, and the belt is drawn for a common desktop.
const scale = computed(() => (outer.value > 0 ? Math.max(1, outer.value / WIDEST_PX) : 1))
const width = computed(() => (outer.value > 0 ? outer.value / scale.value : 1440))
const narrow = computed(() => width.value < NARROW_PX)
const shape = computed<ReelShape & {unit: number}>(() =>
  narrow.value ? {rest: 70, open: Math.min(290, Math.round(width.value * 0.74)), cut: 18, unit: 150} : WIDE)

const motionPolicy = useMotionAllowed()
const motion = shallowRef(new ReelMotion(items.length, {unit: shape.value.unit, drift}))
const resting = ref(0)
const slices: (HTMLElement | null)[] = []
let suppressClick = false
/** What was last written onto each slice, so a frame writes only what it changes. */
const written: Record<string, string>[] = []
let frame = 0
/** Set while the belt waits out a rest it will drift after, in place of a frame per tick. */
let alarm = 0
let last = 0

watch(() => [items.map(item => item.id).join(), shape.value.unit] as const, ([, unit]) => {
  motion.value = new ReelMotion(items.length, {unit, drift}, motion.value.position)
  // The slices may be other elements now, holding none of what was written.
  written.length = 0
  void nextTick(paint)
  wake()
})

watch(motionPolicy.reduced, wake)


/* Every write restyles the slice, and `--open` is inherited, so it restyles all it holds: a
   value the frame did not change is left alone. Off the band, a slice is hidden and nothing
   else about it is worth writing. */
const put = (index: number, slice: HTMLElement, key: string, value: string) => {
  const held = written[index] ??= {}
  if (held[key] === value) return
  held[key] = value
  if (key.startsWith("--")) slice.style.setProperty(key, value)
  else slice.style[key as "width"] = value
}

/* The belt is painted by writing onto the slices, not by rendering: a drag moves it every frame,
   and a render per frame across every slice is what made it stutter. */
function paint() {
  for (const placed of placeSlices(items.length, motion.value.position, shape.value, width.value)) {
    const slice = slices[placed.index]
    if (!slice) continue
    const offBand = placed.left > width.value + 40 || placed.left + placed.width < -40
    const hidden = placed.visibility === 0 || offBand
    put(placed.index, slice, "visibility", hidden ? "hidden" : "visible")
    if (hidden) continue
    put(placed.index, slice, "transform", `translate3d(${placed.left.toFixed(1)}px,0,0)`)
    put(placed.index, slice, "width", `${(placed.width - shape.value.cut).toFixed(1)}px`)
    put(placed.index, slice, "opacity", placed.visibility.toFixed(2))
    put(placed.index, slice, "zIndex", String(placed.layer))
    put(placed.index, slice, "--open", placed.openness.toFixed(3))
  }
  resting.value = motion.value.resting
}

/** Whether any of the band is on screen: off it, the belt stands still and costs nothing. */
const inView = ref(true)
let viewing: IntersectionObserver | null = null

const frameOf = (callback: (now: number) => void) =>
  typeof window.requestAnimationFrame === "function"
    ? window.requestAnimationFrame(callback)
    : window.setTimeout(() => callback(performance.now()), 16)

const cancelFrame = (id: number) =>
  typeof window.cancelAnimationFrame === "function" ? window.cancelAnimationFrame(id) : window.clearTimeout(id)

/**
 * Runs the belt's clock while it has somewhere to go. At rest it asks for no frames: a frame per
 * tick while in view held an idle casual page at 11% of a core (#1696).
 */
function wake() {
  if (frame !== 0 || !inView.value) return
  window.clearTimeout(alarm)
  alarm = 0
  last = performance.now()
  frame = frameOf(tick)
}

function tick(now: number) {
  const elapsed = Math.min(48, now - last)
  last = now
  frame = 0
  if (motion.value.tick(elapsed, now, motionPolicy.reduced.value)) paint()
  if (!inView.value) return
  const next = motion.value.wakeAt(now, motionPolicy.reduced.value)
  if (next === null) return
  if (next <= now) frame = frameOf(tick)
  else alarm = window.setTimeout(wake, next - now)
}

onMounted(() => {
  paint()
  wake()
  if (typeof IntersectionObserver === "function" && band.value) {
    viewing = new IntersectionObserver(([entry]) => {
      inView.value = entry?.isIntersecting ?? true
      wake()
    })
    viewing.observe(band.value)
  }
})

onBeforeUnmount(() => {
  cancelFrame(frame)
  window.clearTimeout(alarm)
  viewing?.disconnect()
})

let pressedAt = 0

function press(event: PointerEvent) {
  if ((event.target as HTMLElement).closest("button")) return
  pressedAt = event.clientX
  motion.value.press(event.clientX / scale.value, performance.now())
}

function drag(event: PointerEvent) {
  if (!motion.value.isDragging) return
  motion.value.drag(event.clientX / scale.value, performance.now())
  // Captured only once it is a drag, so a press on a slice still reaches the slice as a click.
  if (Math.abs(event.clientX - pressedAt) > DRAG.slop && !(event.currentTarget as HTMLElement).hasPointerCapture?.(event.pointerId)) {
    (event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
  }
  paint()
}

function release() {
  if (motion.value.release(performance.now())) suppressClick = true
  wake()
}

function leave() {
  motion.value.hovered = false
  wake()
}

function swipe(event: WheelEvent) {
  if (Math.abs(event.deltaX) <= Math.abs(event.deltaY)) return
  event.preventDefault()
  motion.value.swipe(event.deltaX / scale.value, performance.now())
  paint()
  wake()
}

/** A press anywhere on a slice follows it; the rail is where a slice is brought to the middle. */
function choose(event: MouseEvent, item: ReelItem) {
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return
  event.preventDefault()
  if (suppressClick) {
    suppressClick = false
    return
  }
  emit("go", item)
}

function bring(index: number) {
  motion.value.bring(index, performance.now())
  wake()
}

function step(by: number) {
  motion.value.step(by, performance.now())
  wake()
}

const discordMark = "M20.317 4.37a19.79 19.79 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.865-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.74 19.74 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028c.462-.63.874-1.295 1.226-1.994a.076.076 0 0 0-.041-.106 13.1 13.1 0 0 1-1.872-.892.077.077 0 0 1-.008-.128c.126-.094.252-.192.372-.291a.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.197.373.291a.077.077 0 0 1-.006.127 12.3 12.3 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.84 19.84 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.955-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.946 2.418-2.157 2.418z"
</script>

<template>
  <div
    ref="reel"
    class="flick-reel"
    :class="{'flick-reel--narrow': narrow}"
    :data-testid="`${testidPrefix}-reel`"
    :style="{'--k': scale}"
  >
    <div class="flick-reel__scale">
      <div
        ref="band"
        class="flick-reel__band"
        :data-testid="`${testidPrefix}-band`"
        @mouseenter="motion.hovered = true"
        @mouseleave="leave"
        @pointercancel="release"
        @pointerdown="press"
        @pointermove="drag"
        @pointerup="release"
        @wheel="swipe"
      >
        <div class="flick-reel__belt">
          <a
            v-for="(item, index) in items"
            :key="item.id"
            :ref="el => { slices[index] = el as HTMLElement | null }"
            :aria-current="index === resting ? 'true' : undefined"
            class="flick-reel__slice"
            :data-testid="`${testidPrefix}-slice-${item.id}`"
            draggable="false"
            :href="item.href"
            :style="{'--accent': item.accent}"
            @click="choose($event, item)"
            @focus="bring(index)"
          >
            <span class="flick-reel__face">
              <span
                v-if="item.plus"
                aria-hidden="true"
                class="flick-reel__plate flick-reel__plate--plus"
              ><svg
                fill="none"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-width="1.4"
                viewBox="0 0 24 24"
              ><path d="M12 5v14M5 12h14" /></svg></span>
              <img
                v-else-if="item.banner"
                alt=""
                class="flick-reel__art"
                loading="lazy"
                sizes="608px"
                :src="item.banner"
                :srcset="item.srcset"
              >
              <template v-else>
                <span
                  aria-hidden="true"
                  class="flick-reel__plate"
                />
                <span
                  aria-hidden="true"
                  class="flick-reel__initials"
                >{{ item.initials }}</span>
              </template>
              <span
                aria-hidden="true"
                class="flick-reel__glow"
              />
              <span class="flick-reel__body">
                <!-- Tick, icon and name shrink as one around the icon, which the rest keep to. -->
                <span
                  class="flick-reel__title"
                  :class="{'flick-reel__title--bare': !item.icon}"
                >
                  <span
                    aria-hidden="true"
                    class="flick-reel__tick"
                  />
                  <span class="flick-reel__name">
                    <img
                      v-if="item.icon"
                      alt=""
                      :src="item.icon"
                    >
                    <span class="flick-reel__words">{{ item.title }}</span>
                  </span>
                </span>
                <!-- Laid over the room the name rises out of, so their wrapping and fading never
                     move the name. -->
                <span class="flick-reel__more">
                  <span
                    v-for="note in item.notes ?? []"
                    :key="note"
                    class="flick-reel__note"
                  >
                    <svg
                      aria-hidden="true"
                      fill="currentColor"
                      viewBox="0 0 24 24"
                    ><path :d="discordMark" /></svg>{{ note }}
                  </span>
                  <span
                    v-for="chip in item.chips ?? []"
                    :key="chip"
                    class="flick-reel__chip"
                  >{{ chip }}</span>
                  <span
                    v-if="!item.plus"
                    class="flick-reel__open"
                  >Open {{ item.title }} →</span>
                </span>
              </span>
            </span>
          </a>
        </div>

        <pan-chevron
          :label="panBackLabel"
          :testid="`${testidPrefix}-back`"
          way="back"
          @pan="step(-1)"
        />
        <pan-chevron
          :label="panOnLabel"
          :testid="`${testidPrefix}-on`"
          way="on"
          @pan="step(1)"
        />
      </div>

      <div
        class="flick-reel__rail"
        :data-testid="`${testidPrefix}-rail`"
      >
        <button
          v-for="(item, index) in items"
          :key="item.id"
          :aria-label="item.title"
          :aria-pressed="index === resting"
          class="flick-reel__cell"
          :class="{'flick-reel__cell--on': index === resting}"
          :data-testid="`${testidPrefix}-rail-${item.id}`"
          :style="{'--accent': item.accent}"
          type="button"
          @click="bring(index)"
        >
          <svg
            v-if="item.plus"
            aria-hidden="true"
            class="flick-reel__cell-plus"
            fill="none"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-width="1.8"
            viewBox="0 0 24 24"
          ><path d="M12 5v14M5 12h14" /></svg>
          <img
            v-else-if="item.icon"
            alt=""
            :src="item.icon"
          >
          <span v-else>{{ item.railLabel ?? item.initials }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.flick-reel {
  --reel-height: 25rem;
  --rail-height: 3.4rem;
  --cut: 30px;
  /* The lean that puts the cut across the reel's height, which the belt is skewed by once. */
  --lean: atan2(var(--cut), var(--reel-height));

  position: relative;
  width: 100%;
}

/* Scaled rather than zoomed: a transform leaves widths and positions in the units the belt's
   own arithmetic is done in. The margin gives back the height the transform does not take. */
.flick-reel__scale {
  width: calc(100% / var(--k));
  margin-bottom: calc((var(--reel-height) + var(--rail-height)) * (var(--k) - 1));
  transform: scale(var(--k));
  transform-origin: 0 0;
}

.flick-reel--narrow {
  --reel-height: 19rem;
  --cut: 18px;
}

.flick-reel__band {
  position: relative;
  height: var(--reel-height);
  overflow: hidden;
  cursor: grab;
  touch-action: pan-y;
  user-select: none;
  overscroll-behavior-x: contain;
}

.flick-reel__band:active {
  cursor: grabbing;
}

/* PanChevron's fade, stretched to the band's full height. */
.flick-reel__band :deep(.pan-chevron) {
  --pan-bleed: calc(var(--reel-height) / 2 - 26px);
  --pan-fade: var(--color-ground);

  z-index: 60;
}

/*
 * The belt is skewed once, so the slices are plain boxes whose edges lean: a slanted clip on
 * each slice was a mask the compositor drew again every frame, for every slice on screen.
 */
.flick-reel__belt {
  position: absolute;
  inset: 0;
  transform: skewX(calc(-1 * var(--lean)));
  transform-origin: 0 100%;
}

.flick-reel__slice {
  --open: 0;

  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  overflow: hidden;
  background-color: var(--color-surface);
  will-change: transform;
  -webkit-user-drag: none;
}

/* The seam between two slices, drawn on the one to the right. */
.flick-reel__slice::after {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  z-index: 2;
  width: 1.5px;
  pointer-events: none;
  background-color: var(--color-hairline);
}

/* Upright again inside the leaning slice, and as wide as the slice and its cut together. */
.flick-reel__face {
  position: absolute;
  inset: 0 auto 0 0;
  width: calc(100% + var(--cut));
  transform: skewX(var(--lean));
  transform-origin: 0 100%;
}

.flick-reel__face::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background-color: var(--color-ground);
  opacity: calc((1 - var(--open)) * 0.42);
}

.flick-reel__slice:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: -4px;
}

/*
 * The pictures and grounds keep one size whatever the slice's width and sit on layers of their
 * own, so a slice growing moves them rather than having them painted again every frame.
 */
.flick-reel__art,
.flick-reel__plate {
  position: absolute;
  top: 0;
  left: 50%;
  width: 38rem;
  max-width: none;
  height: 100%;
  translate: -50% 0;
  pointer-events: none;
  will-change: transform;
}

.flick-reel__art {
  object-fit: cover;
}

.flick-reel__plate {
  background:
    radial-gradient(110% 90% at 0 0, color-mix(in oklab, var(--accent) 30%, transparent), transparent 70%),
    repeating-linear-gradient(-62deg, transparent 0 22px, color-mix(in oklab, var(--accent) 7%, transparent) 22px 24px),
    var(--color-pit);
}

.flick-reel__plate--plus {
  display: grid;
  place-items: center;
  color: color-mix(in oklab, var(--accent) 70%, var(--color-chalk));
}

.flick-reel__plate--plus svg {
  width: 6rem;
  height: 6rem;
  translate: 0 -2.5rem;
}

.flick-reel__initials {
  position: absolute;
  top: -1.2rem;
  right: -0.4rem;
  will-change: transform;
  font-family: var(--font-display);
  font-size: 9rem;
  line-height: 1;
  color: color-mix(in oklab, var(--accent) 22%, transparent);
}

.flick-reel__glow {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 38rem;
  height: 75%;
  pointer-events: none;
  will-change: transform;
  background: linear-gradient(to top, color-mix(in oklab, var(--color-ground) 94%, transparent), color-mix(in oklab, var(--color-ground) 60%, transparent) 40%, transparent);
}

/*
 * The icon's row is the anchor everything keeps to: it rises with the slice's opening in a
 * straight line, by the room the notes under it need once open, and nothing drawn in it changes
 * that room. The name wraps as the slice narrows without moving the icon or the tick above it.
 */
.flick-reel__body {
  --side: calc(var(--cut) + 1.1rem);
  --foot: 1.6rem;
  --more-room: 5.4rem;
  --more-gap: 0.8rem;
  --anchor: 2.8rem;

  position: absolute;
  right: 0;
  bottom: calc(var(--foot) + var(--open) * var(--more-room));
  left: 0;
  z-index: 3;
  padding: 0 var(--side);
}

/* Laid out at the width it is drawn at once scaled, so a long name wraps in a shut slice
   instead of running off it. A transform, so the shrinking is the compositor's work. */
.flick-reel__title {
  position: relative;
  display: block;
  width: calc(100% / (0.42 + 0.58 * var(--open)));
  scale: calc(0.42 + 0.58 * var(--open));
  transform-origin: left bottom;
}

.flick-reel__tick {
  position: absolute;
  bottom: calc(100% + 0.45rem);
  left: 0;
  width: 2.4rem;
  height: 3px;
  background-color: var(--accent);
}

/* As tall as the icon whatever the name does: a name longer than a line spills evenly above and
   below it, so the icon holds its place. */
.flick-reel__name {
  display: flex;
  gap: 0.6rem;
  align-items: center;
  height: var(--anchor);
  font-family: var(--font-display);
  font-size: 2.4rem;
  line-height: 1.05;
  text-transform: uppercase;
  color: var(--color-chalk);
}

/* Without an icon the name's first line is the anchor, and a second line goes below it. */
.flick-reel__title--bare .flick-reel__name {
  align-items: flex-start;
}

.flick-reel__name img {
  flex: none;
  width: var(--anchor);
  height: var(--anchor);
  object-fit: contain;
}

.flick-reel__words {
  min-width: 0;
}

.flick-reel__more {
  position: absolute;
  top: calc(100% + var(--more-gap));
  right: var(--side);
  left: var(--side);
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1.2rem;
  align-items: center;
  pointer-events: none;
  opacity: calc(var(--open) * 3 - 2);
  translate: 0 calc((1 - var(--open)) * 0.8rem);
}

.flick-reel__note {
  display: inline-flex;
  gap: 0.4rem;
  align-items: center;
  font-size: 1.05rem;
  color: var(--color-chalk);
}

.flick-reel__note svg {
  width: 17px;
  height: 17px;
  color: #5865f2;
}

.flick-reel__chip {
  padding: 0.2rem 0.55rem;
  font-size: 0.82rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-chalk);
  border: 1px solid color-mix(in oklab, var(--color-chalk) 28%, transparent);
}

.flick-reel__open {
  font-family: var(--font-display);
  font-size: 0.85rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--accent);
}

/*
 * Straight at both ends, so the row stops where the reel does. The cells are plain boxes and
 * the diagonals are drawn: a seam between two cells, and the lit cell's ground reaching from
 * one seam to the next. Clipped boxes laid edge to edge left hairline gaps wherever a width
 * fell on a fraction of a pixel.
 */
.flick-reel__rail {
  --cut: 10px;

  display: flex;
  height: var(--rail-height);
  overflow: hidden;
  /* Laid on the ground rather than over whatever is behind: under .island-dark the tokens are
     dark ones, and a light page showing through left its labels pale on pale. */
  background-color: color-mix(in oklab, var(--color-chalk) 5%, var(--color-ground));
}

.flick-reel__cell {
  position: relative;
  isolation: isolate;
  display: grid;
  flex: 1 1 0;
  place-items: center;
  min-width: 0;
  padding: 0 0.6rem;
  cursor: pointer;
  background: none;
  border: 0;
}

/* The lit or hovered cell's ground, from the seam on its left to the seam on its right. */
.flick-reel__cell::before {
  content: "";
  position: absolute;
  inset: 0 calc(var(--cut) / -2);
  z-index: -1;
  background-color: transparent;
  clip-path: polygon(var(--cut) 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
  transition: background-color 220ms ease;
}

/* The seam on a cell's left, leaning the way the slices above it lean. */
.flick-reel__cell + .flick-reel__cell::after {
  content: "";
  position: absolute;
  inset: 0 auto 0 calc(var(--cut) / -2);
  width: calc(var(--cut) + 1.5px);
  pointer-events: none;
  background-color: var(--color-hairline);
  clip-path: polygon(var(--cut) 0, calc(var(--cut) + 1.5px) 0, 1.5px 100%, 0 100%);
}

.flick-reel__cell:hover::before {
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
}

.flick-reel__cell:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: -2px;
}

.flick-reel__cell img {
  width: 1.7rem;
  height: 1.7rem;
  object-fit: contain;
  opacity: 0.5;
  filter: grayscale(1) brightness(1.35);
  transition: filter 300ms ease, opacity 300ms ease;
}

.flick-reel__cell-plus {
  width: 1.5rem;
  height: 1.5rem;
  color: var(--color-ash);
}

.flick-reel__cell span {
  max-width: 100%;
  overflow: hidden;
  font-family: var(--font-display);
  font-size: 0.72rem;
  letter-spacing: 0.04em;
  text-overflow: ellipsis;
  text-transform: uppercase;
  white-space: nowrap;
  color: var(--color-ash);
}

.flick-reel__cell--on::before,
.flick-reel__cell--on:hover::before {
  background-color: color-mix(in oklab, var(--accent) 24%, transparent);
}

.flick-reel__cell--on img {
  opacity: 1;
  filter: none;
}

.flick-reel__cell--on span {
  color: var(--color-chalk);
}

.flick-reel--narrow .flick-reel__art {
  width: 22rem;
}

.flick-reel--narrow .flick-reel__body {
  --side: calc(var(--cut) + 0.6rem);
  --foot: 1.1rem;
  --more-room: 6.6rem;
  --more-gap: 1.2rem;
  --anchor: 2rem;
}

.flick-reel--narrow .flick-reel__name {
  font-size: 1.6rem;
}

.flick-reel--narrow .flick-reel__more {
  gap: 0.35rem 0.8rem;
}

.flick-reel--narrow .flick-reel__open {
  display: none;
}

.flick-reel--narrow .flick-reel__rail {
  --cut: 6px;
  --rail-height: 2.6rem;
}

.flick-reel--narrow .flick-reel__cell {
  padding: 0 0.1rem;
}

.flick-reel--narrow .flick-reel__cell img {
  width: 1.1rem;
  height: 1.1rem;
}

.flick-reel--narrow .flick-reel__cell span {
  font-size: 0.55rem;
}

@media (prefers-reduced-motion: reduce) {
  .flick-reel__cell::before,
  .flick-reel__cell img {
    transition: none;
  }
}
</style>
