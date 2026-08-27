<script lang="ts" setup>
import {computed, ref} from "vue"
import {seasonAxis} from "./seasonAxis"
import type {Season} from "../adapters/esports"

defineOptions({name: "SeasonTimeline"})

const props = defineProps<{
  seasons: Season[]
  selectedId: number | null
  accent: string
}>()

const emit = defineEmits<{(event: "select", id: number): void}>()

const hovered = ref<number | null>(null)

const axis = computed(() => seasonAxis(props.seasons))

const selectedAt = computed<number>(() => {
  const node = axis.value.nodes.find(n => n.season.id === props.selectedId)
  return node?.at ?? 0
})

/**
 * How far along the line is lit. It follows the pointer while a node is under it and falls
 * back to the season on show, so the line always says where you are even at rest.
 */
const litTo = computed<number>(() => {
  const node = axis.value.nodes.find(n => n.season.id === hovered.value)
  return node?.at ?? selectedAt.value
})

const percent = (at: number) => `${at * 100}%`

/** What the narrow layout says under the line, since it cannot label every node. */
const caption = computed<string>(() => {
  const node = axis.value.nodes.find(n => n.season.id === (hovered.value ?? props.selectedId))
  return node?.season.name ?? ""
})

/** Left and right arrows walk the line; the ends do not wrap, since a timeline has ends. */
const step = (from: number, by: number) => {
  const next = axis.value.nodes[from + by]
  if (!next) return
  emit("select", next.season.id)
}
</script>

<template>
  <div
    class="season-timeline relative w-full select-none"
    data-testid="esports-season-timeline"
    :style="{'--accent': accent, '--lit': percent(litTo)}"
    @mouseleave="hovered = null"
  >
    <!-- Years sit above the line, each centred over its own seasons. -->
    <div class="relative h-5">
      <span
        v-for="year in axis.years"
        :key="year.year"
        class="absolute -translate-x-1/2 font-body text-[10px] tracking-[0.18em] text-ash uppercase transition-colors duration-300 sm:text-[11px]"
        :class="{'text-chalk': litTo >= year.from && litTo <= year.to}"
        :style="{left: percent(year.at)}"
      >{{ year.year }}</span>
    </div>

    <!-- The line itself: dashed and dim, with a lit length laid over it. -->
    <div class="relative h-6">
      <span
        aria-hidden="true"
        class="season-timeline__rule absolute top-1/2 right-0 left-0 h-0.5 -translate-y-1/2"
      />
      <span
        aria-hidden="true"
        class="season-timeline__lit absolute top-1/2 left-0 h-0.5 -translate-y-1/2"
      />

      <button
        v-for="(node, index) in axis.nodes"
        :key="node.season.id"
        class="season-timeline__node absolute top-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full p-2"
        :class="{'season-timeline__node--on': node.season.id === selectedId}"
        :data-testid="`esports-season-node-${node.season.id}`"
        :style="{left: percent(node.at)}"
        type="button"
        @click="emit('select', node.season.id)"
        @focus="hovered = node.season.id"
        @keydown.left.prevent="step(index, -1)"
        @keydown.right.prevent="step(index, 1)"
        @mouseenter="hovered = node.season.id"
      >
        <span class="sr-only">{{ node.season.name }}</span>
        <span
          aria-hidden="true"
          class="season-timeline__dot block h-2 w-2 rounded-full"
        />
      </button>
    </div>

    <!--
      Which half of the year each node is. Twelve of these collide into a smear on a narrow
      screen, so below the small breakpoint they give way to one caption naming whichever
      season is under the pointer or on show.
    -->
    <div class="relative hidden h-5 sm:block">
      <span
        v-for="node in axis.nodes"
        :key="node.season.id"
        class="absolute -translate-x-1/2 font-display text-[10px] uppercase transition-colors duration-300 sm:text-xs"
        :class="node.season.id === selectedId ? 'text-chalk' : 'text-ash/70'"
        :style="{left: percent(node.at)}"
      >{{ node.half }}</span>
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
/* The dashes are drawn rather than bordered: a border-style dash cannot be sized, and this
   one has to read as a measured line rather than a hairline rule. */
.season-timeline__rule {
  background-image: repeating-linear-gradient(
    to right,
    color-mix(in oklab, var(--color-ash) 45%, transparent) 0 7px,
    transparent 7px 14px
  );
}

.season-timeline__lit {
  width: var(--lit);
  background-image: repeating-linear-gradient(
    to right,
    var(--accent) 0 7px,
    transparent 7px 14px
  );
  filter: drop-shadow(0 0 6px color-mix(in oklab, var(--accent) 55%, transparent));
  transition: width 420ms cubic-bezier(0.22, 1, 0.36, 1);
}

.season-timeline__dot {
  background-color: var(--color-ash);
  transition: background-color 260ms ease, box-shadow 260ms ease, scale 260ms ease;
}

.season-timeline__node:hover .season-timeline__dot,
.season-timeline__node:focus-visible .season-timeline__dot {
  background-color: var(--accent);
  scale: 1.5;
}

.season-timeline__node--on .season-timeline__dot {
  background-color: var(--accent);
  scale: 1.6;
  box-shadow: 0 0 0 4px color-mix(in oklab, var(--accent) 22%, transparent);
}

@media (prefers-reduced-motion: reduce) {
  .season-timeline__lit {
    transition: none;
  }

  .season-timeline__dot {
    transition: none;
  }

  /* The scale is what a node uses to say it is the one selected, so it stays; what goes is
     the movement getting there. */
}
</style>
