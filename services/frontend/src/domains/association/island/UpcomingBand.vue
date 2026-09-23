<script setup lang="ts">
import BandHead from "@/components/island/BandHead.vue"
import CutButton from "@/components/island/CutButton.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import PosterStrip from "@/components/island/PosterStrip.vue"
import {useUpcomingEvents} from "./useUpcomingEvents"

/**
 * The events still to come, as the posters the association made for them.
 *
 * Absent while nothing is coming or while the first read is in flight, like every band that
 * would otherwise grow a heading over nothing. The strip is pinned dark: it is an image band,
 * and its chevrons, fades and feet look the same in both themes.
 */
const {posters, total, more} = useUpcomingEvents()
</script>

<template>
  <lead-band
    v-if="posters.length > 0"
    accent="#e8842a"
    class="upcoming-band"
    testid="home-upcoming"
  >
    <band-head
      :count="total"
      count-said="upcoming events"
      eyebrow="Come along"
      heading="Upcoming events"
      testid="home-upcoming-head"
    >
      <cut-button
        href="/events"
        testid="home-upcoming-all"
      >
        All upcoming events
      </cut-button>
    </band-head>

    <template #bleed>
      <poster-strip
        class="island-dark upcoming-band__strip"
        :items="posters"
        pan-back-label="Earlier events"
        pan-on-label="Later events"
        testid-prefix="home-upcoming-strip"
        @needs-more="more"
      />
    </template>
  </lead-band>
</template>

<style scoped>
/* The strip follows the head closely: it is what the head is about. */
.upcoming-band :deep(.lead-band__inner) {
  padding-bottom: 1.25rem;
}

/* The pinned strip carries its own dark ground, so the feet read the same on a light page. */
.upcoming-band__strip {
  background-color: var(--color-ground);
}
</style>
