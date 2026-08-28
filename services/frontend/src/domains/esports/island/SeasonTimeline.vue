<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, useId, watch} from "vue"
import {litAt, seasonStrip, STRIP} from "./seasonAxis"
import {useMotionAllowed} from "./useMotionAllowed"
import type {Season} from "../adapters/esports"

defineOptions({name: "SeasonTimeline"})

const props = withDefaults(defineProps<{
  seasons: Season[]
  selectedId: number | null
  accent: string
  /** Whether to offer the edit affordance. Decided by the page, which knows who is reading. */
  mayEdit?: boolean
}>(), {mayEdit: false})

const emit = defineEmits<{
  (event: "select", id: number): void
  (event: "edit", season: Season): void
  (event: "add"): void
}>()

const motion = useMotionAllowed()

/** How fast the strip travels while an arrow is held under the pointer, in px per ms. */
const PAN_RATE = 0.55
/** How much of the strip a click on an arrow moves, as a share of what is in view. */
const PAN_STEP = 0.8

const strip = ref<HTMLElement | null>(null)
const scroller = ref<HTMLElement | null>(null)
const width = ref(0)
const hovered = ref<number | null>(null)
/**
 * The season whose affordance was last taken up.
 *
 * It stays visible until the pointer goes to another season. Opening the dialog moves focus
 * into it, which means the affordance is neither hovered nor focused while it is open — and
 * a hidden element cannot be given focus back when it closes. Pinning it is what gives the
 * dialog somewhere to return to, and does not depend on anything happening in time.
 */
const pinned = ref<number | null>(null)

/** Ids for the mask that dissolves the ends of the line, which the two layers share. */
const uid = useId()
const fadeId = `${uid}-fade`
const maskId = `${uid}-ends`

let observer: ResizeObserver | null = null
onMounted(() => {
  measureScroll()
  if (!strip.value || typeof ResizeObserver === "undefined") return
  observer = new ResizeObserver(entries => {
    width.value = entries[0]?.contentRect.width ?? 0
  })
  observer.observe(strip.value)
  width.value = strip.value.clientWidth
})
onBeforeUnmount(() => {
  observer?.disconnect()
  rest()
})

/**
 * The block offering another season is a band like the rest, so it takes a share of the strip
 * rather than floating over the end of it.
 */
const trailing = computed(() => (props.mayEdit ? 1 : 0))

/** Where everything on the strip sits, in pixels: the bands, the nodes and the line. */
const axis = computed(() => seasonStrip(props.seasons, {width: width.value, trailing: trailing.value}))
const bands = computed(() => axis.value.bands)
const nodes = computed(() => axis.value.nodes)
const track = computed(() => axis.value.track)

/**
 * How far the line is lit: to the pointer, or to the season on show at rest.
 *
 * A share of the track, because that is the box the layer drawing it occupies — so the lit
 * stretch ends on the middle of the node however many seasons there are and whether or not
 * the strip reserves a band for adding one.
 */
const litFraction = computed<number>(() => {
  if (track.value === 0) return 0
  const at = litAt(nodes.value, hovered.value ?? props.selectedId)
  return Math.min(Math.max(at / track.value, 0), 1)
})

/**
 * The season on show was chosen here, on a node in front of the visitor.
 *
 * The strip opens on the season being shown, which is right when that season arrives from
 * somewhere the visitor cannot see — a shared link, the back button, a season just written
 * down. It is wrong after a click: the node they aimed at would slide out from under the
 * pointer, and whichever band slid into its place would light the line instead.
 */
const chosenHere = ref(false)

const choose = (id: number) => {
  if (id !== props.selectedId) chosenHere.value = true
  emit("select", id)
}

/** A strip wider than its window opens on the season being shown, not at the far past. */
watch([() => props.selectedId, track, width], ([id], [before]) => {
  if (id !== before && chosenHere.value) {
    chosenHere.value = false
    return
  }
  const node = nodes.value.find(n => n.id === props.selectedId)
  const box = scroller.value
  if (!node || !box || box.scrollWidth <= box.clientWidth) return
  box.scrollTo({left: node.x - box.clientWidth / 2, behavior: "auto"})
}, {flush: "post"})

/**
 * Whether there is anything further to see each way.
 *
 * The scrollbar is hidden and the ends fade rather than cutting, so without this the seasons
 * off the end of the window are simply not found.
 */
const canPanBack = ref(false)
const canPanOn = ref(false)

const measureScroll = () => {
  const box = scroller.value
  if (!box) return
  const furthest = box.scrollWidth - box.clientWidth
  canPanBack.value = box.scrollLeft > 1
  canPanOn.value = box.scrollLeft < furthest - 1
}

watch([track, width], measureScroll, {flush: "post"})

let panning: number | null = null
let panDirection = 0
let panAt = 0

/** Travels while the pointer rests on an arrow, and stops where there is no further to go. */
const pan = (direction: number) => {
  panDirection = direction
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
  if (panning != null) cancelAnimationFrame(panning)
  panning = null
}

/** A click moves a screenful, which is the gesture for somebody who is not hovering at all. */
const panBy = (direction: number) => {
  const box = scroller.value
  if (!box) return
  box.scrollBy({
    left: direction * box.clientWidth * PAN_STEP,
    behavior: motion.decorative.value ? "smooth" : "auto",
  })
}

const yOf = (id: number) => nodes.value.find(n => n.id === id)?.y ?? STRIP.height / 2

const enter = (id: number) => {
  hovered.value = id
  if (id !== pinned.value) pinned.value = null
}

const step = (from: number, by: number) => {
  const next = bands.value[from + by]
  if (next) emit("select", next.season.id)
}
</script>

<template>
  <div
    ref="strip"
    class="season-strip"
    data-testid="esports-season-timeline"
    :style="{
      '--accent': accent,
      '--lit': `${litFraction * 100}%`,
      '--h': `${STRIP.height}px`,
      '--track': `${track}px`,
    }"
    @mouseleave="hovered = null"
  >
    <div
      ref="scroller"
      class="season-strip__scroll"
      @scroll="measureScroll"
    >
      <div class="season-strip__track">
        <!--
          One band per season, tiled exactly so a node can sit in the middle of its own
          season and the division between two bands falls halfway between their nodes — which
          is where the teams below divide too. Hovering highlights a band and lights the line
          as far as its node; changing season takes a click, so a pointer crossing the strip
          changes nothing.
        -->
        <div class="season-strip__bands">
          <div
            v-for="(band, index) in bands"
            :key="band.season.id"
            class="season-slot"
            :class="{'season-slot--editing': band.season.id === pinned}"
            @mouseenter="enter(band.season.id)"
          >
            <button
              class="season-band"
              :class="{
                'season-band--on': band.season.id === selectedId,
                'season-band--lit': band.season.id === hovered,
                'season-band--last': index === bands.length - 1 && !mayEdit,
              }"
              :aria-current="band.season.id === selectedId ? 'true' : undefined"
              :data-testid="`esports-season-node-${band.season.id}`"
              type="button"
              @click="choose(band.season.id)"
              @focus="hovered = band.season.id"
              @keydown.left.prevent="step(index, -1)"
              @keydown.right.prevent="step(index, 1)"
            >
              <span class="sr-only">{{ band.season.name }}</span>
              <span
                aria-hidden="true"
                class="season-band__wash"
              />
              <span
                aria-hidden="true"
                class="season-band__label season-band__label--half"
                :style="{top: band.high ? `${yOf(band.season.id) + 18}px` : `${yOf(band.season.id) - 34}px`}"
              >{{ band.half }}</span>
              <span
                aria-hidden="true"
                class="season-band__label season-band__label--year"
                :style="{top: band.high ? `${yOf(band.season.id) + 32}px` : `${yOf(band.season.id) - 20}px`}"
              >{{ band.year }}</span>
            </button>

            <!--
              Offered only to somebody who may take it up. Where there is a pointer it belongs
              to the season being pointed at; where there is not, there is nothing to hover
              with, so it simply stands.
            -->
            <button
              v-if="mayEdit"
              :aria-label="`Edit ${band.season.name}`"
              class="season-slot__edit"
              :data-testid="`esports-season-edit-${band.season.id}`"
              type="button"
              @click="pinned = band.season.id; emit('edit', band.season)"
            >
              <svg
                aria-hidden="true"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                viewBox="0 0 24 24"
              >
                <path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4Z" />
              </svg>
            </button>
          </div>

          <!--
            Seasons are added twice a year and always at the end, which is where their absence
            is noticed — so the offer is a band at the end of the line rather than a control
            floating over it. It stands rather than waiting to be hovered: there is no season
            under the pointer for it to belong to.
          -->
          <div
            v-if="mayEdit"
            class="season-slot season-slot--add"
          >
            <button
              aria-label="Add a season"
              class="season-band season-band--add"
              data-testid="esports-season-add"
              type="button"
              @click="emit('add')"
            >
              <span
                aria-hidden="true"
                class="season-band__wash"
              />
              <span
                aria-hidden="true"
                class="season-band__plus island-plus"
              >
                <svg
                  class="island-plus__edge"
                  fill="none"
                  viewBox="0 0 100 100"
                >
                  <path d="M38 2 H62 V38 H98 V62 H62 V98 H38 V62 H2 V38 H38 Z" />
                </svg>
              </span>
            </button>
          </div>
        </div>

        <!--
          The line, in two layers over the same path: the rule it always is, and the lit
          stretch as far as the season being read. Two svgs rather than two groups in one,
          because the lit one is revealed by clipping its own box — and a box is a thing a
          group does not have, which is what left the lit stretch measured against the
          bounding box of the path and stopping short of the season it was reporting.
        -->
        <template v-if="axis.path">
          <svg
            aria-hidden="true"
            class="season-strip__line"
            preserveAspectRatio="none"
            :viewBox="`0 0 ${Math.max(track, 1)} ${STRIP.height}`"
          >
            <defs>
              <!--
                The line dissolves as it arrives at its end rather than stopping at one. Where
                the block that adds a season bounds it, its end is in view, and a stub with a
                cap on it reads as a drawing laid on the strip rather than as the seasons
                carrying on. Nothing is left over to fall on a block that is not a season.
              -->
              <linearGradient
                :id="fadeId"
                gradientUnits="userSpaceOnUse"
                :x1="axis.to - STRIP.fade"
                :x2="axis.to"
                y1="0"
                y2="0"
              >
                <stop
                  offset="0"
                  stop-color="#fff"
                />
                <stop
                  offset="1"
                  stop-color="#fff"
                  stop-opacity="0"
                />
              </linearGradient>
              <mask
                :id="maskId"
                :height="STRIP.height"
                maskUnits="userSpaceOnUse"
                :width="axis.to - axis.from"
                :x="axis.from"
                y="0"
              >
                <rect
                  :fill="`url(#${fadeId})`"
                  :height="STRIP.height"
                  :width="axis.to - axis.from"
                  :x="axis.from"
                  y="0"
                />
              </mask>
            </defs>
            <path
              class="season-strip__rule"
              :d="axis.path"
              fill="none"
              :mask="`url(#${maskId})`"
              vector-effect="non-scaling-stroke"
            />
          </svg>

          <svg
            aria-hidden="true"
            class="season-strip__line season-strip__lit"
            preserveAspectRatio="none"
            :viewBox="`0 0 ${Math.max(track, 1)} ${STRIP.height}`"
          >
            <path
              :d="axis.path"
              fill="none"
              :mask="`url(#${maskId})`"
              vector-effect="non-scaling-stroke"
            />
          </svg>
        </template>

        <!-- Drawn as elements rather than inside the svg: the line is stretched to the track
             by its viewBox, and a circle in that space would stretch with it. -->
        <span
          v-for="node in nodes"
          :key="node.id"
          aria-hidden="true"
          class="season-strip__dot"
          :class="{
            'season-strip__dot--on': node.id === selectedId,
            'season-strip__dot--lit': node.id === hovered,
          }"
          :style="{left: `${node.x}px`, top: `${node.y}px`}"
        />
      </div>
    </div>

    <!--
      Where the strip holds more seasons than fit, the way to the rest of them. Resting the
      pointer on one travels that way; a click moves a screenful, which is what somebody
      arriving by keyboard gets. Neither changes the season being read.
    -->
    <button
      v-if="canPanBack"
      aria-label="Show earlier seasons"
      class="season-strip__pan season-strip__pan--back"
      data-testid="esports-season-pan-back"
      type="button"
      @blur="rest"
      @click="panBy(-1)"
      @mouseenter="pan(-1)"
      @mouseleave="rest"
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
      aria-label="Show later seasons"
      class="season-strip__pan season-strip__pan--on"
      data-testid="esports-season-pan-on"
      type="button"
      @blur="rest"
      @click="panBy(1)"
      @mouseenter="pan(1)"
      @mouseleave="rest"
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
.season-strip {
  --cut: 26px;

  position: relative;
  width: 100%;
  user-select: none;
}

/* The ends fade rather than stopping at a line: the strip belongs to the page it sits on. */
.season-strip__scroll {
  mask-image: linear-gradient(to right, transparent 0, #000 5%, #000 95%, transparent 100%);
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
}

.season-strip__scroll::-webkit-scrollbar {
  display: none;
}

.season-strip__track {
  position: relative;
  width: var(--track);
  min-width: 100%;
}

.season-strip__bands {
  display: flex;
  height: var(--h);
  width: 100%;
  overflow: hidden;
}

/*
 * The bands tile exactly, each taking the same share of the track, because the nodes are
 * placed by arithmetic on that same share. Overlapping them to interlock a diagonal clip
 * moved every band's centre off the node it belongs to, and the labels drifted away from
 * their own dots. The division is drawn instead: a slanted rule on the trailing edge, which
 * reads as the same cut the teams below are separated by.
 */
.season-slot {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  height: 100%;
}

.season-band {
  position: relative;
  width: 100%;
  height: 100%;
  cursor: pointer;
}

/*
 * Hidden rather than transparent: an affordance that is merely see-through still answers a
 * click, and a test that asks whether it is on screen would be told that it is.
 */
.season-slot__edit {
  position: absolute;
  top: 2px;
  right: 4px;
  z-index: 2;
  visibility: hidden;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  background: none;
  border: 0;
  color: var(--color-chalk);
  cursor: pointer;
}

.season-slot__edit svg {
  width: 22px;
  height: 22px;
}

/*
 * Focus-within rather than focus on the affordance itself: hidden means unfocusable, so an
 * affordance that waited to be focused could never be reached. Focus lands on the band first,
 * which reveals the affordance sitting behind it, and the next tab reaches it. While its own
 * dialog is open it stays put, so closing the dialog has somewhere to give focus back to.
 */
.season-slot:hover .season-slot__edit,
.season-slot:focus-within .season-slot__edit,
.season-slot--editing .season-slot__edit {
  visibility: visible;
}

/* No pointer to hover with, so there is no state to reveal it from. */
@media (hover: none) {
  .season-slot__edit {
    visibility: visible;
  }
}

.season-band::after {
  content: "";
  position: absolute;
  top: -6%;
  right: 0;
  height: 112%;
  width: 1px;
  background-color: color-mix(in oklab, var(--color-ash) 26%, transparent);
  rotate: 7deg;
  transition: background-color 320ms ease;
}

.season-band--last::after {
  display: none;
}

.season-band--lit::after {
  background-color: color-mix(in oklab, var(--accent) 60%, transparent);
}

/* The wash is what divides one season from the next: a faded band of the game's colour,
   deeper on the season being read. */
/*
 * Skewed to the same angle as the slanted rule that divides one season from the next, so the
 * lit band is bounded by the lines that bound the season rather than by a rectangle that
 * crosses them. The overhang this leaves at either end is clipped by the row.
 */
.season-band__wash {
  position: absolute;
  inset: 0;
  transform: skewX(-7deg);
  background:
    linear-gradient(to bottom, color-mix(in oklab, var(--accent) 10%, transparent), transparent 74%),
    linear-gradient(to bottom, transparent, color-mix(in oklab, var(--color-void) 60%, transparent));
  opacity: 0.45;
  transition: opacity 320ms ease;
}

/*
 * Every second band sits back a little, so the strip reads as a run of seasons rather than as
 * one flat wash. Only while it is at rest, though: written to match any band at all this
 * outweighed both the band being read and the band under the pointer — they carry one class
 * each and this carries two and a position — so every second season lost its highlight
 * altogether and the strip answered "which season is this" only half the time.
 */
.season-slot:nth-child(even) .season-band:not(.season-band--on, .season-band--lit) .season-band__wash {
  opacity: 0.2;
}

.season-band--lit .season-band__wash,
.season-band:focus-visible .season-band__wash {
  opacity: 0.85;
}

/*
 * The season on show, which is the first thing the strip is asked.
 *
 * Its wash, and nothing else: deeper in the game's own colour than a band under the pointer
 * can go, and washed up from the foot as well as down from the head, so it reads as lit from
 * within rather than drawn around. A rule along its edges said the same thing a second time
 * and did it loudly, and the strip sits under photography — shouting over that is the louder
 * mistake.
 */
.season-band--on .season-band__wash {
  opacity: 1;
  background:
    linear-gradient(to bottom, color-mix(in oklab, var(--accent) 20%, transparent), transparent 70%),
    linear-gradient(to top, color-mix(in oklab, var(--accent) 8%, transparent), transparent 38%);
}

/*
 * The band that offers another season: the same wash and the same slanted division as a
 * season's, so it reads as the next one along rather than as a control on the end.
 */
.season-band--add {
  display: grid;
  place-items: center;
  color: color-mix(in oklab, var(--color-ash) 88%, transparent);
}

.season-band--add .season-band__wash {
  opacity: 0.28;
}

.season-band--add:hover .island-plus,
.season-band--add:focus-visible .island-plus {
  opacity: 0.95;
  background: color-mix(in oklab, var(--color-chalk) 20%, transparent);
}

.season-band--add:hover .season-band__wash,
.season-band--add:focus-visible .season-band__wash {
  opacity: 0.75;
}


/*
 * Skewed rather than rotated, and to the same angle as the rule that divides one season from
 * the next: a rotated plus reads as tipped over, a skewed one leans with the band it sits in.
 */
.season-band__plus {
  position: relative;
  width: 60%;
  max-width: 66px;
  transform: skewX(-7deg);
}

.season-band__label {
  position: absolute;
  left: 50%;
  translate: -50% 0;
  white-space: nowrap;
  transition: color 240ms ease;
}

.season-band__label--half {
  font-family: var(--font-display);
  font-size: 11px;
  text-transform: uppercase;
  color: color-mix(in oklab, var(--color-ash) 80%, transparent);
}

.season-band__label--year {
  font-size: 10px;
  letter-spacing: 0.14em;
  color: color-mix(in oklab, var(--color-ash) 55%, transparent);
}

.season-band--lit .season-band__label--half,
.season-band--on .season-band__label--half {
  font-weight: 700;
  color: var(--color-chalk);
}

.season-band--lit .season-band__label--year,
.season-band--on .season-band__label--year {
  color: var(--color-ash);
}

.season-strip__line {
  position: absolute;
  inset: 0;
  height: var(--h);
  width: 100%;
  pointer-events: none;
  overflow: visible;
}

.season-strip__rule {
  stroke: color-mix(in oklab, var(--color-ash) 38%, transparent);
  stroke-width: 2;
}

/*
 * Clipped against its own box, which is the track: a share of the track is what the lit
 * length is reckoned as, so the two agree and the lit stretch ends on the middle of the node
 * whether the strip carries two seasons or twenty.
 */
.season-strip__lit {
  clip-path: inset(0 calc(100% - var(--lit)) 0 0);
  transition: clip-path 480ms cubic-bezier(0.22, 1, 0.36, 1);
}

.season-strip__lit path {
  stroke: var(--accent);
  stroke-width: 2.5;
  filter: drop-shadow(0 0 6px color-mix(in oklab, var(--accent) 60%, transparent));
}

/*
 * The way to the seasons that do not fit.
 *
 * The side of the strip rather than a button on it: the whole edge answers the pointer, and
 * what it shows is a fade coming out of that edge with a chevron in it — the strip carrying on
 * that way rather than a control sitting on top of it. Resting anywhere in the fade travels;
 * the chevron is where to look, not a target to hit.
 */
.season-strip__pan {
  position: absolute;
  top: 0;
  bottom: 0;
  z-index: 3;
  display: grid;
  align-items: center;
  width: 84px;
  padding: 0 12px;
  border: 0;
  background: none;
  color: var(--color-chalk);
  cursor: pointer;
}

.season-strip__pan::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.72;
  transition: opacity 220ms ease;
}

.season-strip__pan:hover::before,
.season-strip__pan:focus-visible::before {
  opacity: 1;
}

.season-strip__pan svg {
  position: relative;
  width: 26px;
  height: 26px;
  opacity: 0.78;
  transition: scale 220ms ease, opacity 220ms ease;
}

.season-strip__pan:hover svg,
.season-strip__pan:focus-visible svg {
  opacity: 1;
  scale: 1.24;
}

.season-strip__pan--back {
  left: 0;
  justify-items: start;
}

.season-strip__pan--back::before {
  background: linear-gradient(to right, color-mix(in oklab, var(--color-void) 82%, transparent), transparent);
}

.season-strip__pan--on {
  right: 0;
  justify-items: end;
}

.season-strip__pan--on::before {
  background: linear-gradient(to left, color-mix(in oklab, var(--color-void) 82%, transparent), transparent);
}

.season-strip__dot {
  position: absolute;
  height: 11px;
  width: 11px;
  translate: -50% -50%;
  border-radius: 9999px;
  background-color: var(--color-void);
  box-shadow: inset 0 0 0 2px color-mix(in oklab, var(--color-ash) 60%, transparent);
  pointer-events: none;
  transition: background-color 240ms ease, box-shadow 240ms ease, scale 240ms ease;
}

.season-strip__dot--lit {
  box-shadow: inset 0 0 0 2px var(--accent);
  scale: 1.15;
}

.season-strip__dot--on {
  background-color: var(--accent);
  box-shadow: inset 0 0 0 2px var(--accent), 0 0 0 5px color-mix(in oklab, var(--accent) 18%, transparent);
  scale: 1.3;
}

/*
 * On a phone the strip scrolls rather than shrinking, so a band keeps the width its labels
 * need and they stay on the line: the year and the half travel with their own nodes, which
 * is all the naming a highlighted band needs.
 */
@media (max-width: 767px) {
  .season-strip {
    --cut: 14px;
  }

  .season-band__label--half {
    font-size: 10px;
  }

  .season-band__label--year {
    font-size: 9px;
    letter-spacing: 0.1em;
  }

  .season-strip__caption {
    display: block;
  }
}

@media (prefers-reduced-motion: reduce) {
  .season-strip__lit,
  .season-strip__dot,
  .season-band__wash,
  .season-band__label,
  .season-strip__pan::before,
  .season-strip__pan svg {
    transition: none;
  }
}
</style>
