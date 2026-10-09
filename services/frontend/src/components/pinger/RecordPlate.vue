<script lang="ts" setup>
import {computed} from "vue"
import {compactRate, setAt, type CombinedRecord} from "@/domains/pinger"

/**
 * The combined record: the top rate every sender reached together, SiteCie included, and when,
 * beside the combined rate right now and how close it runs to the record.
 */
defineOptions({name: "RecordPlate"})

const {record = null, combinedPps} = defineProps<{
  record?: CombinedRecord | null
  combinedPps: number
}>()

/** How far the rate right now runs towards the record, as a share capped at the whole bar. */
const share = computed<number>(() => (record && record.pps > 0 ? Math.min(1, combinedPps / record.pps) : 0))
</script>

<template>
  <div
    class="plate"
    data-testid="snt-record"
  >
    <div class="plate__part">
      <p class="plate__eyebrow">
        Record
      </p>
      <p
        v-if="record"
        class="plate__line"
        data-testid="snt-record-best"
      >
        <b>{{ compactRate(record.pps) }}</b> pings a second<span class="plate__when">, set {{ setAt(record.at) }}</span>
      </p>
      <p
        v-else
        class="plate__line plate__line--quiet"
        data-testid="snt-record-none"
      >
        No record set yet
      </p>
    </div>
    <div class="plate__part">
      <p class="plate__eyebrow">
        Right now
      </p>
      <p
        class="plate__line"
        data-testid="snt-record-now"
      >
        <b>{{ compactRate(combinedPps) }}</b> pings a second
      </p>
      <span
        v-if="record"
        class="plate__meter"
      >
        <span
          class="plate__fill"
          :style="{width: `${Math.round(share * 100)}%`}"
        />
      </span>
    </div>
  </div>
</template>

<style scoped>
/* Cut on the lean and lit in acid, so the record reads as the band's banner. */
.plate {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem 2rem;
  padding: 1rem calc(0.7rem + 1.1rem);
  clip-path: polygon(0.7rem 0, 100% 0, calc(100% - 0.7rem) 100%, 0 100%);
  background:
    linear-gradient(100deg, color-mix(in oklab, var(--color-acid) 22%, transparent), transparent 70%),
    var(--band-ground);
}

.plate__part {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  min-width: 0;
}

.plate__eyebrow {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.plate__line {
  font-size: 1rem;
  color: var(--color-ash);
}

.plate__line b {
  font-family: var(--font-display);
  font-size: 1.9rem;
  font-weight: 400;
  font-variant-numeric: tabular-nums;
  color: var(--color-chalk);
}

.plate__line--quiet {
  padding-top: 0.6rem;
}

.plate__when {
  font-variant-numeric: tabular-nums;
}

.plate__meter {
  position: relative;
  width: 100%;
  max-width: 16rem;
  height: 0.5rem;
  overflow: hidden;
  background: color-mix(in oklab, var(--color-chalk) 12%, transparent);
  transform: skewX(-12deg);
}

.plate__fill {
  position: absolute;
  inset: 0 auto 0 0;
  background: var(--color-acid);
  transition: width 0.4s var(--ease-out-quint);
}

@media (prefers-reduced-motion: reduce) {
  .plate__fill {
    transition: none;
  }
}

@media (max-width: 639px) {
  .plate {
    grid-template-columns: minmax(0, 1fr);
    padding: 0.9rem 1.2rem;
  }
}
</style>
