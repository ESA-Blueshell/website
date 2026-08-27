<script lang="ts" setup>
import {computed} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Motion} from "motion-v"
import EsportsIsland from "@/domains/esports/island/EsportsIsland.vue"
import SeasonTimeline from "@/domains/esports/island/SeasonTimeline.vue"
import TeamFlipCard from "@/domains/esports/island/TeamFlipCard.vue"
import {identityOf} from "@/domains/esports/island/gameIdentity"
import {useMotionAllowed} from "@/domains/esports/island/useMotionAllowed"
import {useEsportsPage} from "../composables/useEsportsPage"
import type {Game} from "../adapters/esports"

defineOptions({name: "EsportsGamePage"})

const props = defineProps<{game: Game; title: string}>()

const route = useRoute()
const router = useRouter()
const motion = useMotionAllowed()
const identity = computed(() => identityOf(props.game))

const seasonFromRoute = () => {
  const raw = route.query.season
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(value) && value > 0 ? value : null
}

// The season lives in the url, so a roster can be linked to and the back button works.
const rememberSeason = (id: number) => {
  void router.replace({query: {...route.query, season: String(id)}})
}

const {loading, teams, seasons, season, hasRosters, showSeason} = useEsportsPage(
  props.game,
  seasonFromRoute,
  rememberSeason,
)

const currentSeasonId = computed<number | null>(() => season.value?.id ?? null)

const entrance = (index: number) => ({
  initial: motion.decorative.value ? {opacity: 0, y: 14} : {opacity: 1},
  animate: {opacity: 1, y: 0},
  transition: {
    duration: motion.duration(0.4),
    delay: motion.decorative.value ? index * 0.05 : 0,
    ease: [0.22, 1, 0.36, 1] as const,
  },
})
</script>

<template>
  <v-main>
    <esports-island>
      <header class="relative isolate overflow-hidden">
        <div
          aria-hidden="true"
          class="pointer-events-none absolute -top-28 -left-20 h-72 w-[34rem] rounded-full opacity-30 blur-[90px]"
          :style="{backgroundColor: identity.accent}"
        />
        <div class="relative mx-auto w-full max-w-6xl px-5 pt-12 pb-8 sm:px-8 sm:pt-16 sm:pb-10">
          <div class="flex items-center gap-4">
            <img
              v-if="identity.mark"
              alt=""
              class="h-10 w-10 object-contain sm:h-12 sm:w-12"
              :src="identity.mark"
            >
            <div>
              <p class="font-body text-[11px] tracking-[0.28em] text-ash uppercase">
                Blueshell Esports
              </p>
              <h1 class="font-display text-2xl leading-none uppercase sm:text-4xl">
                {{ title }}
              </h1>
            </div>
          </div>
          <div class="mt-5 max-w-2xl font-body text-sm leading-relaxed text-ash">
            <slot name="intro" />
          </div>
        </div>
      </header>

      <!-- The seasons as a line rather than a row of pills: the years read across the top,
           the halves below, and the line lights up to whichever season is under the pointer. -->
      <section
        v-if="seasons.length > 1"
        class="mx-auto w-full max-w-6xl px-8 pt-2 pb-8 sm:px-12"
        data-testid="esports-season-bar"
      >
        <season-timeline
          :accent="identity.accent"
          :seasons="seasons"
          :selected-id="currentSeasonId"
          @select="showSeason"
        />
      </section>

      <section class="mx-auto w-full max-w-6xl px-5 pb-16 sm:px-8 sm:pb-20">
        <div
          v-if="loading"
          class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3"
        >
          <div
            v-for="n in 3"
            :key="n"
            class="h-44 animate-pulse rounded-2xl bg-surface motion-reduce:animate-none"
          />
        </div>

        <p
          v-else-if="!hasRosters"
          class="py-16 text-center font-body text-sm text-ash"
          data-testid="esports-empty"
        >
          No teams recorded for this season yet.
        </p>

        <ul
          v-else
          class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3"
          data-testid="esports-team-grid"
        >
          <Motion
            v-for="(team, index) in teams"
            :key="`${currentSeasonId}-${team.id}`"
            as="li"
            v-bind="entrance(index)"
          >
            <team-flip-card
              :accent="identity.accent"
              :team="team"
            />
          </Motion>
        </ul>
      </section>
    </esports-island>
  </v-main>
</template>
