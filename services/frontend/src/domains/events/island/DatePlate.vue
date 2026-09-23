<script lang="ts" setup>
/**
 * What stands in for a poster nobody made: the day in the house blue, the month, and the
 * event's name at the foot, all scaled with the square so a grid of them stays readable.
 */
import {computed} from "vue"
import {DateTime} from "luxon"

defineOptions({name: "DatePlate"})

const {startTime, title} = defineProps<{startTime: string, title: string}>()

const at = computed(() => DateTime.fromISO(startTime))
</script>

<template>
  <span class="plate">
    <span class="plate__in">
      <span>
        <span class="plate__day">{{ at.toFormat("d") }}</span>
        <span class="plate__month">{{ at.toFormat("LLL") }}</span>
      </span>
      <span class="plate__title">{{ title }}</span>
    </span>
  </span>
</template>

<style scoped>
.plate {
  display: block;
  aspect-ratio: 1 / 1;
  container-type: inline-size;
  background:
    radial-gradient(120% 90% at 0 0, color-mix(in oklab, var(--color-brand) 18%, transparent), transparent 72%),
    var(--color-pit);
}

.plate__in {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 100%;
  padding: 9cqw 10cqw 10cqw;
}

.plate__day {
  display: block;
  font-family: var(--font-display);
  font-size: 27cqw;
  line-height: 0.85;
  color: var(--color-brand);
}

.plate__month {
  display: block;
  margin-top: 3cqw;
  font-size: max(0.62rem, 6cqw);
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.plate__title {
  display: -webkit-box;
  overflow: hidden;
  font-family: var(--font-display);
  font-size: max(0.8rem, 9cqw);
  line-height: 1.1;
  text-transform: uppercase;
  color: var(--color-chalk);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}
</style>
