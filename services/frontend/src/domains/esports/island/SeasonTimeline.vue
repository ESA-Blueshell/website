<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from "vue"
import {seasonAxis} from "./seasonAxis"
import type {Season} from "../adapters/esports"

defineOptions({name: "SeasonTimeline"})

const props = defineProps<{
  seasons: Season[]
  selectedId: number | null
  accent: string
}>()

const emit = defineEmits<{(event: "select", id: number): void}>()

/** Room at each end so the outermost node and its label are not cut off. */
const INSET = 34
/*
 * The height has to hold the lowest thing the chain can produce: a node pushed down by an
 * indent, plus its label below that. Anything less and the label of an indented Spring
 * crosses into whatever follows the rail.
 */
const HEIGHT = 100
const AMPLITUDE = 11
/** How far a year's own stretch of chain drops while it is under the pointer. */
const INDENT = 14

const rail = ref<HTMLElement | null>(null)
const width = ref(0)
const hovered = ref<number | null>(null)

let observer: ResizeObserver | null = null
onMounted(() => {
  if (!rail.value || typeof ResizeObserver === "undefined") return
  observer = new ResizeObserver(entries => {
    width.value = entries[0]?.contentRect.width ?? 0
  })
  observer.observe(rail.value)
  width.value = rail.value.clientWidth
})
onBeforeUnmount(() => observer?.disconnect())

const axis = computed(() => seasonAxis(props.seasons))

/** The year the pointer is in, whose stretch of chain indents. */
const hoveredYear = computed<string>(() =>
  axis.value.nodes.find(n => n.season.id === hovered.value)?.year ?? "",
)

interface Point {
  id: number
  name: string
  half: string
  year: string
  x: number
  y: number
  /** True when the node sits above the middle, so its label goes above it too. */
  high: boolean
}

/**
 * The chain, in pixels.
 *
 * It snakes: consecutive seasons sit above and below the middle in turn, which makes a run of
 * twelve nodes read as a chain of links rather than as a ruler. While a year is under the
 * pointer its own two nodes drop, so the chain visibly gives way at the part being read.
 */
const points = computed<Point[]>(() => {
  const usable = Math.max(width.value - INSET * 2, 1)
  return axis.value.nodes.map((node, index) => {
    const high = index % 2 === 0
    const indent = node.year !== "" && node.year === hoveredYear.value ? INDENT : 0
    return {
      id: node.season.id,
      name: node.season.name,
      half: node.half,
      year: node.year,
      x: INSET + node.at * usable,
      y: HEIGHT / 2 + (high ? -AMPLITUDE : AMPLITUDE) + indent,
      high,
    }
  })
})

const polyline = computed<string>(() => points.value.map(p => `${p.x},${p.y}`).join(" "))

const selectedPoint = computed<Point | undefined>(() =>
  points.value.find(p => p.id === props.selectedId),
)

/** How much of the chain is lit: to the pointer, or to the season on show at rest. */
const litFraction = computed<number>(() => {
  const target = points.value.find(p => p.id === hovered.value) ?? selectedPoint.value
  if (!target || width.value === 0) return 0
  return Math.min(Math.max((target.x + 4) / width.value, 0), 1)
})

const caption = computed<string>(() =>
  (points.value.find(p => p.id === (hovered.value ?? props.selectedId))?.name) ?? "",
)

const step = (from: number, by: number) => {
  const next = axis.value.nodes[from + by]
  if (next) emit("select", next.season.id)
}
</script>

<template>
  <div
    ref="rail"
    class="season-timeline relative w-full select-none"
    data-testid="esports-season-timeline"
    :style="{'--accent': accent, '--lit': `${litFraction * 100}%`}"
    @mouseleave="hovered = null"
  >
    <!--
      The years, each centred over its own two halves. Six of them touch at phone widths, and
      the caption underneath already names the year, so they only appear once there is room.
    -->
    <div class="relative hidden h-4 sm:block">
      <span
        v-for="year in axis.years"
        :key="year.year"
        class="absolute -translate-x-1/2 font-body text-[10px] tracking-[0.2em] uppercase transition-colors duration-300 sm:text-[11px]"
        :class="year.year === hoveredYear ? 'text-chalk' : 'text-ash/70'"
        :style="{left: `${INSET + year.at * Math.max(width - INSET * 2, 1)}px`}"
      >{{ year.year }}</span>
    </div>

    <div
      class="relative"
      :style="{height: `${HEIGHT}px`}"
    >
      <svg
        aria-hidden="true"
        class="absolute inset-0 h-full w-full overflow-visible"
        :viewBox="`0 0 ${Math.max(width, 1)} ${HEIGHT}`"
      >
        <polyline
          class="season-timeline__rule"
          fill="none"
          :points="polyline"
        />
        <g class="season-timeline__lit">
          <polyline
            fill="none"
            :points="polyline"
          />
        </g>
      </svg>

      <button
        v-for="(point, index) in points"
        :key="point.id"
        class="season-timeline__node absolute rounded-full p-2"
        :class="{'season-timeline__node--on': point.id === selectedId}"
        :data-testid="`esports-season-node-${point.id}`"
        :style="{left: `${point.x}px`, top: `${point.y}px`, transform: 'translate(-50%, -50%)'}"
        type="button"
        @click="emit('select', point.id)"
        @focus="hovered = point.id"
        @keydown.left.prevent="step(index, -1)"
        @keydown.right.prevent="step(index, 1)"
        @mouseenter="hovered = point.id"
      >
        <span class="sr-only">{{ point.name }}</span>
        <span
          aria-hidden="true"
          class="season-timeline__dot block h-2 w-2 rounded-full"
        />
      </button>

      <!--
        Each half sits with its own node, above it or below it as the chain rises and falls.
        Twelve of them collide on a phone, so there they give way to the caption underneath.
      -->
      <span
        v-for="point in points"
        :key="`label-${point.id}`"
        class="season-timeline__half absolute hidden -translate-x-1/2 font-display text-[10px] whitespace-nowrap uppercase transition-[color,font-weight] duration-200 sm:block sm:text-[11px]"
        :class="[
          point.id === hovered ? 'font-bold text-chalk' : '',
          point.id === selectedId && point.id !== hovered ? 'text-chalk' : '',
          point.id !== hovered && point.id !== selectedId ? 'text-ash/60' : '',
        ]"
        :style="{
          left: `${point.x}px`,
          top: point.high ? `${point.y - 22}px` : `${point.y + 10}px`,
        }"
      >{{ point.half }}</span>
    </div>

    <p
      class="mt-1 text-center font-display text-xs uppercase sm:hidden"
      data-testid="esports-season-caption"
    >
      {{ caption }}
    </p>
  </div>
</template>

<style scoped>
/* Drawn dashes rather than a dashed border: a border cannot follow a polyline, and this has
   to read as a measured chain rather than a hairline rule. */
.season-timeline__rule {
  stroke: color-mix(in oklab, var(--color-ash) 42%, transparent);
  stroke-width: 2;
  stroke-dasharray: 7 7;
  stroke-linecap: round;
  transition: none;
}

.season-timeline__lit {
  clip-path: inset(0 calc(100% - var(--lit)) 0 0);
  transition: clip-path 460ms cubic-bezier(0.22, 1, 0.36, 1);
}

.season-timeline__lit polyline {
  stroke: var(--accent);
  stroke-width: 2;
  stroke-dasharray: 7 7;
  stroke-linecap: round;
  filter: drop-shadow(0 0 5px color-mix(in oklab, var(--accent) 60%, transparent));
}

.season-timeline__dot {
  background-color: var(--color-ash);
  transition: background-color 240ms ease, scale 240ms ease, box-shadow 240ms ease;
}

.season-timeline__node:hover .season-timeline__dot,
.season-timeline__node:focus-visible .season-timeline__dot {
  background-color: var(--accent);
  scale: 1.5;
}

.season-timeline__node--on .season-timeline__dot {
  background-color: var(--accent);
  scale: 1.6;
  box-shadow: 0 0 0 4px color-mix(in oklab, var(--accent) 20%, transparent);
}

/* The node positions themselves move when a year indents, so they transition with the chain. */
.season-timeline__node,
.season-timeline__half {
  transition: top 320ms cubic-bezier(0.22, 1, 0.36, 1);
}

@media (prefers-reduced-motion: reduce) {
  .season-timeline__lit,
  .season-timeline__dot,
  .season-timeline__node,
  .season-timeline__half {
    transition: none;
  }
}
</style>
