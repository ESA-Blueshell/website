<script lang="ts" setup>
/**
 * A page for one task: the form in a narrow column, and beside it what happens and where to get
 * help. On a phone the aside follows the form.
 */
defineOptions({name: "TaskLayout"})

const {asideTitle = ""} = defineProps<{
  asideTitle?: string
}>()
</script>

<template>
  <div class="task">
    <div class="task__main">
      <slot />
    </div>
    <aside
      v-if="$slots.aside"
      class="task__aside"
    >
      <p
        v-if="asideTitle"
        class="task__aside-title"
      >
        {{ asideTitle }}
      </p>
      <slot name="aside" />
    </aside>
  </div>
</template>

<style scoped>
.task {
  display: grid;
  grid-template-columns: minmax(0, 40rem) minmax(0, 1fr);
  gap: 4rem;
  align-items: start;
  padding: 2.25rem 0 3rem;
}

.task__main {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
  min-width: 0;
}

.task__aside {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  padding: 1.3rem 1.4rem 1.4rem;
  background-color: var(--band-ground);
  font-size: 0.9rem;
  line-height: 1.55;
  color: var(--color-ash);
}

.task__aside-title {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.task__aside :deep(a) {
  color: var(--color-brand);
}

@media (max-width: 767px) {
  .task {
    grid-template-columns: minmax(0, 1fr);
    gap: 1.75rem;
    padding: 1.5rem 0 2rem;
  }
}
</style>
