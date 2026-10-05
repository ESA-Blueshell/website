<script setup lang="ts">
import {computed} from "vue"
import {useRouter} from "vue-router"
import BandHead from "@/components/island/BandHead.vue"
import CutButton from "@/components/island/CutButton.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import SliceBand from "@/components/island/SliceBand.vue"
import SeasonTeams from "./SeasonTeams.vue"
import {lineupSliceOf} from "./lineupSlice"
import {useGames} from "./useGames"
import {useSeasonLineup} from "./useSeasonLineup"
import {BRAND_ACCENT} from "@/utils/brand"

/**
 * Blueshell in competition on a page that is not the index: the index's own band for the newest
 * season, each game opening to its teams and leading to that game in that season.
 *
 * The same read, slices and teams the index draws, so the two cannot disagree. Absent while the
 * season is read and where it fielded nothing, rather than a heading over an empty band. Pinned
 * dark, like every band of game art.
 */
const router = useRouter()
const {ready, identityOf, recordOf} = useGames()
const {entries, loading, seasons, selected} = useSeasonLineup(() => null, ready)

const season = computed(() => seasons.value.find(one => one.id === selected.value) ?? null)

const hrefOf = (game: string) => {
  const slug = recordOf(game)?.slug
  if (!slug) return "/competition"
  return season.value ? `/competition/${slug}?season=${season.value.id}` : `/competition/${slug}`
}

const slices = computed(() => entries.value.map(entry => lineupSliceOf(entry, identityOf(entry.game), hrefOf(entry.game))))
const teamsOf = (game: string | number) => entries.value.find(entry => entry.game === game)?.teams ?? []
</script>

<template>
  <lead-band
    v-if="!loading && slices.length > 0"
    testid="home-esports"
  >
    <band-head
      eyebrow="Competitive gaming for anyone who wants to"
      heading="Blueshell in esports"
    >
      <template #heading>
        Blueshell in <span class="text-brand-ink">esports</span>
      </template>
      <cut-button
        href="/competition"
        testid="home-esports-more"
        tone="solid"
      >
        More on esports
      </cut-button>
    </band-head>

    <template #bleed>
      <slice-band
        :accent="BRAND_ACCENT"
        class="island-dark"
        :items="slices"
        testid-prefix="home-esports"
        @go="item => item.href && router.push(item.href)"
      >
        <template #details="{item}">
          <season-teams
            :season="season?.name ?? ''"
            :teams="teamsOf(item.id)"
          />
          <router-link
            class="slice__link"
            :data-testid="`home-esports-link-${item.id}`"
            :to="item.href ?? '/competition'"
          >
            {{ season ? `${item.title} in ${season.name}` : `Every season of ${item.title}` }} →
          </router-link>
        </template>
      </slice-band>
    </template>
  </lead-band>
</template>
