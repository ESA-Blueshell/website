<script lang="ts" setup>
import HeaderBand from "./HeaderBand.vue"
import LeadBand from "./LeadBand.vue"

/**
 * What an island page draws while its record is still on its way: the head with bars where the
 * words go and a band of blank plates, so the page stands at once and fills in where it will.
 * It goes inside the page's island, under whatever the page draws before its record.
 */
defineOptions({name: "PagePlaceholder"})

withDefaults(defineProps<{
  testid?: string
  /** How many plates the band holds. */
  plates?: number
}>(), {testid: "page-placeholder", plates: 3})
</script>

<template>
  <div
    aria-busy="true"
    :data-testid="testid"
  >
    <span class="sr-only">Loading</span>
    <header-band>
      <template #head>
        <div
          aria-hidden="true"
          class="placeholder"
        >
          <span class="placeholder__bar placeholder__bar--eyebrow" />
          <span class="placeholder__bar placeholder__bar--heading" />
          <span class="placeholder__bar placeholder__bar--body" />
          <span class="placeholder__bar placeholder__bar--body placeholder__bar--short" />
        </div>
      </template>
    </header-band>
    <lead-band :testid="`${testid}-band`">
      <div
        aria-hidden="true"
        class="placeholder__plates"
      >
        <span
          v-for="plate in plates"
          :key="plate"
          class="placeholder__plate"
        />
      </div>
    </lead-band>
  </div>
</template>

<style scoped>
.placeholder {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.placeholder__bar {
  display: block;
  height: 0.9rem;
  background-color: color-mix(in oklab, var(--color-chalk) 8%, transparent);
  animation: placeholder-pulse 1.4s ease-in-out infinite;
}

.placeholder__bar--eyebrow {
  width: 7rem;
  height: 0.55rem;
}

.placeholder__bar--heading {
  width: min(22rem, 70%);
  height: 2.2rem;
}

.placeholder__bar--body {
  width: min(34rem, 90%);
}

.placeholder__bar--short {
  width: min(20rem, 60%);
}

.placeholder__plates {
  --cut: 14px;

  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
  gap: 14px;
}

.placeholder__plate {
  display: block;
  height: 12rem;
  background-color: var(--color-surface);
  clip-path: polygon(var(--cut) 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
  animation: placeholder-pulse 1.4s ease-in-out infinite;
}

@keyframes placeholder-pulse {
  50% {
    opacity: 0.55;
  }
}

@media (prefers-reduced-motion: reduce) {
  .placeholder__bar,
  .placeholder__plate {
    animation: none;
  }
}
</style>
