<script setup lang="ts">
import {computed} from "vue"
import {useRouter} from "vue-router"
import BandHead from "@/components/island/BandHead.vue"
import CutButton from "@/components/island/CutButton.vue"
import FlickReel, {type ReelItem} from "@/components/island/FlickReel.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import {reelItemOf, useCasualGames} from "@/domains/games"

/**
 * The games members play together, on the flick reel, each leading to its own page.
 *
 * The reel is pinned dark: its fades, glow and names are tuned against dark art. Archived games
 * are not on it; they wait on the casual page. An empty list hides the band, as every band does.
 */
const {live} = useCasualGames()
const router = useRouter()

const items = computed<ReelItem[]>(() => live.value.map(game => reelItemOf(game)))

const go = (item: ReelItem) => void router.push(item.href)
</script>

<template>
  <lead-band
    v-if="items.length > 0"
    accent="var(--color-acid)"
    testid="home-casual"
  >
    <band-head
      eyebrow="Casual gaming"
      heading="The games we play together"
    >
      <cut-button
        href="/casual"
        testid="home-casual-more"
      >
        All games
      </cut-button>
    </band-head>

    <template #bleed>
      <flick-reel
        class="island-dark"
        :items="items"
        pan-back-label="Previous game"
        pan-on-label="Next game"
        testid-prefix="home-casual"
        @go="go"
      />
    </template>
  </lead-band>
</template>
