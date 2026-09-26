<script setup lang="ts">
import {computed} from "vue"
import {useRouter} from "vue-router"
import BandHead from "@/components/island/BandHead.vue"
import CutButton from "@/components/island/CutButton.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import SliceBand from "@/components/island/SliceBand.vue"
import TeamRoster from "./TeamRoster.vue"
import {rosterGroupsOf, teamSliceOf} from "./teamSlice"
import {useGames} from "./useGames"
import {useSeasonLineup} from "./useSeasonLineup"

/**
 * Blueshell in competition on a page that is not the index: every team fielded in the newest
 * season, each named with its game and the season and opening to its line-up, and the way
 * through to the esports pages.
 *
 * The same read the index draws and the same slices a game's page draws, so none of them can
 * disagree. A team with no banner of its own is drawn on its game's. Absent while the season is
 * read and where it fielded nothing, rather than a heading over an empty band. Pinned dark, like
 * every band of game art.
 */
const router = useRouter()
const {ready, identityOf, recordOf} = useGames()
const {entries, loading, seasons, selected} = useSeasonLineup(() => null, ready)

const season = computed(() => seasons.value.find(one => one.id === selected.value) ?? null)

const teams = computed(() => entries.value.flatMap(entry => {
  const game = identityOf(entry.game)
  const slug = recordOf(entry.game)?.slug
  const href = slug ? `/competition/${slug}${season.value ? `?season=${season.value.id}` : ""}` : "/competition"
  return entry.teams.map(team => {
    const slice = teamSliceOf(team)
    return {
      team,
      said: season.value ? `${game.name} in ${season.value.name}` : game.name,
      slice: {
        ...slice,
        // A team can play several games, so its slice is named by the game as well.
        id: `${entry.game}-${team.id}`,
        href,
        accent: game.accent,
        meta: season.value ? `${game.name} · ${season.value.name}` : game.name,
        ...(slice.banner ? {} : {banner: game.banner ?? "", srcset: game.srcset, width: game.width, height: game.height}),
      },
    }
  })
}))

const slices = computed(() => teams.value.map(one => one.slice))
const held = (id: string | number) => teams.value.find(one => one.slice.id === id)
</script>

<template>
  <lead-band
    v-if="!loading && slices.length > 0"
    accent="var(--color-brand)"
    testid="home-esports"
  >
    <band-head
      eyebrow="Competitive gaming for anyone who wants to"
      heading="Blueshell in competition"
    >
      <template #heading>
        Blueshell in <span class="text-brand">competition</span>
      </template>
      <cut-button
        href="/competition"
        testid="home-esports-more"
        tone="solid"
      >
        More on competition
      </cut-button>
    </band-head>

    <template #bleed>
      <slice-band
        accent="var(--color-brand)"
        class="island-dark"
        :items="slices"
        testid-prefix="home-esports"
        @go="item => item.href && router.push(item.href)"
      >
        <template #details="{item}">
          <team-roster
            v-if="held(item.id)"
            :groups="rosterGroupsOf(held(item.id)!.team)"
          />
          <router-link
            class="slice__link"
            :data-testid="`home-esports-link-${item.id}`"
            :to="item.href ?? '/competition'"
          >
            {{ held(item.id)?.said }} →
          </router-link>
        </template>
      </slice-band>
    </template>
  </lead-band>
</template>
