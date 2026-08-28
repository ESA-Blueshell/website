<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Motion} from "motion-v"
import EsportsIsland from "@/domains/esports/island/EsportsIsland.vue"
import SeasonTimeline from "@/domains/esports/island/SeasonTimeline.vue"
import SeasonDialog from "@/domains/esports/island/SeasonDialog.vue"
import {useMayEditEsports} from "@/domains/esports/island/useMayEditEsports"
import {loadSeasons} from "../adapters/esports"
import BannerSlices from "@/domains/esports/island/BannerSlices.vue"
import JoinBand from "@/domains/esports/island/JoinBand.vue"
import {$require} from "@/plugins/require"
import {identityOf} from "@/domains/esports/island/gameIdentity"
import {useMotionAllowed} from "@/domains/esports/island/useMotionAllowed"
import {useEsportsPage} from "../composables/useEsportsPage"
import type {Game, Season} from "../adapters/esports"

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

const {page, loading, teams, seasons, season, hasRosters, showSeason} = useEsportsPage(
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

const mayEdit = useMayEditEsports()

/**
 * A visitor's strip carries the seasons this game played in; there is nothing to say about
 * one it sat out. Somebody who may edit sees every season, because a season has to be
 * reachable before a team can be added to it — and a season just written down has nobody in
 * it by definition.
 */
const allSeasons = ref<Season[]>([])
watch(mayEdit, async (may) => {
  if (may && allSeasons.value.length === 0) allSeasons.value = await loadSeasons()
}, {immediate: true})

const stripSeasons = computed<Season[]>(() =>
  (mayEdit.value && allSeasons.value.length > 0 ? allSeasons.value : seasons.value))
const editing = ref<Season | null>(null)
const editorOpen = ref(false)

const editSeason = (season: Season) => {
  editing.value = season
  editorOpen.value = true
}

// Nothing to fill the form from: the dialog opens empty and writes a new season.
const addSeason = () => {
  editing.value = null
  editorOpen.value = true
}

const closeEditor = (open: boolean) => {
  editorOpen.value = open
}

// The strip and the labels under it both read from the loaded page, so the saved season is
// written back into it rather than fetched again.
const seasonSaved = (saved: Season) => {
  const current = page.value
  if (!current) return
  const known = current.seasons.some(one => one.id === saved.id)
  page.value = {
    ...current,
    seasons: known
      ? current.seasons.map(one => (one.id === saved.id ? saved : one))
      : [...current.seasons, saved],
    season: current.season?.id === saved.id ? saved : current.season,
  }
  const listed = allSeasons.value.some(one => one.id === saved.id)
  allSeasons.value = listed
    ? allSeasons.value.map(one => (one.id === saved.id ? saved : one))
    : [...allSeasons.value, saved]
  // A season nobody has seen before is the one to show, which also scrolls the strip to it.
  if (!known && !listed) showSeason(saved.id)
}
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
        v-if="stripSeasons.length > 1"
        class="w-full pt-1 pb-2"
        data-testid="esports-season-bar"
      >
        <season-timeline
          :accent="identity.accent"
          :may-edit="mayEdit"
          :seasons="stripSeasons"
          :selected-id="currentSeasonId"
          @add="addSeason"
          @edit="editSeason"
          @select="showSeason"
        />

        <season-dialog
          :accent="identity.accent"
          :open="editorOpen"
          :season="editing"
          @saved="seasonSaved"
          @update:open="closeEditor"
        />
      </section>

      <!-- Full width, edge to edge: the teams are the page, not a card grid inside it. -->
      <section class="w-full pb-3 sm:pb-4">
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

      <join-band />
    </esports-island>
  </v-main>
</template>
