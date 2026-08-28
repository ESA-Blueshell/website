<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Motion} from "motion-v"
import EsportsIsland from "@/domains/esports/island/EsportsIsland.vue"
import SeasonTimeline from "@/domains/esports/island/SeasonTimeline.vue"
import BannerSlices from "@/domains/esports/island/BannerSlices.vue"
import AddTeamDialog from "@/domains/esports/island/AddTeamDialog.vue"
import JoinBand from "@/domains/esports/island/JoinBand.vue"
import SeasonDialog from "@/domains/esports/island/SeasonDialog.vue"
import {useMayEditEsports} from "@/domains/esports/island/useMayEditEsports"
import {loadSeasons} from "@/domains/esports/adapters/esports"
import {identityOf} from "@/domains/esports/island/gameIdentity"
import {useSeasonLineup} from "@/domains/esports/island/useSeasonLineup"
import {useMotionAllowed} from "@/domains/esports/island/useMotionAllowed"
import {$require} from "@/plugins/require"
import {Game as GameEnum} from "@/services/api"
import type {Game, Season, Team} from "@/domains/esports/adapters/esports"

defineOptions({name: "EsportsPage"})

const route = useRoute()
const router = useRouter()
const motion = useMotionAllowed()

/**
 * The games the index offers, and where each one's own page lives. Which games exist, in what
 * order, and what is said about each becomes a record in its own right; until then this is
 * what the router already answers to.
 */
const GAMES: Array<{game: Game; name: string; url: string; banner: string}> = [
  {game: GameEnum.LEAGUE_OF_LEGENDS, name: "League of Legends", url: "/esports/league-of-legends", banner: "leagueesportsbg1.jpg"},
  {game: GameEnum.CS2, name: "CS2", url: "/esports/counter-strike-2", banner: "csgoesports2.jpg"},
  {game: GameEnum.VALORANT, name: "Valorant", url: "/esports/valorant", banner: "valorantesports1.jpg"},
  {game: GameEnum.ROCKET_LEAGUE, name: "Rocket League", url: "/esports/rocketleague", banner: "rocketleagueesports.jpg"},
  {game: GameEnum.GEOGUESSR, name: "GeoGuessr", url: "/esports/geoguessr", banner: ""},
]

const {seasons, selected, entries, loading, fielded, show, reload} = useSeasonLineup(GAMES.map(g => g.game))

const seasonName = computed(() =>
  seasons.value.find(s => s.id === selected.value)?.name ?? "",
)

const slices = computed(() =>
  entries.value.map(entry => {
    const known = GAMES.find(g => g.game === entry.game)
    const teams = entry.teams.length
    return {
      id: entry.game,
      href: known?.url,
      title: known?.name ?? entry.game,
      meta: `${teams} team${teams === 1 ? "" : "s"} this season`,
      banner: known?.banner ? $require(`@/assets/${known.banner}`) : "",
      accent: identityOf(entry.game).accent,
    }
  }),
)

const teamsOf = (game: string) => entries.value.find(e => e.game === game)?.teams ?? []
const urlOf = (game: string) => GAMES.find(g => g.game === game)?.url ?? "/esports"

const chooseSeason = (id: number) => {
  void router.replace({query: {...route.query, season: String(id)}})
  void show(id)
}

const entrance = {
  initial: motion.decorative.value ? {opacity: 0, y: 14} : {opacity: 1},
  animate: {opacity: 1, y: 0},
  transition: {duration: motion.duration(0.45), ease: [0.22, 1, 0.36, 1] as const},
}

const mayEdit = useMayEditEsports()

/**
 * A visitor's strip carries the seasons something was fielded in; somebody who may edit sees
 * every season, since a season has to be reachable before a team can be added to it.
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

/** A season that has gone takes its place on the strip with it, and the page moves to another. */
const seasonRemoved = async (gone: Season) => {
  allSeasons.value = allSeasons.value.filter(one => one.id !== gone.id)
  seasons.value = seasons.value.filter(one => one.id !== gone.id)
  const next = stripSeasons.value[0] ?? null
  if (next) await show(next.id)
  else await reload()
}

const adding = ref(false)
/** The game the team just added belongs to, which is the slice to look at. */
const justAdded = ref<Game | null>(null)

/** The season on show, with whatever game the team was added to now among its slices. */
const seasonOnShow = computed<Season | null>(() =>
  seasons.value.find(one => one.id === selected.value) ?? null)

/** Ids are unique across games, so one flat list says who is already playing this season. */
const alreadyFielded = computed<number[]>(() =>
  entries.value.flatMap(entry => entry.teams.map(team => team.id)))

const teamAdded = async (team: Team) => {
  await reload(selected.value ?? undefined)
  // A game that fielded nothing this season had no slice; it does now, and it is the one to
  // look at. Which game that is comes from the answer, not from what was asked for.
  justAdded.value = entries.value.find(entry => entry.teams.some(one => one.id === team.id))?.game ?? null
}

// The strip reads from this list, so writing the saved season back into it is the whole of
// showing the change.
const seasonSaved = (saved: Season) => {
  const known = seasons.value.some(one => one.id === saved.id)
  seasons.value = known
    ? seasons.value.map(one => (one.id === saved.id ? saved : one))
    : [...seasons.value, saved]
  const listed = allSeasons.value.some(one => one.id === saved.id)
  allSeasons.value = listed
    ? allSeasons.value.map(one => (one.id === saved.id ? saved : one))
    : [...allSeasons.value, saved]
  // A season nobody has seen before is the one to show, which also scrolls the strip to it.
  if (!known && !listed) void show(saved.id)
}
</script>

<template>
  <v-main>
    <esports-island>
      <header class="relative isolate overflow-hidden">
        <div
          aria-hidden="true"
          class="pointer-events-none absolute -top-32 -left-24 h-80 w-[36rem] rounded-full bg-brand/18 blur-[90px]"
        />
        <div class="relative mx-auto w-full max-w-6xl px-5 pt-12 pb-8 sm:px-8 sm:pt-16 sm:pb-10">
          <p class="font-body text-[11px] font-medium tracking-[0.3em] text-acid uppercase">
            Blueshell Esports
          </p>
          <h1 class="mt-2.5 max-w-2xl font-display text-2xl leading-[1.1] uppercase sm:text-4xl">
            Any game you want to play,<br>
            <span class="text-brand">competitively</span>
          </h1>
          <p class="mt-3 max-w-xl font-body text-sm leading-relaxed text-ash">
            The games below have teams in them right now, and that list is not a limit: bring
            enough people who want to play something else and the association will arrange it.
            Tryouts run every season, and there is a room full of people who will help you get
            better.
          </p>
        </div>
      </header>

      <!-- The same strip as a game page, one level up: it selects a season, and what shows
           below is the games the association fielded in it. -->
      <section
        v-if="stripSeasons.length > 1"
        class="w-full pt-1 pb-2"
        data-testid="esports-index-seasons"
      >
        <season-timeline
          accent="var(--color-brand)"
          :may-edit="mayEdit"
          :seasons="stripSeasons"
          :selected-id="selected"
          @add="addSeason"
          @edit="editSeason"
          @select="chooseSeason"
        />

        <season-dialog
          accent="var(--color-brand)"
          :open="editorOpen"
          :season="editing"
          @removed="seasonRemoved"
          @saved="seasonSaved"
          @update:open="closeEditor"
        />
      </section>

      <section class="w-full pb-3 sm:pb-4">
        <div
          v-if="loading"
          class="flex min-h-[22rem] w-full animate-pulse bg-surface motion-reduce:animate-none"
        />

        <p
          v-else-if="!fielded"
          class="py-16 text-center font-body text-sm text-ash"
          data-testid="esports-index-empty"
        >
          No teams were fielded in {{ seasonName || "this season" }}.
        </p>

        <!--
          Not keyed on the season: a key would rebuild the band on every switch, and the
          entrance would play again for a set of slices that is largely the same one. The
          band updates in place instead, and only what actually changed moves.
        -->
        <Motion
          v-else
          v-bind="entrance"
        >
          <banner-slices
            accent="var(--color-brand)"
            add-label="Add a game"
            :items="slices"
            :may-add="mayEdit"
            :open-id="justAdded"
            testid-prefix="esports-game"
            @go="item => item.href && router.push(item.href)"
            @add="adding = true"
          >
            <template #details="{item}">
              <span class="team-slice__group">
                <span class="team-slice__group-label">
                  {{ seasonName }}
                </span>
                <span class="team-slice__members">
                  <span
                    v-for="team in teamsOf(String(item.id))"
                    :key="team.id"
                    class="team-slice__member"
                  >
                    <span class="team-slice__handle">{{ team.name }}</span>
                    <span class="team-slice__member-name">
                      {{ team.members.length }} on the roster
                    </span>
                  </span>
                </span>
              </span>
              <router-link
                class="team-slice__link"
                :to="urlOf(String(item.id))"
              >
                Every season of {{ item.title }} →
              </router-link>
            </template>
          </banner-slices>
        </Motion>

        <add-team-dialog
          accent="var(--color-brand)"
          :fielded-games="entries.map(entry => entry.game)"
          :fielded-team-ids="alreadyFielded"
          :games="GAMES.map(one => ({game: one.game, name: one.name}))"
          :open="adding"
          :season="seasonOnShow"
          @added="teamAdded"
          @update:open="adding = $event"
        />
      </section>

      <join-band />
    </esports-island>
  </v-main>
</template>
