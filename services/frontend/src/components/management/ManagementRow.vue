<template>
  <div
    class="mg-row"
    :class="{'mg-row--check': $slots.check}"
    :data-testid="testid"
  >
    <slot name="check" />
    <div class="mg-row__words">
      <router-link
        v-if="to"
        class="mg-row__name"
        :data-testid="testid ? `${testid}-open` : undefined"
        :to="to"
      >
        <slot name="name">
          {{ name }}
        </slot>
      </router-link>
      <p
        v-else
        class="mg-row__name"
      >
        <slot name="name">
          {{ name }}
        </slot>
      </p>
      <p
        v-if="meta || $slots.meta"
        class="mg-row__meta"
      >
        <slot name="meta">
          {{ meta }}
        </slot>
      </p>
      <p
        v-if="$slots.default"
        class="mg-row__state"
      >
        <slot />
      </p>
    </div>
    <span
      v-if="$slots.acts"
      class="mg-row__acts"
    >
      <slot name="acts" />
    </span>
  </div>
</template>

<script lang="ts" setup>
/* One record as a row: its name, one line about it, its state and its acts. A row with a page of
   its own opens it on a press anywhere. What a table's row becomes on a phone, and what a short
   list on a wider page is built from. */

const {name, to = "", meta = "", testid = undefined} = defineProps<{
  name: string
  /** The record's own page; omit for a row that opens nothing. */
  to?: string
  meta?: string
  testid?: string
}>()
</script>

<style scoped>
.mg-row {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.2rem 0.8rem;
  min-height: 4.4rem;
  padding: 0.75rem 1rem;
  color: var(--color-chalk);
  background-color: var(--band-ground);
}

.mg-row--check {
  grid-template-columns: 1.6rem minmax(0, 1fr) auto;
}

.mg-row__words {
  min-width: 0;
}

.mg-row__name {
  display: block;
  font-family: var(--font-display);
  font-size: 1rem;
  line-height: 1.15;
  text-transform: uppercase;
  overflow-wrap: anywhere;
}

.mg-row__meta {
  margin-top: 0.2rem;
  font-size: 0.84rem;
  color: var(--color-ash);
}

.mg-row__state {
  margin-top: 0.35rem;
}

/* The name's link covers the whole row; the tick and the acts stand above it to stay pressable. */
a.mg-row__name::after {
  content: "";
  position: absolute;
  inset: 0;
}

.mg-row :deep(.island-check),
.mg-row__acts {
  position: relative;
  z-index: 1;
}

.mg-row__acts {
  display: flex;
  gap: 0.35rem;
}
</style>
