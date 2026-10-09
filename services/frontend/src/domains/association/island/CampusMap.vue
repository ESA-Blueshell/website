<script lang="ts" setup>
import {computed, ref, useTemplateRef, watch} from "vue"
import {useMediaQuery} from "@vueuse/core"
import CutButton from "@/components/island/CutButton.vue"
import IconButton from "@/components/island/IconButton.vue"
import {
  CAMPUS,
  HOME,
  LOUNGE_IN_MAPS_APP,
  LOUNGE_ROUTE,
  MAX_ZOOM,
  MIN_ZOOM,
  type MapView,
  panned,
  PHONE_HOME,
  type Place,
  spotOf,
  zoomedAt,
} from "../campusMap"
import campusDark from "@/assets/contact/campus-dark.svg?url"
import campusLight from "@/assets/contact/campus-light.svg?url"
import {PHONE} from "@/styles/breakpoints"

/**
 * The campus around the Esports Lounge Twente, drawn from OpenStreetMap data the site serves
 * itself: dragged to look around, zoomed by its buttons or a double press. The pins sit on the
 * drawing and keep their size while it scales.
 */
defineOptions({name: "CampusMap"})

const ZOOM_STEP = 1.4
/** How far a pixel of scroll zooms: a mouse notch of about 100 is one button press. */
const WHEEL_PACE = Math.log(ZOOM_STEP) / 100
/** A line of scroll, as Firefox counts a mouse wheel, in pixels. */
const LINE = 16

const plate = useTemplateRef<HTMLElement>("plate")
const phone = useMediaQuery(PHONE)
const home = computed(() => (phone.value ? PHONE_HOME : HOME))
const view = ref<MapView>(home.value)
watch(home, next => {
  view.value = next
})
const moved = ref(false)
const held = ref<{x: number, y: number, from: MapView} | null>(null)
/** While a scroll is zooming, each step lands at once rather than easing after the last. */
const scrolling = ref(false)
let scrolled: ReturnType<typeof setTimeout> | undefined

const stage = computed(() => `translate(-50%, -50%) translate(${view.value.x}px, ${view.value.y}px) scale(${view.value.zoom})`)
const steady = computed(() => `scale(${Number((1 / view.value.zoom).toFixed(4))})`)

const at = (place: Place) => {
  const {left, top} = spotOf(place)
  return {left: `${left}%`, top: `${top}%`}
}

/** Only the plate's own handlers ask, so it is always mounted by then. */
const box = () => plate.value!.getBoundingClientRect()

/** Where the pointer is, in pixels from the plate's middle. */
const pointAt = (event: MouseEvent) => {
  const {left, top, width, height} = box()
  return {x: event.clientX - left - width / 2, y: event.clientY - top - height / 2}
}

const zoom = (factor: number, at = {x: 0, y: 0}) => {
  view.value = zoomedAt(view.value, factor, at, box())
}

const scroll = (event: WheelEvent) => {
  const pixels = event.deltaY * (event.deltaMode === WheelEvent.DOM_DELTA_LINE ? LINE : 1)
  zoom(Math.exp(-pixels * WHEEL_PACE), pointAt(event))
  scrolling.value = true
  clearTimeout(scrolled)
  scrolled = setTimeout(() => {
    scrolling.value = false
  }, 180)
}

const grab = (event: PointerEvent) => {
  if (event.button !== 0 || (event.target as Element).closest("button, a")) return
  held.value = {x: event.clientX, y: event.clientY, from: view.value}
  plate.value?.setPointerCapture?.(event.pointerId)
}

const drag = (event: PointerEvent) => {
  if (!held.value) return
  const by = {x: event.clientX - held.value.x, y: event.clientY - held.value.y}
  view.value = panned(held.value.from, by, box())
  moved.value = true
}

const release = () => {
  held.value = null
}
</script>

<template>
  <div class="campus-map">
    <div
      ref="plate"
      class="campus-map__plate"
      :class="{'campus-map__plate--still': held || scrolling}"
      data-testid="campus-map"
      @dblclick="zoom(ZOOM_STEP, pointAt($event))"
      @pointercancel="release"
      @pointerdown="grab"
      @pointermove="drag"
      @pointerup="release"
      @wheel.prevent="scroll"
    >
      <div
        class="campus-map__stage"
        :style="{transform: stage}"
      >
        <img
          alt="Map of the University of Twente campus around the Bastille"
          class="campus-map__art campus-map__art--dark"
          draggable="false"
          loading="lazy"
          :src="campusDark"
        >
        <img
          alt="Map of the University of Twente campus around the Bastille"
          class="campus-map__art campus-map__art--light"
          draggable="false"
          loading="lazy"
          :src="campusLight"
        >

        <span
          class="campus-map__spot"
          :style="at(CAMPUS.busStop)"
        >
          <span
            class="campus-map__steady"
            :style="{transform: steady}"
          >
            <span
              class="campus-map__bus"
              title="Bus stop UT/Bastille"
            >
              <svg
                aria-hidden="true"
                fill="none"
                stroke="currentColor"
                stroke-linejoin="round"
                stroke-width="2"
                viewBox="0 0 24 24"
              ><rect
                height="15"
                rx="2"
                width="14"
                x="5"
                y="3"
              /><path d="M5 11h14M8 21v-3M16 21v-3" /></svg>
            </span>
          </span>
        </span>

        <span
          class="campus-map__spot"
          :style="at(CAMPUS.lounge)"
        >
          <span
            class="campus-map__steady"
            :style="{transform: steady}"
          >
            <span class="campus-map__pulse" />
            <span class="campus-map__dot" />
            <span
              class="campus-map__card"
              data-testid="campus-map-lounge"
            >
              <b>Esports Lounge Twente</b>
              <small>Bastille, University of Twente</small>
            </span>
          </span>
        </span>
      </div>

      <div class="campus-map__tools">
        <icon-button
          :disabled="view.zoom >= MAX_ZOOM"
          label="Zoom in"
          @click="zoom(ZOOM_STEP)"
        >
          <svg
            fill="none"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-width="2"
            viewBox="0 0 24 24"
          ><path d="M12 5v14M5 12h14" /></svg>
        </icon-button>
        <icon-button
          :disabled="view.zoom <= MIN_ZOOM"
          label="Zoom out"
          @click="zoom(1 / ZOOM_STEP)"
        >
          <svg
            fill="none"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-width="2"
            viewBox="0 0 24 24"
          ><path d="M5 12h14" /></svg>
        </icon-button>
        <icon-button
          label="Back to the Lounge"
          @click="view = home"
        >
          <svg
            fill="none"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-width="2"
            viewBox="0 0 24 24"
          ><circle
            cx="12"
            cy="12"
            r="3"
          /><path d="M12 2v4M12 18v4M2 12h4M18 12h4" /></svg>
        </icon-button>
      </div>

      <p
        class="campus-map__hint"
        :class="{'campus-map__hint--gone': moved}"
      >
        Drag to move, scroll to zoom
      </p>
      <p class="campus-map__credit">
        © <a
          href="https://www.openstreetmap.org/copyright"
          rel="noopener"
          target="_blank"
        >OpenStreetMap</a> contributors
      </p>
    </div>

    <div class="campus-map__ways">
      <cut-button
        class="campus-map__way"
        :href="LOUNGE_IN_MAPS_APP"
        small
        testid="campus-map-app"
        tone="solid"
      >
        Open in your maps app
      </cut-button>
      <cut-button
        away
        class="campus-map__way"
        :href="LOUNGE_ROUTE"
        small
        testid="campus-map-route"
      >
        Route on OpenStreetMap
      </cut-button>
    </div>
  </div>
</template>

<style scoped>
.campus-map {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

/* The drawing's own ground shows while it loads and past its edges at the farthest zoom. */
.campus-map__plate {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  touch-action: none;
  cursor: grab;
  user-select: none;
  background-color: #1d1f22;
  outline: 1px solid var(--color-hairline);
}

.campus-map__plate:active {
  cursor: grabbing;
}

/* Twice the plate across at zoom 1, centred on the Lounge: campusMap.ts measures on that. */
.campus-map__stage {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 200%;
  aspect-ratio: 1;
  transform-origin: 50% 50%;
  transition: transform 560ms var(--ease-out-quint);
}

/* Under a hand or a scroll the drawing follows at once; it eases only for the buttons. */
.campus-map__plate--still .campus-map__stage,
.campus-map__plate--still .campus-map__steady {
  transition: none;
}

.campus-map__art {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.campus-map__art--light {
  display: none;
}

:where([data-theme="light"]) .campus-map__plate {
  background-color: #e8edf2;
}

:where([data-theme="light"]) .campus-map__art--dark {
  display: none;
}

:where([data-theme="light"]) .campus-map__art--light {
  display: block;
}

.campus-map__spot {
  position: absolute;
  width: 0;
  height: 0;
}

.campus-map__steady {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: 0 0;
  transition: transform 560ms var(--ease-out-quint);
}

.campus-map__dot,
.campus-map__pulse {
  position: absolute;
  top: -9px;
  left: -9px;
  width: 18px;
  height: 18px;
  border-radius: 999px;
  background-color: var(--color-acid);
}

.campus-map__dot {
  box-shadow: 0 0 0 3px var(--color-void);
}

.campus-map__pulse {
  animation: campus-map-ping 2.2s var(--ease-out-quint) infinite;
}

@keyframes campus-map-ping {
  from {
    transform: scale(1);
    opacity: 0.55;
  }

  to {
    transform: scale(3.6);
    opacity: 0;
  }
}

/* The house cut, in the association's blue, so the Lounge reads before any street name. */
.campus-map__card {
  position: absolute;
  top: -30px;
  left: 18px;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  padding: 0.55rem 1.1rem 0.6rem 1.25rem;
  white-space: nowrap;
  clip-path: polygon(0.6rem 0, 100% 0, calc(100% - 0.6rem) 100%, 0 100%);
  background-color: var(--color-brand);
  color: #1c1c1c;
}

.campus-map__card b {
  font-family: var(--font-display);
  font-size: 0.85rem;
  font-weight: 400;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.campus-map__card small {
  font-size: 0.78rem;
  font-weight: 600;
}

.campus-map__bus {
  position: absolute;
  top: -12px;
  left: -12px;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background-color: var(--color-chalk);
  color: var(--color-ground);
}

.campus-map__bus svg {
  width: 15px;
  height: 15px;
}

.campus-map__tools {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  display: flex;
  flex-direction: column;
  background-color: color-mix(in oklab, var(--color-pit) 88%, transparent);
  backdrop-filter: blur(10px);
  outline: 1px solid var(--color-hairline);
}

.campus-map__tools > * {
  width: 2.75rem;
  height: 2.75rem;
  color: var(--color-chalk);
}

.campus-map__hint {
  position: absolute;
  bottom: 0.6rem;
  left: 0.75rem;
  font-size: 0.72rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-ash);
  pointer-events: none;
  transition: opacity 400ms var(--ease-out-quint);
}

.campus-map__hint--gone {
  opacity: 0;
}

.campus-map__credit {
  position: absolute;
  right: 0;
  bottom: 0;
  padding: 0.25rem 0.55rem;
  font-size: 0.72rem;
  background-color: color-mix(in oklab, var(--color-pit) 88%, transparent);
  color: var(--color-ash);
}

.campus-map__credit a {
  text-decoration: underline;
}

.campus-map__ways {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.6rem;
}

/* On a phone the two ways stack, each the width of the map, so neither is a target to hunt for. */
@media (--phone) {
  .campus-map__plate {
    aspect-ratio: 1;
  }

  .campus-map__ways {
    flex-direction: column;
    align-items: stretch;
  }

  .campus-map__way {
    justify-content: center;
    padding-block: 0.8rem;
  }

  /* Above the pin rather than beside it, where a narrow plate has room for it and the bus stop
     below stays in sight. */
  .campus-map__card {
    top: auto;
    bottom: 18px;
    left: 0;
    translate: -50% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .campus-map__pulse {
    display: none;
  }

  .campus-map__stage,
  .campus-map__steady {
    transition: none;
  }
}
</style>
