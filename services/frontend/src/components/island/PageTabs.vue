<script lang="ts" setup>
import {computed} from "vue"
import {useRoute} from "vue-router"

/**
 * Tabs between the pages of one section, drawn under its header. The tab marked is the one whose
 * page the reader is on or under, the closest where two cover it, so `/account` does not stay lit
 * beside Security on a security page.
 */
defineOptions({name: "PageTabs"})

const {entries, label, testid = undefined} = defineProps<{
  entries: {label: string, to: string}[]
  label: string
  testid?: string
}>()

const route = useRoute()

const current = computed<string | undefined>(() =>
  entries
    .filter(entry => route.path === entry.to || route.path.startsWith(`${entry.to}/`))
    .sort((a, b) => b.to.length - a.to.length)[0]?.to)
</script>

<template>
  <nav
    :aria-label="label"
    class="page-tabs"
  >
    <router-link
      v-for="entry in entries"
      :key="entry.to"
      :aria-current="entry.to === current ? 'page' : undefined"
      class="page-tabs__tab"
      :class="{'page-tabs__tab--on': entry.to === current}"
      :data-testid="testid ? `${testid}-${entry.label.toLowerCase()}` : undefined"
      :to="entry.to"
    >
      {{ entry.label }}
    </router-link>
  </nav>
</template>

<style scoped>
/* Sideways scroll rather than a wrap: a second row of tabs reads as a second section. */
.page-tabs {
  display: flex;
  gap: 2px;
  overflow-x: auto;
  border-bottom: 1px solid var(--color-hairline);
  scrollbar-width: none;
}

.page-tabs__tab {
  flex: none;
  padding: 0.95rem 1.25rem;
  font-family: var(--font-display);
  font-size: 0.82rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  white-space: nowrap;
  color: var(--color-ash);
  transition: color 220ms ease;
}

.page-tabs__tab:hover {
  color: var(--color-chalk);
}

.page-tabs__tab--on {
  color: var(--color-chalk);
  box-shadow: inset 0 -2px 0 var(--color-eyebrow);
}

@media (max-width: 767px) {
  .page-tabs__tab {
    padding: 0.85rem 0.9rem;
    font-size: 0.72rem;
  }
}
</style>
