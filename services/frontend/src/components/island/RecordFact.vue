<script lang="ts" setup>
/** One fact in a record head's row: a small label, and a value or a quieter line under it. */
defineOptions({name: "RecordFact"})

const {label, quiet = false} = defineProps<{
  label: string
  /** Drawn as a sentence rather than as a value, for a fact that says there is none. */
  quiet?: boolean
}>()
</script>

<template>
  <div class="record-fact">
    <p class="record-fact__label">
      {{ label }}
    </p>
    <p :class="quiet ? 'record-fact__sub' : 'record-fact__value'">
      <slot />
    </p>
  </div>
</template>

<style scoped>
.record-fact {
  position: relative;
  min-width: 0;
  padding-inline: 1.25rem;
}

.record-fact::before {
  content: "";
  position: absolute;
  top: 0.2rem;
  bottom: 0.2rem;
  left: 0;
  width: 1px;
  background-color: var(--color-hairline);
  transform: skewX(-12deg);
}

.record-fact:first-child {
  padding-inline-start: 0;
}

.record-fact:first-child::before {
  display: none;
}

.record-fact__label {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.record-fact__value {
  margin-top: 0.45rem;
  font-family: var(--font-display);
  font-size: 1.05rem;
  line-height: 1.2;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.record-fact__value :deep(a) {
  color: inherit;
}

.record-fact__value :deep(a:hover) {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.record-fact__sub {
  margin-top: 0.35rem;
  font-size: 0.9rem;
  line-height: 1.4;
  color: var(--color-ash);
}
</style>
