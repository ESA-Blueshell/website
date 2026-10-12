<template>
  <section
    class="mg-panel"
    :data-testid="testid"
  >
    <header class="mg-panel__head">
      <h2 class="mg-panel__title">
        {{ title }}
      </h2>
      <router-link
        v-if="to"
        class="mg-panel__link"
        :data-testid="testid ? `${testid}-link` : undefined"
        :to="to"
      >
        {{ link }}
      </router-link>
    </header>
    <slot />
  </section>
</template>

<script lang="ts" setup>
/* One area of Management summed up on the dashboard, with the way to its page. */
const {title, to = "", link = "", testid = undefined} = defineProps<{
  title: string
  to?: string
  /** What the link says; the page it opens, by name. */
  link?: string
  testid?: string
}>()
</script>

<style scoped>
.mg-panel {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  min-width: 0;
  padding: 1.2rem 1.3rem;
  background-color: var(--band-ground);
}

.mg-panel__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
}

.mg-panel__title {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.mg-panel__link {
  font-size: 0.85rem;
  color: var(--color-brand-ink);
}

.mg-panel__link:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
