<script setup lang="ts">
import PosterStrip from "@/components/island/PosterStrip.vue"
import {useEventsOnShow} from "./useEventsOnShow"

/**
 * The events the association ran lately, as proof rather than as a claim.
 *
 * The band is absent rather than short, and absent while the read is in flight: a page that
 * grows a heading promising what goes on here and then empties it reads worse than one that
 * never promised. The composable decides how few is too few; this only draws what it is given.
 *
 * Each page names it in its own words, because "lately" means something different on a page
 * asking somebody to join than on one asking a company to sponsor.
 */
defineProps<{
  eyebrow: string
  heading: string
  testid: string
}>()

const {posters} = useEventsOnShow()
</script>

<template>
  <section
    v-if="posters.length > 0"
    class="w-full"
    :data-testid="testid"
  >
    <div class="mx-auto w-full max-w-6xl px-5 pt-10 pb-6 sm:px-8">
      <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
        {{ eyebrow }}
      </p>
      <h2 class="mt-2.5 font-display text-2xl uppercase sm:text-4xl">
        {{ heading }}
      </h2>
    </div>
    <div class="mx-auto w-full max-w-6xl px-5 pb-10 sm:px-8">
      <poster-strip
        :items="posters"
        pan-back-label="Earlier events"
        pan-on-label="Later events"
        :testid-prefix="`${testid}-strip`"
      />
    </div>
  </section>
</template>
