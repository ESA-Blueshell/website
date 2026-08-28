<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Motion} from "motion-v"
import EsportsIsland from "@/domains/esports/island/EsportsIsland.vue"
import SeasonTimeline from "@/domains/esports/island/SeasonTimeline.vue"
import SeasonDialog from "@/domains/esports/island/SeasonDialog.vue"
import GameDialog from "@/domains/esports/island/GameDialog.vue"
import {useMayEditEsports} from "@/domains/esports/island/useMayEditEsports"
import {loadSeasons, unfieldTeamFromSeason} from "../adapters/esports"
import BannerSlices from "@/domains/esports/island/BannerSlices.vue"
import BannerDialog from "@/domains/esports/island/BannerDialog.vue"
import AddTeamDialog from "@/domains/esports/island/AddTeamDialog.vue"
import LineupEditor from "@/domains/esports/island/LineupEditor.vue"
import ConfirmDialog from "@/domains/esports/island/ConfirmDialog.vue"
import JoinBand from "@/domains/esports/island/JoinBand.vue"
import $markdownToHtml from "@/plugins/markdownToHtml.ts"
import {$require} from "@/plugins/require"
import {useGames} from "@/domains/esports/island/useGames"
import {useMotionAllowed} from "@/domains/esports/island/useMotionAllowed"
import {useEsportsPage} from "../composables/useEsportsPage"
import type {Game, Season, Team} from "../adapters/esports"

defineOptions({name: "EsportsGamePage"})

const props = defineProps<{game: Game}>()

const route = useRoute()
const router = useRouter()
const motion = useMotionAllowed()
// What the game is called, the colour it carries and its mark are its record's answer, as is
// what this page says about it.
const {identityOf, recordOf, refresh: refreshGames} = useGames()
const identity = computed(() => identityOf(props.game))
const intro = computed(() => recordOf(props.game)?.intro ?? "")

/** The same editor the index offers, reached from the page the game is on. */
const gameEditorOpen = ref(false)

const gameSaved = async () => {
  await refreshGames()
  // Its address may have moved, and this page is at the old one.
  const now = recordOf(props.game)
  if (now && now.slug !== route.params.slug) void router.replace(`/esports/${now.slug}`)
}

const seasonFromRoute = () => {
  const raw = route.query.season
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(value) && value > 0 ? value : null
}

// The season lives in the url, so a roster can be linked to and the back button works.
const rememberSeason = (id: number) => {
  void router.replace({query: {...route.query, season: String(id)}})
}

const {page, loading, teams, seasons, season, hasRosters, showSeason, reload} = useEsportsPage(
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

// The uploaded poster where there is one, and the bundled asset until then, so a team that
// has not been given a picture yet keeps the one the page has always drawn.
const slices = computed(() => teams.value.map(team => ({
  id: team.id,
  title: team.name,
  meta: `${team.members.length} on the roster`,
  banner: team.posterUrl || (team.image ? $require(`@/assets/${team.image}`) : ""),
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

/** A season that has gone takes its place on the strip with it, and the page moves to another. */
const seasonRemoved = async (gone: Season) => {
  allSeasons.value = allSeasons.value.filter(one => one.id !== gone.id)
  const current = page.value
  if (current) {
    page.value = {...current, seasons: current.seasons.filter(one => one.id !== gone.id)}
  }
  const next = (stripSeasons.value[0] ?? null)
  if (next) await reload(next.id)
  else await reload()
}

const adding = ref(false)
const bannersOpen = ref(false)

/** The banner the api resolved for this game and season, where anything was set. */
const pageBanner = computed(() => page.value?.bannerUrl ?? null)
/** The team just added, which is the one to look at when the band comes back. */
const justAdded = ref<number | null>(null)

/**
 * The season is asked about again rather than patched here: what a team looks like on the
 * page is the api's answer, roster and all, and a team just added has a roster only because
 * something was written on the other side.
 */
const teamAdded = async (team: Team) => {
  justAdded.value = team.id
  await reload(season.value?.id)
}

const editingTeam = ref<{id: number; name: string; image: string | null; posterUrl: string | null} | null>(null)
const lineupOpen = ref(false)

const editLineup = (teamId: number | string) => {
  const team = teams.value.find(one => one.id === teamId)
  if (!team) return
  editingTeam.value = {
    id: team.id,
    name: team.name,
    image: team.image ?? null,
    posterUrl: team.posterUrl ?? null,
  }
  lineupOpen.value = true
}

/**
 * What the slice shows is the api's answer, so it is asked again rather than patched here.
 *
 * The team being edited is refreshed from that answer as well. The editor reads its poster
 * from this, and reloading rebuilds the props it watches — so leaving this stale would undo
 * an upload on screen a moment after it landed.
 */
const lineupSaved = async () => {
  await reload(season.value?.id)
  const open = editingTeam.value
  if (!open) return
  const fresh = teams.value.find(one => one.id === open.id)
  if (fresh) {
    editingTeam.value = {...open, image: fresh.image ?? null, posterUrl: fresh.posterUrl ?? null}
  }
}

const dropping = ref<{id: number; name: string; players: number} | null>(null)
const dropFailure = ref<string | null>(null)
const droppingNow = ref(false)

const askToDrop = (teamId: number | string) => {
  const team = teams.value.find(one => one.id === teamId)
  if (!team) return
  dropFailure.value = null
  dropping.value = {id: team.id, name: team.name, players: team.members.length}
}

const dropQuestion = computed(() => {
  const team = dropping.value
  if (!team || !season.value) return ""
  const players = team.players === 1 ? "1 roster place" : `${team.players} roster places`
  return `${team.name} played ${season.value.name} with ${players}. Dropping it from this season `
    + "leaves the team, and the other seasons it played, as they are."
})

const dropTeamFromSeason = async () => {
  const team = dropping.value
  const seasonId = season.value?.id
  if (!team || seasonId == null || droppingNow.value) return
  droppingNow.value = true
  dropFailure.value = null
  try {
    await unfieldTeamFromSeason(team.id, seasonId)
    dropping.value = null
    await reload(seasonId)
  } catch (error) {
    const body = (error as {detail?: string; title?: string})
    dropFailure.value = body?.detail || body?.title || "The team could not be dropped."
  } finally {
    droppingNow.value = false
  }
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
        <!-- The uploaded banner where one was set; the accent wash alone until then. -->
        <img
          v-if="pageBanner"
          alt=""
          class="pointer-events-none absolute inset-0 -z-10 h-full w-full object-cover opacity-40"
          data-testid="esports-page-banner"
          :src="pageBanner"
        >
        <div
          aria-hidden="true"
          class="pointer-events-none absolute -top-28 -left-20 h-72 w-[34rem] rounded-full opacity-30 blur-[90px]"
          :style="{backgroundColor: identity.accent}"
        />
        <div class="relative mx-auto w-full max-w-6xl px-5 pt-12 pb-8 sm:px-8 sm:pt-16 sm:pb-10">
          <!-- Where the game itself is corrected: the same affordance the seasons and the
               teams below already carry. -->
          <button
            v-if="mayEdit"
            aria-label="Edit this game"
            class="game-header__edit"
            data-testid="esports-game-edit"
            type="button"
            @click="gameEditorOpen = true"
          >
            <svg
              aria-hidden="true"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              viewBox="0 0 24 24"
            >
              <path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4Z" />
            </svg>
          </button>

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
              <!-- min-h holds the line while the records answer, so the name arriving does
                   not shift the header down. -->
              <h1 class="min-h-[1em] font-display text-2xl leading-none uppercase sm:text-4xl">
                {{ identity.name }}
              </h1>
            </div>
            <button
              v-if="mayEdit"
              class="ml-auto rounded border border-ash/40 px-3 py-1.5 font-body text-xs text-ash transition-colors hover:border-current hover:text-white"
              data-testid="esports-banners-open"
              type="button"
              @click="bannersOpen = true"
            >
              Banners
            </button>
          </div>
          <div
            v-if="intro"
            class="mt-5 max-w-2xl font-body text-sm leading-relaxed text-ash"
            data-testid="esports-game-intro"
            v-html="$markdownToHtml(intro)"
          />
        </div>

        <game-dialog
          :accent="identity.accent"
          :game="recordOf(game)"
          :open="gameEditorOpen"
          @removed="router.push('/esports/competitive-scene')"
          @saved="gameSaved"
          @update:open="gameEditorOpen = $event"
        />
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
          @removed="seasonRemoved"
          @saved="seasonSaved"
          @update:open="closeEditor"
        />

        <confirm-dialog
          :accent="identity.accent"
          confirm-label="Drop from this season"
          :failure="dropFailure"
          :open="dropping !== null"
          :question="dropQuestion"
          testid="team-drop-dialog"
          title="Drop this team from the season?"
          :working="droppingNow"
          @confirm="dropTeamFromSeason"
          @update:open="dropping = $event ? dropping : null"
        />


        <add-team-dialog
          :accent="identity.accent"
          :fielded-team-ids="teams.map(one => one.id)"
          :game="game"
          :open="adding"
          :season="season"
          @added="teamAdded"
          @update:open="adding = $event"
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
            add-label="Add a team"
            :items="slices"
            :may-add="mayEdit"
            :editing-id="lineupOpen ? editingTeam?.id ?? null : null"
            :may-drop="mayEdit"
            :may-edit="mayEdit"
            :open-id="justAdded"
            testid-prefix="team-roster"
            @add="adding = true"
            @drop="askToDrop"
            @edit="editLineup"
          >
            <template #editor="{item}">
              <lineup-editor
                :accent="identity.accent"
                :open="lineupOpen && item.id === editingTeam?.id"
                :season="season"
                :team-id="editingTeam?.id ?? null"
                :team-image="editingTeam?.image ?? null"
                :team-name="editingTeam?.name ?? ''"
                :team-poster-url="editingTeam?.posterUrl ?? null"
                @removed="lineupSaved"
                @saved="lineupSaved"
                @update:open="lineupOpen = $event"
              />
            </template>
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
                    <!-- What they did in the team's own words, beside the part they played. -->
                    <span
                      v-if="member.roleTitle"
                      class="team-slice__member-role"
                    >{{ member.roleTitle }}</span>
                    <!-- Only ever present for a member who said their name may be shown. -->
                    <span
                      v-if="member.name"
                      class="team-slice__member-name"
                    >{{ member.name }}</span>
                    <!-- Written by an admin, but read on a public page, so it is sanitised. -->
                    <span
                      v-if="member.description"
                      class="team-slice__member-note"
                      v-html="$markdownToHtml(member.description)"
                    />
                  </span>
                </span>
              </span>
            </template>
          </banner-slices>
        </Motion>
      </section>

      <banner-dialog
        :accent="identity.accent"
        :game="game"
        :open="bannersOpen"
        :season="season"
        :teams="teams.map(one => ({id: one.id, name: one.name}))"
        @changed="reload(season?.id)"
        @update:open="bannersOpen = $event"
      />

      <join-band />
    </esports-island>
  </v-main>
</template>

<style scoped>
/*
  The same affordance the slices below carry: top right, no chrome, and where there is a
  pointer it waits for one. Where there is not, it stands.
*/
.game-header__edit {
  position: absolute;
  top: 18px;
  right: 20px;
  z-index: 3;
  visibility: hidden;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  background: none;
  border: 0;
  color: var(--color-chalk);
  cursor: pointer;
}

.game-header__edit svg {
  width: 23px;
  height: 23px;
}

header:hover .game-header__edit,
header:focus-within .game-header__edit {
  visibility: visible;
}

@media (hover: none) {
  .game-header__edit {
    visibility: visible;
  }
}
</style>
