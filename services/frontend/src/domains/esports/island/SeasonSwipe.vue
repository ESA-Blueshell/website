<script lang="ts" setup>
import {ref, watch} from "vue"
import BandSwipe from "@/components/island/BandSwipe.vue"
import {directionBetween, type SeasonDirection} from "./seasonAxis"
import type {Season} from "../adapters/esports"

/**
 * A season change as a pass across the screen, in the island's own band swipe.
 *
 * What is left here is the season vocabulary: which of two seasons is later is knowledge about
 * seasons, so the direction is worked out on this side and the island is handed the answer. The
 * pinned class is here too: the dark treatment is the esports bands' own, not the shared band's.
 */
defineOptions({name: "SeasonSwipe"})

const props = defineProps<{
  /**
   * The season whose contents are shown.
   *
   * The season that has *arrived*, not the one that was clicked. The strip answers a click at
   * once; the band waits, and then moves once.
   */
  season: Season | null
}>()

/**
 * Which way the page last travelled.
 *
 * Set before the swap rather than after it: the direction has to be known while the contents
 * arriving are being rendered, since it is what decides which side they arrive from.
 */
const direction = ref<SeasonDirection>("same")

let shown: Season | null = null
watch(() => props.season, (next) => {
  const to = next ?? null
  direction.value = directionBetween(shown, to)
  shown = to
})

/**
 * Which season a stop is.
 *
 * The island deals in stops: it hands back the stop whose contents it wants drawn, and says
 * nothing about what a stop is, which is the whole of why the shared band carries no season
 * vocabulary. Turning one back into a season is seasons' knowledge, so it is answered here and
 * the pages above go on being handed a season, exactly as they are today.
 *
 * There is one season to answer with for now, because there is one panel to draw. When the band
 * asks about a neighbour it will be given the seasons either side to answer from, and every page
 * above is already written as a function of whichever it gets.
 */
const seasonAt = (stop: string | number | null): Season | null =>
  (stop != null && stop === props.season?.id ? props.season : null)
</script>

<template>
  <band-swipe
    class="band-swipe--pinned"
    :direction="direction"
    :stop="season?.id ?? null"
    testid="season-swipe"
  >
    <template #default="{stop}">
      <slot :season="seasonAt(stop)" />
    </template>
  </band-swipe>
</template>
