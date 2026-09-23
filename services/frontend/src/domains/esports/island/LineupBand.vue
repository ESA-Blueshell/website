<script setup lang="ts">
import {computed} from "vue"
import {useRouter} from "vue-router"
import BandHead from "@/components/island/BandHead.vue"
import CutButton from "@/components/island/CutButton.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import SliceBand from "@/components/island/SliceBand.vue"
import {lineupSliceOf} from "./lineupSlice"
import {useGames} from "./useGames"
import {useSeasonLineup} from "./useSeasonLineup"

/**
 * Blueshell's Esports on a page that is not the index: the games fielded in the newest season,
 * each with how many teams it has, and the way through to the esports pages.
 *
 * The same read and the same slices the index draws, so the two cannot disagree. Absent while
 * the season is read and where it fielded nothing, rather than a heading over an empty band.
 * Pinned dark, like every band of game art.
 */
const router = useRouter()
const {ready, identityOf, recordOf} = useGames()
const {entries, loading} = useSeasonLineup(() => null, ready)

const slices = computed(() => entries.value.map(entry => {
  const record = recordOf(entry.game)
  return lineupSliceOf(entry, identityOf(entry.game), record ? `/esports/${record.slug}` : "/esports")
}))
</script>

<template>
  <lead-band
    v-if="!loading && slices.length > 0"
    accent="var(--color-brand)"
    testid="home-esports"
  >
    <band-head
      eyebrow="Competitive gaming for anyone who wants to"
      heading="Blueshell's Esports"
    >
      <template #heading>
        Blueshell's <span class="text-brand">Esports</span>
      </template>
      <cut-button
        href="/esports"
        testid="home-esports-more"
        tone="solid"
      >
        More on esports
      </cut-button>
    </band-head>

    <template #bleed>
      <slice-band
        accent="var(--color-brand)"
        class="island-dark"
        :items="slices"
        short
        testid-prefix="home-esports"
        @go="item => item.href && router.push(item.href)"
      >
        <template #details="{item}">
          <router-link
            class="slice__link"
            :data-testid="`home-esports-link-${item.id}`"
            :to="item.href ?? '/esports'"
          >
            {{ item.title }} this season →
          </router-link>
        </template>
      </slice-band>
    </template>
  </lead-band>
</template>
