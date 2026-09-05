<script setup lang="ts">
import {computed} from "vue"

/** One field of study, and how much of the membership is in it. */
export interface Field {
  label: string
  percent: number
  colour: string
}

/**
 * What our members study, as a ring.
 *
 * Rebuilt from the six numbers rather than shipped as a picture: the printed version exists as
 * a pair of images, one for each theme, which drift apart the moment a number changes and can
 * never take the page's own colours. A ring rather than a pie, because the labels are the
 * point and a ring leaves the middle for the total.
 */
const props = withDefaults(
  defineProps<{
    fields: Field[]
    testid?: string
  }>(),
  {testid: undefined},
)

const CIRCUMFERENCE = 100

/** Each slice as a dash on one circle, offset by everything before it. */
const arcs = computed(() => {
  let travelled = 0
  return props.fields.map(field => {
    const arc = {
      ...field,
      dash: `${field.percent} ${CIRCUMFERENCE - field.percent}`,
      // SVG starts a stroke at three o'clock and this starts at twelve, hence the quarter.
      offset: CIRCUMFERENCE / 4 - travelled,
    }
    travelled += field.percent
    return arc
  })
})
</script>

<template>
  <figure
    class="reach-chart"
    :data-testid="testid"
  >
    <svg
      class="reach-chart__ring"
      role="img"
      viewBox="0 0 40 40"
    >
      <title>What our members study</title>
      <circle
        class="reach-chart__track"
        cx="20"
        cy="20"
        fill="none"
        r="15.915"
        stroke-width="6"
      />
      <circle
        v-for="arc in arcs"
        :key="arc.label"
        cx="20"
        cy="20"
        fill="none"
        r="15.915"
        :stroke="arc.colour"
        :stroke-dasharray="arc.dash"
        :stroke-dashoffset="arc.offset"
        stroke-width="6"
        :transform="'rotate(-90 20 20)'"
      />
    </svg>

    <figcaption class="reach-chart__key">
      <ul>
        <li
          v-for="field in fields"
          :key="field.label"
          class="reach-chart__field"
        >
          <span
            aria-hidden="true"
            class="reach-chart__swatch"
            :style="{backgroundColor: field.colour}"
          />
          <span class="reach-chart__label">{{ field.label }}</span>
          <span class="reach-chart__percent">{{ field.percent }}%</span>
        </li>
      </ul>
    </figcaption>
  </figure>
</template>

<style scoped>
.reach-chart {
  display: grid;
  grid-template-columns: minmax(9rem, 14rem) 1fr;
  align-items: center;
  gap: 2rem;
}

.reach-chart__ring {
  width: 100%;
  /* Drawn with round ends off, so neighbouring fields meet rather than overlap. */
  stroke-linecap: butt;
}

.reach-chart__track {
  stroke: var(--color-hairline);
}

.reach-chart__key ul {
  display: grid;
  gap: 0.55rem;
}

.reach-chart__field {
  display: grid;
  grid-template-columns: 0.75rem 1fr auto;
  align-items: center;
  gap: 0.6rem;
  font-family: var(--font-body);
  font-size: 0.875rem;
  color: var(--color-chalk);
}

.reach-chart__swatch {
  width: 0.75rem;
  height: 0.75rem;
}

.reach-chart__percent {
  color: var(--color-ash);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 767px) {
  .reach-chart {
    grid-template-columns: 1fr;
    gap: 1.5rem;
  }

  .reach-chart__ring {
    max-width: 12rem;
    margin-inline: auto;
  }
}
</style>
