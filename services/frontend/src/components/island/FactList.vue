<script lang="ts">
export interface Fact {
  label: string
  value: string
  sub?: string
  /** How full the meter under the value is, from 0 to 1. */
  share?: number
  testid?: string
}
</script>

<script lang="ts" setup>
/**
 * A few facts side by side, each a label over a value with a line under it, parted by a hairline
 * that leans with the cut. A fact with a share has a meter under its value.
 */
defineOptions({name: "FactList"})

const {facts, columns = 3} = defineProps<{
  facts: Fact[]
  columns?: 2 | 3
}>()
</script>

<template>
  <div
    class="facts"
    :class="`facts--${columns}`"
  >
    <div
      v-for="fact in facts"
      :key="fact.label"
      class="facts__one"
      :data-testid="fact.testid"
    >
      <p class="facts__label">
        {{ fact.label }}
      </p>
      <p class="facts__value">
        {{ fact.value }}
      </p>
      <p
        v-if="fact.sub"
        class="facts__sub"
      >
        {{ fact.sub }}
      </p>
      <div
        v-if="fact.share !== undefined"
        aria-hidden="true"
        class="facts__meter"
      >
        <span :style="{width: `${Math.round(fact.share * 100)}%`}" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.facts {
  display: grid;
  row-gap: 1.2rem;
}

.facts--3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.facts--2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.facts__one {
  position: relative;
  min-width: 0;
  padding-inline: 1.25rem;
}

.facts__one::before {
  content: "";
  position: absolute;
  top: 0.2rem;
  bottom: 0.2rem;
  left: 0;
  width: 1px;
  background-color: var(--color-hairline);
  transform: skewX(-12deg);
}

.facts__one:first-child {
  padding-inline-start: 0;
}

.facts__one:first-child::before {
  display: none;
}

.facts__label {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.facts__value {
  margin-top: 0.45rem;
  font-family: var(--font-display);
  font-size: 1.3rem;
  line-height: 1.2;
  text-transform: uppercase;
  overflow-wrap: break-word;
}

.facts__sub {
  margin-top: 0.25rem;
  font-size: 0.9rem;
  line-height: 1.4;
  color: var(--color-ash);
}

.facts__meter {
  position: relative;
  width: 14rem;
  max-width: 100%;
  height: 4px;
  margin-top: 0.6rem;
  overflow: hidden;
  background: color-mix(in oklab, var(--color-chalk) 12%, transparent);
}

.facts__meter > span {
  position: absolute;
  inset: 0 auto 0 0;
  background: var(--color-brand);
}

/* Two to a row, the third across the foot, so no value is squeezed to a word a line. */
@media (max-width: 767px) {
  .facts--3 {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .facts__one:nth-child(odd) {
    padding-inline-start: 0;
  }

  .facts__one:nth-child(odd)::before {
    display: none;
  }

  .facts--3 > .facts__one:nth-child(3) {
    grid-column: 1 / -1;
  }

  .facts__value {
    font-size: 1.1rem;
  }
}
</style>
