<script lang="ts" setup>
/**
 * The choice at the top of a dialog that decides what the rest of it is.
 *
 * Cut on the same diagonal as the bands and the buttons, so a dialog is put together the way the
 * rest of the island is. The chosen one is filled in the association's blue and stays filled
 * while the others tint on hover, so "chosen" against "under the pointer" is a fill that stays
 * against one that arrives. A component rather than the same markup twice, both dialogs that add
 * something asking this question.
 */
defineOptions({name: "IslandChoice"})

defineProps<{
  options: Array<{key: string; label: string}>
  modelValue: string
  /** What each option's data-testid is built from, since the dialogs name them differently. */
  testidPrefix: string
}>()

const emit = defineEmits<{
  (event: "update:modelValue", key: string): void
}>()
</script>

<template>
  <div
    class="choice"
    role="radiogroup"
  >
    <button
      v-for="option in options"
      :key="option.key"
      :aria-checked="modelValue === option.key"
      class="choice__cut"
      :class="{'choice__cut--on': modelValue === option.key}"
      :data-testid="`${testidPrefix}-${option.key}`"
      role="radio"
      type="button"
      @click="emit('update:modelValue', option.key)"
    >
      <span>{{ option.label }}</span>
    </button>
  </div>
</template>

<style scoped>
.choice {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  padding-bottom: 1.1rem;
  margin-bottom: 0.35rem;
  border-bottom: 1px solid color-mix(in oklab, var(--color-chalk) 12%, transparent);
}

/*
 * The same cut the join band's buttons use, so this reads as one of the island's buttons
 * rather than as a form control that wandered in.
 */
.choice__cut {
  position: relative;
  display: inline-flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-width: 10rem;
  padding: 0.62rem 1.35rem;
  overflow: hidden;
  font-family: var(--font-display);
  font-size: 0.72rem;
  color: var(--color-chalk);
  letter-spacing: 0.06em;
  text-align: center;
  text-transform: uppercase;
  white-space: nowrap;
  cursor: pointer;
  background-color: color-mix(in oklab, var(--color-chalk) 8%, transparent);
  border: 0;
  clip-path: polygon(0.7rem 0, 100% 0, calc(100% - 0.7rem) 100%, 0 100%);
}

.choice__cut::before {
  position: absolute;
  inset: 0;
  content: "";
  background-color: var(--color-brand);
  scale: 0 1;
  transform-origin: left center;
  transition: scale 320ms cubic-bezier(0.22, 1, 0.36, 1);
}

.choice__cut > span {
  position: relative;
}

.choice__cut:hover::before,
.choice__cut:focus-visible::before {
  scale: 1 1;
}

/* Chosen, and filled in the association's own blue: a fill that stays, against one that arrives. */
.choice__cut--on {
  color: var(--color-void);
  background-color: var(--color-brand);
}

/* The one that is chosen deepens under the pointer rather than turning another colour: it is
   already the chosen one, and a second colour on top of that says nothing. */
.choice__cut--on::before {
  background-color: color-mix(in oklab, var(--color-brand) 82%, black);
}

@media (prefers-reduced-motion: reduce) {
  .choice__cut::before {
    transition: none;
  }
}
</style>
