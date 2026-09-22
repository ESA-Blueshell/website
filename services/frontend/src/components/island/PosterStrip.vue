<script lang="ts">
/** One poster on the strip: its own art, the line under it, and where it leads. */
export interface PosterItem {
  id: number | string
  title: string
  /** A line under the title: when it ran, or who it was for. */
  meta: string
  /** What the art cannot say, cut short by the strip rather than by the caller. */
  said?: string
  banner: string
  /** The widths that image is stored at, ready for a `srcset`, where it has several. */
  srcset?: string
  width?: number
  height?: number
  href?: string
}
</script>

<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
import {useMotionAllowed} from "./useMotionAllowed"

/**
 * A row of event posters, travelled the way the timeline's strip is travelled.
 *
 * The art already carries the name, the time and the place, so the strip adds only what the art
 * cannot: the line under it and the way through. It scrolls rather than paging, and says when it
 * is near the end so a caller can fetch the next few before they are asked for.
 */
defineOptions({name: "PosterStrip"})

const {
  items,
  testidPrefix,
  perView = 4,
  ahead = 2,
  panBackLabel = "Earlier events",
  panOnLabel = "Later events",
} = defineProps<{
  items: PosterItem[]
  testidPrefix: string
  /** How many posters are read at once at desktop width. */
  perView?: number
  /** How many past those are drawn, which is also when `needs-more` is emitted. */
  ahead?: number
  panBackLabel?: string
  panOnLabel?: string
}>()

const emit = defineEmits<{"needs-more": []}>()

/** How fast the strip travels while the pointer rests on its side, in px per ms. */
const PAN_RATE = 0.55
/** How much of the strip a click moves, as a share of what is on screen. */
const PAN_STEP = 0.8
/** How wide the side a pointer travels from is, and the share of a narrow strip it may take. */
const PAN_ZONE = 84
const PAN_ZONE_SHARE = 0.18

const motion = useMotionAllowed()

const strip = ref<HTMLElement | null>(null)
const scroller = ref<HTMLElement | null>(null)

/** The poster under the pointer: the strip lights it and quietens the rest, as the strip does. */
const lit = ref<number | string | null>(null)

const canPanBack = ref(false)
const canPanOn = ref(false)

const measureScroll = () => {
  const box = scroller.value
  if (!box) return
  const furthest = box.scrollWidth - box.clientWidth
  canPanBack.value = box.scrollLeft > 1
  canPanOn.value = box.scrollLeft < furthest - 1
  // Near the end is where the next few are worth asking for, rather than at it.
  if (furthest > 0 && box.scrollLeft > furthest - box.clientWidth / perView * ahead) {
    emit("needs-more")
  }
}

watch(() => items.length, () => {
  requestAnimationFrame(measureScroll)
})

let panning: number | null = null
let panDirection = 0
let panAt = 0
/** Which way the strip is travelling, for the side that is doing it to show that it is. */
const travelling = ref(0)

const pan = (direction: number) => {
  panDirection = direction
  travelling.value = direction
  if (panning != null) return
  panAt = performance.now()
  const step = (now: number) => {
    const box = scroller.value
    if (!box || panDirection === 0) {
      panning = null
      return
    }
    box.scrollLeft += panDirection * PAN_RATE * (now - panAt)
    panAt = now
    measureScroll()
    panning = (panDirection < 0 ? canPanBack.value : canPanOn.value)
      ? requestAnimationFrame(step)
      : null
  }
  panning = requestAnimationFrame(step)
}

const rest = () => {
  panDirection = 0
  travelling.value = 0
  if (panning != null) cancelAnimationFrame(panning)
  panning = null
}

/* A touch screen has no pointer that rests, so the first tap near an edge must not set the
   strip moving under it. */
const canHover = () => typeof window === "undefined"
  || typeof window.matchMedia !== "function"
  || window.matchMedia("(hover: hover)").matches

const aim = (event: MouseEvent) => {
  const box = strip.value?.getBoundingClientRect()
  if (!box || !canHover()) return
  const zone = Math.min(PAN_ZONE, box.width * PAN_ZONE_SHARE)
  const from = event.clientX - box.left
  if (from <= zone && canPanBack.value) pan(-1)
  else if (from >= box.width - zone && canPanOn.value) pan(1)
  else rest()
}

/** A click moves a screenful, which is the gesture for somebody who is not hovering at all. */
const panBy = (direction: number) => {
  // The chevrons are drawn only where the strip is, so it is there to be moved.
  const box = scroller.value as HTMLElement
  box.scrollBy({
    left: direction * box.clientWidth * PAN_STEP,
    behavior: motion.decorative.value ? "smooth" : "auto",
  })
}

const width = computed<string>(() => `calc((100% - ${(perView - 1) * 2}px) / ${perView})`)

onMounted(() => requestAnimationFrame(measureScroll))
onBeforeUnmount(rest)
</script>

<template>
  <div
    ref="strip"
    class="posters"
    :class="{'posters--quiet': lit !== null}"
    :data-testid="testidPrefix"
    @mouseleave="lit = null; rest()"
    @mousemove="aim"
  >
    <div
      ref="scroller"
      class="posters__scroll"
      @scroll="measureScroll"
    >
      <article
        v-for="one in items"
        :key="one.id"
        class="posters__poster"
        :class="{'posters__poster--lit': one.id === lit}"
        :data-testid="`${testidPrefix}-${one.id}`"
        :style="{width}"
        @focusin="lit = one.id"
        @mouseenter="lit = one.id"
      >
        <component
          :is="one.href ? 'a' : 'div'"
          class="posters__art"
          :href="one.href"
        >
          <img
            alt=""
            class="posters__img"
            :height="one.height"
            :sizes="`${Math.round(100 / perView)}vw`"
            :src="one.banner"
            :srcset="one.srcset"
            :width="one.width"
          >
        </component>

        <div class="posters__foot">
          <h3 class="posters__title">
            {{ one.title }}
          </h3>
          <p class="posters__meta">
            {{ one.meta }}
          </p>
          <p
            v-if="one.said"
            class="posters__said"
          >
            {{ one.said }}
          </p>
        </div>
      </article>
    </div>

    <!--
      Where the strip holds more posters than fit, the way to the rest of them. Resting the
      pointer on one travels that way; a click moves a screenful, which is what somebody
      arriving by keyboard gets.
    -->
    <button
      v-if="canPanBack"
      :aria-label="panBackLabel"
      class="posters__pan posters__pan--back"
      :class="{'posters__pan--live': travelling === -1}"
      :data-testid="`${testidPrefix}-pan-back`"
      type="button"
      @click="panBy(-1)"
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
        <path d="M14.5 5.5 8 12l6.5 6.5" />
      </svg>
    </button>

    <button
      v-if="canPanOn"
      :aria-label="panOnLabel"
      class="posters__pan posters__pan--on"
      :class="{'posters__pan--live': travelling === 1}"
      :data-testid="`${testidPrefix}-pan-on`"
      type="button"
      @click="panBy(1)"
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
        <path d="M9.5 5.5 16 12l-6.5 6.5" />
      </svg>
    </button>
  </div>
</template>

<style scoped>
.posters {
  position: relative;
  width: 100%;
}

/* Scrolled rather than paged, and snapped so a poster never rests half off the edge. */
.posters__scroll {
  display: flex;
  gap: 2px;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  scrollbar-width: none;
}

.posters__scroll::-webkit-scrollbar {
  display: none;
}

.posters__poster {
  display: flex;
  flex: none;
  flex-direction: column;
  scroll-snap-align: start;
}

.posters__art {
  position: relative;
  display: block;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  background-color: var(--color-pit);
}

.posters__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: scale 420ms var(--ease-out-quint), opacity 240ms ease;
}

/* One poster lit, the rest quietened: the strip's own way of answering the pointer. */
.posters--quiet .posters__img {
  opacity: 0.55;
}

.posters__poster--lit .posters__img {
  opacity: 1;
  scale: 1.04;
}

.posters__foot {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.85rem 1rem 1.1rem;
  background-color: var(--band-ground);
}

.posters__title {
  font-family: var(--font-display);
  font-size: 1rem;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.posters__meta {
  font-family: var(--font-bitmap);
  font-size: 0.68rem;
  letter-spacing: 0.06em;
  color: var(--color-ash);
}

.posters__said {
  display: -webkit-box;
  overflow: hidden;
  font-size: 0.85rem;
  line-height: 1.45;
  color: var(--color-ash);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.posters__pan {
  position: absolute;
  top: 0;
  z-index: 3;
  display: grid;
  place-items: center;
  width: 44px;
  height: 100%;
  padding: 0;
  color: var(--color-chalk);
  cursor: pointer;
  background: none;
  border: 0;
}

.posters__pan::before {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  pointer-events: none;
  opacity: 0.72;
  transition: opacity 220ms ease;
}

.posters__pan--live::before,
.posters__pan:hover::before,
.posters__pan:focus-visible::before {
  opacity: 1;
}

.posters__pan svg {
  position: relative;
  width: 26px;
  height: 26px;
  opacity: 0.78;
  transition: scale 220ms ease, opacity 220ms ease;
}

.posters__pan--live svg,
.posters__pan:hover svg,
.posters__pan:focus-visible svg {
  opacity: 1;
  scale: 1.24;
}

.posters__pan--back {
  left: 0;
}

.posters__pan--back::before {
  left: 0;
  right: -40px;
  background: linear-gradient(to right, color-mix(in oklab, var(--color-ground) 82%, transparent), transparent);
}

.posters__pan--on {
  right: 0;
}

.posters__pan--on::before {
  left: -40px;
  right: 0;
  background: linear-gradient(to left, color-mix(in oklab, var(--color-ground) 82%, transparent), transparent);
}

@media (prefers-reduced-motion: reduce) {
  .posters__img {
    transition: none;
  }
}
</style>
