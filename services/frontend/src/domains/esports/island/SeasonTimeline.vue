<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
import {seasonBands} from "./seasonAxis"
import type {Season} from "../adapters/esports"

defineOptions({name: "SeasonTimeline"})

const props = defineProps<{
  seasons: Season[]
  selectedId: number | null
  accent: string
}>()

const emit = defineEmits<{(event: "select", id: number): void}>()

const HEIGHT = 104
/**
 * The narrowest a band may be. Below this a node is untappable and its labels unreadable, so
 * the strip stops shrinking and starts scrolling instead — which is what a phone gets.
 */
const MIN_BAND = 94
/** How far a node sits above or below the middle of the strip. */
const AMPLITUDE = 15
/**
 * How wide a bend is, as a multiple of the height it has to climb.
 *
 * The bend is a fixed window in the middle of the gap rather than the whole of it, so the
 * line runs straight for most of its length and then turns in a short, round corner. Because
 * the window is sized from the climb rather than from the gap, every bend turns through the
 * same radius however far apart two seasons happen to sit.
 */
const BEND = 1.35

const strip = ref<HTMLElement | null>(null)
const scroller = ref<HTMLElement | null>(null)
const width = ref(0)
const hovered = ref<number | null>(null)

let observer: ResizeObserver | null = null
onMounted(() => {
  if (!strip.value || typeof ResizeObserver === "undefined") return
  observer = new ResizeObserver(entries => {
    width.value = entries[0]?.contentRect.width ?? 0
  })
  observer.observe(strip.value)
  width.value = strip.value.clientWidth
})
onBeforeUnmount(() => observer?.disconnect())

const bands = computed(() => seasonBands(props.seasons))

/** As wide as the strip can be, or as wide as its bands need — whichever is greater. */
const track = computed<number>(() => Math.max(width.value, bands.value.length * MIN_BAND))

const nodes = computed(() =>
  bands.value.map(band => ({
    id: band.season.id,
    x: band.at * track.value,
    y: HEIGHT / 2 + (band.high ? -AMPLITUDE : AMPLITUDE),
  })),
)

/**
 * The line: flat through each node, then an eased bend to the level of the next.
 *
 * Both control points of a bend sit at its midpoint, which leaves the curve horizontal where
 * it meets each flat run — so it reads as a straight stretch, a bend, another straight
 * stretch, rather than as a zigzag with rounded corners. It runs to both edges of the strip
 * because the seasons do.
 */
const path = computed<string>(() => {
  const points = nodes.value
  const first = points[0]
  const last = points[points.length - 1]
  if (!first || !last || track.value === 0) return ""
  const parts = [`M 0,${first.y}`]
  for (let i = 1; i < points.length; i += 1) {
    const from = points[i - 1]
    const to = points[i]
    if (!from || !to) continue
    const gap = to.x - from.x
    const climb = Math.abs(to.y - from.y)
    // A bend never eats more than two thirds of the gap, however tight the seasons are.
    const bend = Math.min(gap * 0.66, climb * BEND)
    const start = from.x + (gap - bend) / 2
    const end = start + bend
    const mid = (start + end) / 2
    parts.push(`L ${start},${from.y}`)
    parts.push(`C ${mid},${from.y} ${mid},${to.y} ${end},${to.y}`)
  }
  parts.push(`L ${track.value},${last.y}`)
  return parts.join(" ")
})

/** How much of the line is lit: to the pointer, or to the season on show at rest. */
const litFraction = computed<number>(() => {
  const target = nodes.value.find(n => n.id === (hovered.value ?? props.selectedId))
  if (!target || track.value === 0) return 0
  return Math.min(Math.max((target.x + 6) / track.value, 0), 1)
})

/** A strip wider than its window opens on the season being shown, not at the far past. */
watch([() => props.selectedId, track, width], () => {
  const node = nodes.value.find(n => n.id === props.selectedId)
  const box = scroller.value
  if (!node || !box || box.scrollWidth <= box.clientWidth) return
  box.scrollTo({left: node.x - box.clientWidth / 2, behavior: "auto"})
}, {flush: "post"})

const caption = computed<string>(() =>
  bands.value.find(b => b.season.id === (hovered.value ?? props.selectedId))?.season.name ?? "",
)

const yOf = (id: number) => nodes.value.find(n => n.id === id)?.y ?? HEIGHT / 2

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
      '--h': `${HEIGHT}px`,
      '--track': `${track}px`,
    }"
    @mouseleave="hovered = null"
  >
    <div
      ref="scroller"
      class="season-strip__scroll"
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
          <button
            v-for="(band, index) in bands"
            :key="band.season.id"
            class="season-band"
            :class="{
              'season-band--on': band.season.id === selectedId,
              'season-band--lit': band.season.id === hovered,
              'season-band--last': index === bands.length - 1,
            }"
            :aria-current="band.season.id === selectedId ? 'true' : undefined"
            :data-testid="`esports-season-node-${band.season.id}`"
            type="button"
            @click="emit('select', band.season.id)"
            @focus="hovered = band.season.id"
            @keydown.left.prevent="step(index, -1)"
            @keydown.right.prevent="step(index, 1)"
            @mouseenter="hovered = band.season.id"
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
        </div>

        <svg
          aria-hidden="true"
          class="season-strip__line"
          preserveAspectRatio="none"
          :viewBox="`0 0 ${Math.max(track, 1)} ${HEIGHT}`"
        >
          <path
            class="season-strip__rule"
            :d="path"
            fill="none"
            vector-effect="non-scaling-stroke"
          />
          <g class="season-strip__lit">
            <path
              :d="path"
              fill="none"
              vector-effect="non-scaling-stroke"
            />
          </g>
        </svg>

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

    <p
      class="season-strip__caption"
      data-testid="esports-season-caption"
    >
      {{ caption }}
    </p>
  </div>
</template>

<style scoped>
.season-strip {
  --cut: 26px;

  position: relative;
  width: 100%;
  user-select: none;
}

.season-strip__scroll {
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
}

/*
 * The bands tile exactly, each taking the same share of the track, because the nodes are
 * placed by arithmetic on that same share. Overlapping them to interlock a diagonal clip
 * moved every band's centre off the node it belongs to, and the labels drifted away from
 * their own dots. The division is drawn instead: a slanted rule on the trailing edge, which
 * reads as the same cut the teams below are separated by.
 */
.season-band {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  height: 100%;
  cursor: pointer;
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

.season-band--lit::after,
.season-band--on::after {
  background-color: color-mix(in oklab, var(--accent) 60%, transparent);
}

/* The wash is what divides one season from the next: a faded band of the game's colour,
   deeper on the season being read. */
.season-band__wash {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(to bottom, color-mix(in oklab, var(--accent) 10%, transparent), transparent 74%),
    linear-gradient(to bottom, transparent, color-mix(in oklab, var(--color-void) 60%, transparent));
  opacity: 0.45;
  transition: opacity 320ms ease;
}

.season-band:nth-child(even) .season-band__wash {
  opacity: 0.2;
}

.season-band--lit .season-band__wash,
.season-band:focus-visible .season-band__wash {
  opacity: 0.85;
}

.season-band--on .season-band__wash {
  opacity: 1;
  background: linear-gradient(
    to bottom,
    color-mix(in oklab, var(--accent) 26%, transparent),
    transparent 78%
  );
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
  stroke-linecap: round;
}

.season-strip__lit {
  clip-path: inset(0 calc(100% - var(--lit)) 0 0);
  transition: clip-path 480ms cubic-bezier(0.22, 1, 0.36, 1);
}

.season-strip__lit path {
  stroke: var(--accent);
  stroke-width: 2.5;
  stroke-linecap: round;
  filter: drop-shadow(0 0 6px color-mix(in oklab, var(--accent) 60%, transparent));
}

/* Drawn as elements rather than inside the svg: the line is stretched to the track by its
   viewBox, and a circle in that space would stretch with it. */
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

.season-strip__caption {
  display: none;
  margin-top: 0.35rem;
  text-align: center;
  font-family: var(--font-display);
  font-size: 0.75rem;
  text-transform: uppercase;
}

/* Twelve bands are thirty pixels wide on a phone, which is room for a node and nothing else,
   so the labels give way to one caption naming whichever season is being read. */
@media (max-width: 767px) {
  .season-strip {
    --cut: 14px;
  }

  .season-band__label {
    display: none;
  }

  .season-strip__caption {
    display: block;
  }
}

@media (prefers-reduced-motion: reduce) {
  .season-strip__lit,
  .season-strip__dot,
  .season-band__wash,
  .season-band__label {
    transition: none;
  }
}
</style>
