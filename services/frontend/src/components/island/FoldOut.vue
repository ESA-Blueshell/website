<script lang="ts" setup>
/* Work that opens in place over a list rather than in a dialog, so the list stays in view. */
const {label, testid = undefined} = defineProps<{
  label: string
  testid?: string
}>()

const open = defineModel<boolean>("open", {default: false})
</script>

<template>
  <section
    class="fold-out"
    :class="{'fold-out--open': open}"
    :data-testid="testid"
  >
    <button
      :aria-expanded="open"
      class="fold-out__head"
      :data-testid="testid ? `${testid}-toggle` : undefined"
      type="button"
      @click="open = !open"
    >
      <span>{{ label }}</span>
      <svg
        aria-hidden="true"
        fill="none"
        stroke="currentColor"
        stroke-width="1.6"
        viewBox="0 0 24 24"
      >
        <path :d="open ? 'm6 14.5 6-6 6 6' : 'm6 9.5 6 6 6-6'" />
      </svg>
    </button>
    <div
      v-if="open"
      class="fold-out__body"
    >
      <slot />
    </div>
  </section>
</template>

<style scoped>
.fold-out {
  background-color: var(--band-ground);
  border-left: 3px solid transparent;
}

.fold-out--open {
  border-left-color: var(--color-brand);
}

.fold-out__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 0.9rem 1.1rem;
  border: 0;
  background: none;
  color: var(--color-chalk);
  font-family: "Shellhouse One", sans-serif;
  font-size: 0.95rem;
  text-transform: uppercase;
  cursor: pointer;
}

.fold-out__head svg {
  width: 16px;
  height: 16px;
}

.fold-out__body {
  padding: 0 1.1rem 1.2rem;
}
</style>
