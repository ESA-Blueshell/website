<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, useId, watch} from "vue"
import {litAt, STRIP, stripAxis, type Stop} from "./stripAxis"
import {useMotionAllowed} from "./useMotionAllowed"

defineOptions({name: "IslandTimeline"})

const props = withDefaults(defineProps<{
  /** What the strip runs through, in the order it reads them: left is first, right is last. */
  stops: Stop[]
  /** The stop being read, which is where the line is lit to at rest. */
  selectedId: number | null
  accent: string
  /** Whether to offer the edit affordance. Decided by the page, which knows who is reading. */
  mayEdit?: boolean
  /** What a spec names this strip and everything on it by. */
  testidPrefix?: string
  /** What the block at the end of the line offers, in whatever a stop is called here. */
  addLabel?: string
  /** The two ways along the strip, named for what lies that way. */
  panBackLabel?: string
  panOnLabel?: string
}>(), {
  mayEdit: false,
  testidPrefix: "island",
  addLabel: "Add",
  panBackLabel: "Show what came before",
  panOnLabel: "Show what came after",
})

const emit = defineEmits<{
  (event: "select", id: number): void
  (event: "edit", id: number): void
  (event: "add"): void
}>()

const motion = useMotionAllowed()

/** How fast the strip travels while the pointer rests on its side, in px per ms. */
const PAN_RATE = 0.55
/** How much of the strip a click on a chevron moves, as a share of what is in view. */
const PAN_STEP = 0.8
/**
 * How far in from each edge counts as resting on that side, and at most what share of the
 * strip that may be.
 *
 * The side answers the pointer, but only the chevron answers a click: a band under a fade is
 * still a band to be clicked, and a control the width of this zone would have taken the
 * stops at both ends out of reach. The share matters on a narrow strip, where two zones of
 * a fixed width would between them be most of it.
 */
const PAN_ZONE = 84
const PAN_ZONE_SHARE = 0.18

const strip = ref<HTMLElement | null>(null)
const scroller = ref<HTMLElement | null>(null)
const width = ref(0)
const hovered = ref<number | null>(null)
/**
 * The stop whose affordance was last taken up.
 *
 * It stays visible until the pointer goes to another stop. Opening the dialog moves focus
 * into it, which means the affordance is neither hovered nor focused while it is open, and
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
 * The block offering another stop is a band like the rest, so it takes a share of the strip
 * rather than floating over the end of it.
 */
const trailing = computed(() => (props.mayEdit ? 1 : 0))

/** Where everything on the strip sits, in pixels: the bands, the nodes and the line. */
const axis = computed(() => stripAxis(props.stops, {width: width.value, trailing: trailing.value}))
const bands = computed(() => axis.value.bands)
const nodes = computed(() => axis.value.nodes)
const track = computed(() => axis.value.track)

/**
 * How far the line is lit: to the pointer, or to the stop being read at rest.
 *
 * A share of the track, because that is the box the layer drawing it occupies, so the lit
 * stretch ends on the middle of the node however many stops there are and whether or not
 * the strip reserves a band for adding one.
 */
const litFraction = computed<number>(() => {
  if (track.value === 0) return 0
  const at = litAt(nodes.value, hovered.value ?? props.selectedId)
  return Math.min(Math.max(at / track.value, 0), 1)
})

/**
 * The colour the line is lit in: the stop under the pointer, then the stop being read, then
 * the strip's own.
 *
 * A line lit as far as a stop is lit in that stop's colour, so moving down the line is moving
 * through their colours rather than watching one colour reach further. The line's `stroke`
 * carries it, which is a property a browser interpolates, so the change is a fade from one
 * colour to the next and costs no animation of its own.
 */
const litAccent = computed<string>(() => {
  const at = props.stops.find(stop => stop.id === (hovered.value ?? props.selectedId))
  return at?.accent?.trim() || props.accent
})

/**
 * The stop being read was chosen here, on a node in front of the visitor.
 *
 * The strip opens on the stop being read, which is right when that stop arrives from
 * somewhere the visitor cannot see: a shared link, the back button, one just written
 * down. It is wrong after a click: the node they aimed at would slide out from under the
 * pointer, and whichever band slid into its place would light the line instead.
 *
 * The id rather than a flag, because a click is not a promise. Where the page declines to
 * follow one (a refused read, a parent that ignores it) a flag would sit set and swallow
 * the next stop that did arrive from elsewhere, and the back button would stop centring.
 * An id only ever holds back the scroll for the one stop it names.
 */
const chosenHere = ref<number | null>(null)

const choose = (id: number) => {
  if (id !== props.selectedId) chosenHere.value = id
  emit("select", id)
}

/** A strip wider than its window opens on the stop being read, not at the far past. */
watch([() => props.selectedId, track, width], ([id], [before]) => {
  const claimed = chosenHere.value
  chosenHere.value = null
  if (id !== before && claimed === id) return
  const node = nodes.value.find(n => n.id === props.selectedId)
  const box = scroller.value
  if (!node || !box || box.scrollWidth <= box.clientWidth) return
  box.scrollTo({left: node.x - box.clientWidth / 2, behavior: "auto"})
}, {flush: "post"})

/**
 * Whether there is anything further to see each way.
 *
 * The scrollbar is hidden and the ends fade rather than cutting, so without this the stops
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
/** Which way the strip is travelling, for the side that is doing it to show that it is. */
const travelling = ref(0)

/** Travels while the pointer rests on an arrow, and stops where there is no further to go. */
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

/**
 * Whether there is a pointer that can rest on something.
 *
 * A touch screen has none: a finger is either on the strip or off it, and dragging is how the
 * strip is travelled there. Left ungated, the first tap anywhere near an edge would set the
 * strip moving under it.
 */
const canHover = () => typeof window === "undefined"
  || typeof window.matchMedia !== "function"
  || window.matchMedia("(hover: hover)").matches

/** Travels while the pointer is down either side of the strip, and stands still between. */
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
  if (next) emit("select", next.stop.id)
}
</script>

<template>
  <div
    ref="strip"
    class="timeline"
    :data-testid="`${testidPrefix}-timeline`"
    :style="{
      '--accent': litAccent,
      '--lit': `${litFraction * 100}%`,
      '--h': `${STRIP.height}px`,
      '--track': `${track}px`,
    }"
    @mousemove="aim"
    @mouseleave="hovered = null; rest()"
  >
    <div
      ref="scroller"
      class="timeline__scroll"
      @scroll="measureScroll"
    >
      <div class="timeline__track">
        <!--
          One band per stop, tiled exactly so a node can sit in the middle of its own
          stop and the division between two bands falls halfway between their nodes, which
          is where whatever the strip governs divides too. Hovering highlights a band and
          lights the line as far as its node; changing stop takes a click, so a pointer
          crossing the strip changes nothing.
        -->
        <div class="timeline__stops">
          <div
            v-for="(band, index) in bands"
            :key="band.stop.id"
            class="stop-slot"
            :class="{'stop-slot--editing': band.stop.id === pinned}"
            :style="band.stop.accent ? {'--accent': band.stop.accent} : undefined"
            @mouseenter="enter(band.stop.id)"
          >
            <button
              class="stop"
              :class="{
                'stop--on': band.stop.id === selectedId,
                'stop--lit': band.stop.id === hovered,
                'stop--last': index === bands.length - 1 && !mayEdit,
              }"
              :aria-current="band.stop.id === selectedId ? 'true' : undefined"
              :data-testid="`${testidPrefix}-node-${band.stop.id}`"
              type="button"
              @click="choose(band.stop.id)"
              @focus="hovered = band.stop.id"
              @keydown.left.prevent="step(index, -1)"
              @keydown.right.prevent="step(index, 1)"
            >
              <span class="sr-only">{{ band.stop.name }}</span>
              <span
                aria-hidden="true"
                class="stop__wash"
              />
              <span
                aria-hidden="true"
                class="stop__label stop__label--lead"
                :style="{top: band.high ? `${yOf(band.stop.id) + 18}px` : `${yOf(band.stop.id) - 34}px`}"
              >{{ band.stop.label }}</span>
              <span
                aria-hidden="true"
                class="stop__label stop__label--year"
                :style="{top: band.high ? `${yOf(band.stop.id) + 32}px` : `${yOf(band.stop.id) - 20}px`}"
              >{{ band.stop.sublabel }}</span>
              <!--
                The stop the strip is marking out, in whatever word the page marks it with. On
                the far side of the node from the two labels, so it reads as a note on the stop
                rather than a third line of its name. Spoken as part of the stop's name, which
                is why this is hidden like the labels are.
              -->
              <span
                v-if="band.stop.mark"
                aria-hidden="true"
                class="stop__label stop__mark"
                :data-testid="`${testidPrefix}-mark-${band.stop.id}`"
                :style="{top: band.high ? `${yOf(band.stop.id) - 26}px` : `${yOf(band.stop.id) + 12}px`}"
              >{{ band.stop.mark }}</span>
            </button>

            <!--
              Offered only to somebody who may take it up. Where there is a pointer it belongs
              to the stop being pointed at; where there is not, there is nothing to hover
              with, so it simply stands.
            -->
            <button
              v-if="mayEdit"
              :aria-label="`Edit ${band.stop.name}`"
              class="stop-slot__edit"
              :data-testid="`${testidPrefix}-edit-${band.stop.id}`"
              type="button"
              @click="pinned = band.stop.id; emit('edit', band.stop.id)"
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
            Stops are added at the end of the line, which is where their absence is noticed,
            so the offer is a band there rather than a control floating over it. It stands
            rather than waiting to be hovered: there is no stop under the pointer for it to
            belong to.
          -->
          <div
            v-if="mayEdit"
            class="stop-slot stop-slot--add"
          >
            <button
              :aria-label="addLabel"
              class="stop stop--add"
              :data-testid="`${testidPrefix}-add`"
              type="button"
              @click="emit('add')"
            >
              <span
                aria-hidden="true"
                class="stop__wash"
              />
              <span
                aria-hidden="true"
                class="stop__plus island-plus"
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
          stretch as far as the stop being read. Two svgs rather than two groups in one,
          because the lit one is revealed by clipping its own box, and a box is a thing a
          group does not have, which is what left the lit stretch measured against the
          bounding box of the path and stopping short of the stop it was reporting.
        -->
        <template v-if="axis.path">
          <svg
            aria-hidden="true"
            class="timeline__line"
            preserveAspectRatio="none"
            :viewBox="`0 0 ${Math.max(track, 1)} ${STRIP.height}`"
          >
            <defs>
              <!--
                The line dissolves as it arrives at its end rather than stopping at one. Where
                the block that adds a stop bounds it, its end is in view, and a stub with a
                cap on it reads as a drawing laid on the strip rather than as the stops
                carrying on. Nothing is left over to fall on a block that is not a stop.
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
              class="timeline__rule"
              :d="axis.path"
              fill="none"
              :mask="`url(#${maskId})`"
              vector-effect="non-scaling-stroke"
            />
          </svg>

          <svg
            aria-hidden="true"
            class="timeline__line timeline__lit"
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
          class="timeline__dot"
          :class="{
            'timeline__dot--on': node.id === selectedId,
            'timeline__dot--lit': node.id === hovered,
          }"
          :style="{left: `${node.x}px`, top: `${node.y}px`}"
        />
      </div>
    </div>

    <!--
      Where the strip holds more stops than fit, the way to the rest of them. Resting the
      pointer on one travels that way; a click moves a screenful, which is what somebody
      arriving by keyboard gets. Neither changes the stop being read.
    -->
    <button
      v-if="canPanBack"
      :aria-label="panBackLabel"
      class="timeline__pan timeline__pan--back"
      :class="{'timeline__pan--live': travelling === -1}"
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
      class="timeline__pan timeline__pan--on"
      :class="{'timeline__pan--live': travelling === 1}"
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
.timeline {
  --cut: 26px;

  position: relative;
  width: 100%;
  /* The same band ground as the sign-up bar, so the strip reads as a panel rather
     than as a stretch of patterned page with a line over it. */
  background-color: var(--band-ground);
  user-select: none;
}

/* The ends fade rather than stopping at a line: the strip belongs to the page it sits on. */
.timeline__scroll {
  mask-image: linear-gradient(to right, transparent 0, #000 5%, #000 95%, transparent 100%);
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
}

.timeline__scroll::-webkit-scrollbar {
  display: none;
}

.timeline__track {
  position: relative;
  width: var(--track);
  min-width: 100%;
}

.timeline__stops {
  display: flex;
  height: var(--h);
  width: 100%;
  overflow: hidden;
}

/*
 * The bands tile exactly, each taking the same share of the track, because the nodes are
 * placed by arithmetic on that same share. Overlapping them to interlock a diagonal clip
 * moved every band's centre off the node it belongs to, and the labels drifted away from
 * their own dots. The division is drawn instead: a slanted rule on the trailing edge, at the
 * angle the panels under the strip are cut at.
 */
.stop-slot {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  height: 100%;
}

.stop {
  position: relative;
  width: 100%;
  height: 100%;
  cursor: pointer;
}

/*
 * Hidden rather than transparent: an affordance that is merely see-through still answers a
 * click, and a test that asks whether it is on screen would be told that it is.
 */
.stop-slot__edit {
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

.stop-slot__edit svg {
  width: 22px;
  height: 22px;
}

/*
 * Focus on the slot rather than on the affordance itself: hidden means unfocusable, so an
 * affordance that waited to be focused could never be reached. Focus lands on the band first,
 * which reveals the affordance sitting behind it, and the next tab reaches it. While its own
 * dialog is open it stays put, so closing the dialog has somewhere to give focus back to.
 */
.stop-slot:hover .stop-slot__edit,
.stop-slot:focus-within .stop-slot__edit,
.stop-slot--editing .stop-slot__edit {
  visibility: visible;
}

/*
 * Except where the focus the slot holds is a pointer's. A click focuses the band it lands on,
 * and `:focus-within` cannot tell that focus from a keyboard's, so a band clicked to choose
 * a stop went on offering to be edited long after the pointer had left it, and read as a
 * control that had latched.
 *
 * Written as an exception to the rule above rather than folded into it. The obvious fold,
 * asking the slot for `:has(:focus-visible)`, breaks the keyboard route: the affordance is
 * revealed only while the band holds the focus, so the tab that hands the focus over hides
 * the affordance in the same recalc, and the browser drops the focus rather than landing it
 * on something that has just gone. `:focus-within` survives that hand-off because the focus
 * never leaves the slot, and the exception below does not apply during it: the band has let
 * the focus go by then.
 *
 * Asked of a pointer rather than written to outrank the standing rule below. Where there is
 * nothing to hover with, a tap is the only way to reach anything and leaves the focus behind
 * it, so an exception about pointers would hide every affordance on the strip the moment one
 * was used.
 */
@media (hover: hover) {
  .stop-slot:not(:hover):not(.stop-slot--editing):has(.stop:focus:not(:focus-visible)) .stop-slot__edit {
    visibility: hidden;
  }
}

/* No pointer to hover with, so there is no state to reveal it from. */
@media (hover: none) {
  .stop-slot__edit {
    visibility: visible;
  }
}

.stop::after {
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

.stop--last::after {
  display: none;
}

.stop--lit::after {
  background-color: color-mix(in oklab, var(--accent) 60%, transparent);
}

/*
 * Skewed to the angle of the rule that divides one stop from the next, so the wash is bounded
 * by the lines that bound the stop rather than by a rectangle crossing them. The overhang this
 * leaves at either end is clipped by the row.
 */
.stop__wash {
  position: absolute;
  inset: 0;
  transform: skewX(-7deg);
  background:
    linear-gradient(to bottom, color-mix(in oklab, var(--accent) var(--band-wash), transparent), transparent 74%),
    linear-gradient(to bottom, transparent, color-mix(in oklab, var(--color-ground) 60%, transparent));
  opacity: 0.45;
  transition: opacity 320ms ease;
}

/*
 * Every second band sits back a little, so the strip reads as a run of stops rather than as one
 * flat wash. Only while it is at rest, though: written to match any band at all this outweighed
 * both the band being read and the band under the pointer, which carry one class each where
 * this carries two and a position, so every second stop lost its highlight altogether.
 */
.stop-slot:nth-child(even) .stop:not(.stop--on, .stop--lit) .stop__wash {
  opacity: 0.2;
}

.stop--lit .stop__wash,
.stop:focus-visible .stop__wash {
  opacity: 0.85;
}

/*
 * The stop being read, which is the first thing the strip is asked.
 *
 * Its wash, and nothing else: deeper in the stop's own colour than a band under the pointer
 * can go, and washed up from the foot as well as down from the head, so it reads as lit from
 * within rather than drawn around. A rule along its edges said the same thing a second time and
 * shouted it, and the strip sits under photography.
 */
.stop--on .stop__wash {
  opacity: 1;
  background:
    linear-gradient(to bottom, color-mix(in oklab, var(--accent) var(--band-wash-on), transparent), transparent 70%),
    linear-gradient(to top, color-mix(in oklab, var(--accent) var(--band-wash-on-foot), transparent), transparent 38%);
}

/*
 * The band that offers another stop: the same wash and the same slanted division as a stop's,
 * so it reads as the next one along rather than as a control on the end.
 */
.stop--add {
  display: grid;
  place-items: center;
  color: color-mix(in oklab, var(--color-ash) 88%, transparent);
}

.stop--add .stop__wash {
  opacity: 0.28;
}

.stop--add:hover .island-plus,
.stop--add:focus-visible .island-plus {
  opacity: 0.95;
  background: color-mix(in oklab, var(--color-chalk) 20%, transparent);
}

.stop--add:hover .stop__wash,
.stop--add:focus-visible .stop__wash {
  opacity: 0.75;
}


/*
 * Skewed rather than rotated, and to the same angle as the rule that divides one stop from the
 * next: a rotated plus reads as tipped over, a skewed one leans with the band it sits in.
 */
.stop__plus {
  position: relative;
  width: 60%;
  max-width: 66px;
  transform: skewX(-7deg);
}

.stop__label {
  position: absolute;
  left: 50%;
  translate: -50% 0;
  white-space: nowrap;
  transition: color 240ms ease;
}

/* The larger of a stop's two labels, whatever the page names it with, set over the year. */
.stop__label--lead {
  font-family: var(--font-display);
  font-size: 11px;
  text-transform: uppercase;
  color: color-mix(in oklab, var(--color-ash) var(--label-mix-lead), transparent);
}

.stop__label--year {
  font-size: 10px;
  letter-spacing: 0.14em;
  color: color-mix(in oklab, var(--color-ash) var(--label-mix), transparent);
}

/*
 * The mark, in the accent of whatever the strip is drawing: a tinted tag rather than an
 * outlined one, because the slanted clip cuts a border and what survives it reads as a line
 * struck through the word. Undiluted by the label mix, since it is a fact about the stop rather
 * than a louder or quieter copy of its name.
 */
.stop__mark {
  padding: 1px 6px 2px;
  font-family: var(--font-display);
  font-size: 8.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--accent) 32%, transparent);
  clip-path: polygon(0.26rem 0, 100% 0, calc(100% - 0.26rem) 100%, 0 100%);
}

.stop--lit .stop__label--lead,
.stop--on .stop__label--lead {
  font-weight: 700;
  color: var(--color-chalk);
}

.stop--lit .stop__label--year,
.stop--on .stop__label--year {
  color: var(--color-ash);
}

.timeline__line {
  position: absolute;
  inset: 0;
  height: var(--h);
  width: 100%;
  pointer-events: none;
  overflow: visible;
}

.timeline__rule {
  stroke: color-mix(in oklab, var(--color-ash) 38%, transparent);
  stroke-width: 2;
}

/*
 * Clipped against its own box, which is the track: a share of the track is what the lit
 * length is reckoned as, so the two agree and the lit stretch ends on the middle of the node
 * whether the strip carries two stops or twenty.
 */
.timeline__lit {
  clip-path: inset(0 calc(100% - var(--lit)) 0 0);
  transition: clip-path 480ms cubic-bezier(0.22, 1, 0.36, 1);
}

/* The colour of the stop the line is lit to, faded into rather than swapped: `stroke` and
   `filter` are both interpolated, so the line travels and recolours together. */
.timeline__lit path {
  stroke: var(--accent);
  stroke-width: 2.5;
  filter: drop-shadow(0 0 6px color-mix(in oklab, var(--accent) 60%, transparent));
  transition:
    stroke 420ms cubic-bezier(0.22, 1, 0.36, 1),
    filter 420ms cubic-bezier(0.22, 1, 0.36, 1);
}

@media (prefers-reduced-motion: reduce) {
  .timeline__lit path {
    transition: none;
  }
}

/*
 * The way to the stops that do not fit.
 *
 * The side of the strip answers the pointer (resting anywhere down either edge travels that
 * way) and shows a fade with a chevron in it, the strip carrying on rather than a control
 * sitting on top of it. Only the chevron answers a click, and the fade takes no clicks at all:
 * a stop under either is still a stop to be clicked, which a control the width of the whole
 * edge would have put out of reach.
 */
.timeline__pan {
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

.timeline__pan::before {
  content: "";
  position: absolute;
  top: -26px;
  bottom: -26px;
  pointer-events: none;
  opacity: 0.72;
  transition: opacity 220ms ease;
}

.timeline__pan--live::before,
.timeline__pan:hover::before,
.timeline__pan:focus-visible::before {
  opacity: 1;
}

.timeline__pan svg {
  position: relative;
  width: 26px;
  height: 26px;
  opacity: 0.78;
  transition: scale 220ms ease, opacity 220ms ease;
}

.timeline__pan--live svg,
.timeline__pan:hover svg,
.timeline__pan:focus-visible svg {
  opacity: 1;
  scale: 1.24;
}

.timeline__pan--back {
  left: 0;
}

.timeline__pan--back::before {
  left: 0;
  right: -40px;
  background: linear-gradient(to right, color-mix(in oklab, var(--color-ground) 82%, transparent), transparent);
}

.timeline__pan--on {
  right: 0;
}

.timeline__pan--on::before {
  left: -40px;
  right: 0;
  background: linear-gradient(to left, color-mix(in oklab, var(--color-ground) 82%, transparent), transparent);
}

.timeline__dot {
  position: absolute;
  height: 11px;
  width: 11px;
  translate: -50% -50%;
  border-radius: 9999px;
  background-color: var(--color-ground);
  box-shadow: inset 0 0 0 2px color-mix(in oklab, var(--color-ash) 60%, transparent);
  pointer-events: none;
  transition: background-color 240ms ease, box-shadow 240ms ease, scale 240ms ease;
}

.timeline__dot--lit {
  box-shadow: inset 0 0 0 2px var(--accent);
  scale: 1.15;
}

.timeline__dot--on {
  background-color: var(--accent);
  box-shadow: inset 0 0 0 2px var(--accent), 0 0 0 5px color-mix(in oklab, var(--accent) 18%, transparent);
  scale: 1.3;
}

/*
 * On a phone the strip scrolls rather than shrinking, so a band keeps the width its labels
 * need and they stay on the line: both labels travel with their own nodes, which is all the
 * naming a highlighted band needs.
 */
@media (max-width: 767px) {
  .timeline {
    --cut: 14px;
  }

  .stop__label--lead {
    font-size: 10px;
  }

  .stop__label--year {
    font-size: 9px;
    letter-spacing: 0.1em;
  }

  .timeline__caption {
    display: block;
  }
}

@media (prefers-reduced-motion: reduce) {
  .timeline__lit,
  .timeline__dot,
  .stop__wash,
  .stop__label,
  .timeline__pan::before,
  .timeline__pan svg {
    transition: none;
  }
}
</style>
