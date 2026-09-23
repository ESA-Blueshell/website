<script setup lang="ts">
import BandHead from "@/components/island/BandHead.vue"
import PosterStrip from "@/components/island/PosterStrip.vue"
import {useEventsOnShow} from "./useEventsOnShow"

/**
 * The events the association ran lately, as proof rather than as a claim. Each poster leads
 * to its event's own page.
 *
 * The band is absent rather than short, and absent while the read is in flight: a page that
 * grows a heading promising what goes on here and then empties it reads worse than one that
 * never promised. The composable decides how few is too few; this only draws what it is given.
 *
 * Each page names it in its own words, because "lately" means something different on a page
 * asking somebody to join than on one asking a company to sponsor.
 */
const props = defineProps<{
  eyebrow: string
  heading: string
  testid: string
  /** How many events the heading counts, where the page knows; the strip shows only some. */
  count?: number
  countSaid?: string
}>()

const {posters, more} = useEventsOnShow()
</script>

<template>
  <section
    v-if="posters.length > 0"
    class="w-full"
    :data-testid="props.testid"
  >
    <band-head
      class="mx-auto w-full max-w-6xl px-5 pt-10 pb-6 sm:px-8"
      :count="props.count"
      :count-said="props.countSaid"
      :eyebrow="props.eyebrow"
      :heading="props.heading"
      :testid="props.count === undefined ? undefined : `${props.testid}-head`"
    >
      <!-- The way on, where a page has somewhere to take the reader further back. -->
      <template
        v-if="$slots.default"
        #default
      >
        <slot />
      </template>
    </band-head>
    <!-- Full width, the way the slice band it replaces ran: the art is the point. -->
    <poster-strip
      class="pb-10"
      :items="posters"
      pan-back-label="Earlier events"
      pan-on-label="Later events"
      :testid-prefix="`${props.testid}-strip`"
      @needs-more="more"
    />
  </section>
</template>
