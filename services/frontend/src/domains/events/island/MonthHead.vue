<script lang="ts" setup>
/** A month's name over the events in it, with how many there are and a rule to the edge. */
defineOptions({name: "MonthHead"})

const {name, count} = defineProps<{name: string, count: number}>()
</script>

<template>
  <div class="month">
    <h3 class="month__name">
      {{ name }}
    </h3>
    <span class="month__count">{{ count }} event{{ count === 1 ? "" : "s" }}</span>
    <span
      aria-hidden="true"
      class="month__line"
    />
  </div>
</template>

<style scoped>
.month {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.5rem 0 0.75rem;
}

.month__name {
  font-family: var(--font-display);
  font-size: 1.05rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.month__count {
  font-size: 0.8rem;
  color: var(--color-ash);
  white-space: nowrap;
}

.month__line {
  flex: 1 1 auto;
  height: 1px;
  background: var(--color-hairline);
}

@media (max-width: 767px) {
  .month {
    padding-top: 1.2rem;
  }
}
</style>
