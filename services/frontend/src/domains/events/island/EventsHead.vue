<script lang="ts" setup>
/** The head of an events listing: an eyebrow, the page's name, a paragraph and the ways on. */
import HeaderBand from "@/components/island/HeaderBand.vue"

defineOptions({name: "EventsHead"})

const {eyebrow, heading} = defineProps<{eyebrow: string, heading: string}>()
</script>

<template>
  <header-band>
    <template #head>
      <div class="events-head">
        <div class="events-head__words">
          <p class="events-head__eyebrow">
            {{ eyebrow }}
          </p>
          <h1 class="events-head__title">
            {{ heading }}
          </h1>
          <p class="events-head__body">
            <slot />
          </p>
        </div>
        <div class="events-head__actions">
          <slot name="actions" />
        </div>
      </div>
    </template>
  </header-band>
</template>

<style scoped>
.events-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1.5rem 2rem;
  padding-top: 1.5rem;
}

.events-head__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.events-head__title {
  margin-top: 0.7rem;
  font-family: var(--font-display);
  font-size: 4.5rem;
  line-height: 0.95;
  text-transform: uppercase;
}

.events-head__body {
  max-width: 36rem;
  margin-top: 0.9rem;
  font-size: 0.95rem;
  line-height: 1.6;
  color: var(--color-ash);
}

.events-head__body :slotted(a) {
  color: var(--color-brand);
}

.events-head__body :slotted(a:hover),
.events-head__body :slotted(a:focus-visible) {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.events-head__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

@media (max-width: 767px) {
  .events-head {
    padding-top: 0.5rem;
  }

  .events-head__title {
    font-size: 3rem;
  }
}
</style>
