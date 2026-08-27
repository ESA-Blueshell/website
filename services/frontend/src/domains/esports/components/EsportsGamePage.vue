<script lang="ts" setup>
import {computed} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Motion} from "motion-v"
import EsportsIsland from "@/domains/esports/island/EsportsIsland.vue"
import SeasonTimeline from "@/domains/esports/island/SeasonTimeline.vue"
import BannerSlices from "@/domains/esports/island/BannerSlices.vue"
import {$require} from "@/plugins/require"
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

/** The roster as the pages have always read it: players, then substitutes, then coaches. */
const GROUPS = [
  {role: "PLAYER", one: "Player", many: "Players"},
  {role: "SUBSTITUTE", one: "Substitute", many: "Substitutes"},
  {role: "COACH", one: "Coach", many: "Coaches"},
] as const

const rosterOf = (teamId: number) => {
  const team = teams.value.find(t => t.id === teamId)
  if (!team) return []
  return GROUPS
    .map(group => ({...group, members: team.members.filter(m => m.role === group.role)}))
    .filter(group => group.members.length > 0)
}

const slices = computed(() => teams.value.map(team => ({
  id: team.id,
  title: team.name,
  meta: `${team.members.length} on the roster`,
  banner: team.image ? $require(`@/assets/${team.image}`) : "",
})))

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
      <!-- Full width: the seasons run edge to edge, as the teams below them do. -->
      <section
        v-if="seasons.length > 1"
        class="w-full pt-2 pb-8"
        data-testid="esports-season-bar"
      >
        <season-timeline
          :accent="identity.accent"
          :seasons="seasons"
          :selected-id="currentSeasonId"
          @select="showSeason"
        />
      </section>

      <!-- Full width, edge to edge: the teams are the page, not a card grid inside it. -->
      <section class="w-full pb-16 sm:pb-20">
        <div
          v-if="loading"
          class="flex min-h-[22rem] w-full animate-pulse bg-surface motion-reduce:animate-none"
        />

        <p
          v-else-if="!hasRosters"
          class="py-16 text-center font-body text-sm text-ash"
          data-testid="esports-empty"
        >
          No teams recorded for this season yet.
        </p>

        <Motion
          v-else
          :key="currentSeasonId ?? 'none'"
          v-bind="entrance(0)"
        >
          <banner-slices
            :accent="identity.accent"
            :items="slices"
            testid-prefix="team-roster"
          >
            <template #details="{item}">
              <span
                v-for="group in rosterOf(item.id as number)"
                :key="group.role"
                class="team-slice__group"
              >
                <span class="team-slice__group-label">
                  {{ group.members.length === 1 ? group.one : group.many }}
                </span>
                <span class="team-slice__members">
                  <span
                    v-for="member in group.members"
                    :key="member.handle"
                    class="team-slice__member"
                  >
                    <span class="team-slice__handle">{{ member.handle }}</span>
                    <!-- Only ever present for a member who said their name may be shown. -->
                    <span
                      v-if="member.name"
                      class="team-slice__member-name"
                    >{{ member.name }}</span>
                  </span>
                </span>
              </span>
            </template>
          </banner-slices>
        </Motion>
      </section>
    </esports-island>
  </v-main>
</template>
