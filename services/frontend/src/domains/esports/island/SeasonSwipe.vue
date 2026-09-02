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
 * class stays too, since the island's stylesheet hangs the band's dark treatment on it.
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
</script>

<template>
  <band-swipe
    class="season-swipe"
    :direction="direction"
    :stop="season?.id ?? null"
    testid="season-swipe"
  >
    <slot />
  </band-swipe>
</template>
