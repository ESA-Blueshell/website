<script lang="ts" setup>
import {computed, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Motion} from "motion-v"
import Island from "@/components/island/Island.vue"
import Timeline from "@/components/island/Timeline.vue"
import SliceBand from "@/components/island/SliceBand.vue"
import CallBand from "@/components/island/CallBand.vue"
import {useMotionAllowed} from "@/components/island/useMotionAllowed"
import {useSwipeArrival} from "@/components/island/useSwipeArrival"
import SeasonSwipe from "@/domains/esports/island/SeasonSwipe.vue"
import SeasonDialog from "@/domains/esports/island/SeasonDialog.vue"
import GameDialog from "@/domains/esports/island/GameDialog.vue"
import {useMayEditEsports} from "@/domains/esports/island/useMayEditEsports"
import {sizeOf, srcsetOf, type Picture} from "@/components/island/pictures"
import LineupEditor from "@/domains/esports/island/LineupEditor.vue"
import $markdownToHtml from "@/plugins/markdownToHtml.ts"
import {seasonInRoute} from "@/domains/esports/island/seasonInRoute"
import {useGames} from "@/domains/esports/island/useGames"
import {useSeasons} from "@/domains/esports/island/useSeasons"
import {newestSeason, seasonStops, seasonsIncluding} from "@/domains/esports/island/seasonAxis"
import {JOIN_CALL} from "@/domains/esports/island/joinCall"
import {useEsportsPage} from "../composables/useEsportsPage"
import type {EsportsPage, GameCode, Season, TeamRoster} from "../adapters/esports"

defineOptions({name: "EsportsGamePage"})

const props = defineProps<{game: GameCode}>()

const route = useRoute()
const router = useRouter()
const motion = useMotionAllowed()
// What the game is called and the colour it carries are its record's answer, as is what this
// page says about it.
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

const seasonFromRoute = () => seasonInRoute(route)

/**
 * The bookkeeping a committed gesture needs, which is the island's rather than this page's.
 *
 * Written above the reading it asks everything of, because the two need each other: what a
 * gesture asks of this page is a season read, and how this page writes a season to the url turns
 * on whether a gesture asked for it. Both are called long after the setup they are written in.
 *
 * The read is waited on, because a gesture has already carried the screen by this point and is
 * holding the season it brought in. Whether it arrived is asked of the page rather than of the
 * read: the sdk hands a refusal back as a body rather than throwing, so this page answers a read
 * it could not make with no season at all, and an api that answered about some other season
 * answers with that one. Either way what the gesture is waiting on is an arrival.
 */
const {arrival, asked, refused, travelTo} = useSwipeArrival({
  inRoute: seasonFromRoute,
  following: () => chosen.value,
  reach: async (id) => {
    await showSeason(id).catch(() => undefined)
    return season.value?.id === id
  },
})

/**
 * The season lives in the url, so a roster can be linked to and the back button works.
 *
 * Replaced for a season chosen on the strip, which is what this page has always done, and pushed
 * for one arrived at under a finger: a swipe is a navigation like any other and the back button
 * has to return the way the finger came, which a replaced entry cannot do.
 */
const rememberSeason = (id: number) => {
  const query = {...route.query, season: String(id)}
  if (asked.value === id) void router.push({query})
  else void router.replace({query})
}

const {
  page, loading, teams, seasons, season, chosen, showSeason, reload,
  askAhead, answerFor,
} = useEsportsPage(props.game, seasonFromRoute, rememberSeason)

/**
 * What is in hand about a season, and nothing where nobody has asked yet.
 *
 * Everything the band draws goes through this rather than reaching for `teams` or `loading`
 * directly, because those are the season the page is *holding* and a panel is not necessarily
 * that season: under a finger there are two of them on screen, the one being read and the one
 * being dragged in, and the second is only ever known by season.
 *
 * Nothing and an empty answer are different: a season nobody has asked about is still loading,
 * and a season this game sat out is that season's answer, which is why it arrives as an answer
 * rather than as the band vanishing.
 *
 * No season at all is a stop too — the page opens holding nothing — and what it draws then is
 * this page's own answer rather than a season's.
 */
const answerAbout = (shown: Season | null): EsportsPage | null | undefined => {
  if (shown == null) return loading.value ? undefined : page.value
  return answerFor(shown.id)
}

/**
 * The one empty roster, shared by every season with none.
 *
 * Shared rather than made on the spot because a band reads a set it has not seen before as a
 * different season and drops everything it had measured of the art on it.
 */
const NO_TEAMS: TeamRoster[] = []

const teamsFor = (shown: Season | null): TeamRoster[] => answerAbout(shown)?.teams ?? NO_TEAMS

/** The roster as the pages have always read it: players, then substitutes, then coaches. */
const GROUPS = [
  {role: "PLAYER", one: "Player", many: "Players"},
  {role: "SUBSTITUTE", one: "Substitute", many: "Substitutes"},
  {role: "COACH", one: "Coach", many: "Coaches"},
] as const

const rosterOf = (teamId: number, shown: Season | null) => {
  const team = teamsFor(shown).find(t => t.id === teamId)
  if (!team) return []
  return GROUPS
    .map(group => ({...group, members: team.members.filter(m => m.role === group.role)}))
    .filter(group => group.members.length > 0)
}

// A team's own two pictures: the banner behind its slice and the icon beside its name. A team
// nobody has given either to draws neither, and the slice reads on the game's accent instead.
const sliceOf = (team: TeamRoster) => ({
  id: team.id,
  title: team.name,
  meta: `${team.members.length} on the roster`,
  banner: team.banner?.url ?? "",
  srcset: srcsetOf(team.banner),
  ...sizeOf(team.banner),
  icon: team.icon?.url ?? null,
  iconSrcset: srcsetOf(team.icon),
})

const NO_SLICES: ReturnType<typeof sliceOf>[] = []

/**
 * Each season's teams as slices, built when that season's answer arrives and kept afterwards.
 *
 * The set handed to a band has to keep its identity from one render to the next: the band
 * watches the set it was given and reads a new one as a different season, dropping what it had
 * measured of the art and reconsidering which slice is open. Under a finger that matters more
 * than it looks — a neighbour's answer landing mid-drag must not rebuild the slices of the
 * season the visitor is looking at, halfway through the gesture that fetched the other one.
 *
 * Rebuilt only where the answer it was drawn from is a new answer, which a re-read after an
 * edit is: the holder is emptied by `reload`, so a corrected roster arrives as a new answer and
 * is drawn afresh.
 */
const built = new Map<number, {from: TeamRoster[]; slices: ReturnType<typeof sliceOf>[]}>()

const loadingFor = (shown: Season | null) => answerAbout(shown) === undefined
const hasRostersFor = (shown: Season | null) => teamsFor(shown).length > 0

const slicesFor = (shown: Season | null) => {
  const roster = teamsFor(shown)
  if (shown == null || roster.length === 0) return NO_SLICES
  const had = built.get(shown.id)
  if (had && had.from === roster) return had.slices
  const slices = roster.map(sliceOf)
  built.set(shown.id, {from: roster, slices})
  return slices
}

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
 * reachable before a team can be added to it, and a season just written down has nobody in
 * it by definition.
 *
 * The whole list is already read to settle which season is newest, so the editor's strip is
 * that answer put to a second use rather than a second read of it.
 */
const {seasons: allSeasons} = useSeasons()

/**
 * And the season being read is on the strip whether this game played it or not.
 *
 * The page opens on the association's newest season, which a game that has not been fielded
 * lately never played. An unlit strip would leave the visitor standing nowhere; a node for it
 * says where they are, with the seasons this game did play laid out either side of it.
 */
const stripSeasons = computed<Season[]>(() => seasonsIncluding(
  mayEdit.value && allSeasons.value.length > 0 ? allSeasons.value : seasons.value,
  season.value,
))

/** The strip is about stops on a line; which of them is a season is this page's knowledge. */
const stripStops = computed(() => seasonStops(stripSeasons.value))

/**
 * The newest season this game was actually fielded in, where the shown one is not it.
 *
 * A page that opens on a season this game sat out has nothing to show and, without this,
 * nothing to offer either. The seasons it played are part of its own answer, so naming the
 * last of them costs nothing and turns an empty page into a way back to a full one.
 */
const lastPlayedFor = (shown: Season | null): Season | null => {
  const played = newestSeason(answerAbout(shown)?.seasons ?? [])
  return played && played.id !== shown?.id ? played : null
}
const editing = ref<Season | null>(null)
const editorOpen = ref(false)

const editSeason = (id: number) => {
  editing.value = stripSeasons.value.find(one => one.id === id) ?? null
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
  // Never the season just removed: the strip carries the season being read whether it is
  // listed or not, and for one more moment that is still this one.
  const next = stripSeasons.value.find(one => one.id !== gone.id) ?? null
  if (next) await reload(next.id)
  else await reload()
}

const addingTeam = ref(false)

/** The team just added, which is the one to look at when the band comes back. */
const justAdded = ref<number | null>(null)

/**
 * The team whose slice is open, held here because the band that holds it does not outlive a
 * season change. Handed to whichever band comes next, so a team fielded in both seasons is
 * still the one being read after the page has travelled.
 */
const carried = ref<number | null>(null)

/**
 * A team added here, which the page learns about by asking again.
 *
 * Which one it is comes from the answer rather than from what was typed: the dialog writes the
 * team, fields it and its line-up in turn, and the slice to look at is the one that was not
 * there before. That holds whether it was picked out of the pool or made here.
 */
const teamMade = async () => {
  const before = new Set(teams.value.map(one => one.id))
  await reload(season.value?.id)
  justAdded.value = teams.value.find(one => !before.has(one.id))?.id ?? null
}

const editingTeam = ref<{
  id: number
  name: string
  banner: Picture | null
  icon: Picture | null
} | null>(null)
const lineupOpen = ref(false)

const editLineup = (teamId: number | string, shown: Season | null) => {
  const team = teamsFor(shown).find(one => one.id === teamId)
  if (!team) return
  editingTeam.value = {
    id: team.id,
    name: team.name,
    banner: team.banner ?? null,
    icon: team.icon ?? null,
  }
  lineupOpen.value = true
}

/**
 * What the slice shows is the api's answer, so it is asked again rather than patched here.
 *
 * The team being edited is refreshed from that answer as well. The editor reads its pictures
 * from this, and reloading rebuilds the props it watches, so leaving this stale would undo
 * an upload on screen a moment after it landed.
 */
const lineupSaved = async () => {
  await reload(season.value?.id)
  const open = editingTeam.value
  if (!open) return
  const fresh = teams.value.find(one => one.id === open.id)
  if (fresh) {
    editingTeam.value = {...open, banner: fresh.banner ?? null, icon: fresh.icon ?? null}
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
    <island testid="esports-island">
      <header class="island-header relative isolate overflow-hidden">
        <div
          aria-hidden="true"
          class="island-header__blob pointer-events-none absolute -top-28 -left-20 h-72 w-[34rem] rounded-full opacity-30 blur-[90px]"
          :style="{backgroundColor: identity.accent}"
        />
        <div class="relative mx-auto w-full max-w-6xl px-5 pt-7 pb-6 sm:px-8 sm:pt-9 sm:pb-7">
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

          <!--
            The picture behind this header went with the banners and stays gone. The game's own
            logo is a different thing: it identifies the page rather than decorating it, and it
            is the only logo here: a team carries one only once somebody uploads it, so without
            this the page a slice leads to shows nothing of the game the slice named.
          -->
          <div class="flex items-center gap-4">
            <img
              v-if="identity.icon"
              alt=""
              class="h-10 w-10 object-contain sm:h-12 sm:w-12"
              data-testid="esports-game-icon"
              sizes="48px"
              :src="identity.icon"
              :srcset="identity.iconSrcset"
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
      <!-- No room of its own above or below: the strip is a slice of the page, and a slice
           meets the one before it. -->
      <section
        v-if="stripSeasons.length > 1"
        class="w-full"
        data-testid="esports-season-bar"
      >
        <timeline
          :accent="identity.accent"
          add-label="Add a season"
          :arrival="arrival"
          :may-edit="mayEdit"
          pan-back-label="Show earlier seasons"
          pan-on-label="Show later seasons"
          :selected-id="chosen"
          :stops="stripStops"
          testid-prefix="esports-season"
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

        <!-- The same editor a line-up is corrected in, opened on nothing: it asks first which
             kind of adding this is, and is a picker or the whole form accordingly. -->
        <lineup-editor
          :accent="identity.accent"
          :already-fielded="teams.map(one => one.id)"
          :game="game"
          :open="addingTeam"
          :season="season"
          :team-id="null"
          team-name=""
          @saved="teamMade"
          @update:open="addingTeam = $event"
        />
      </section>

      <!-- Full width, edge to edge: the teams are the page, not a card grid inside it. -->
      <section class="w-full">
        <!--
          What the season holds travels as one thing, the band and the word that there is no
          band alike: a season this game sat out is still that season's answer, and it arrives
          the way an answer does rather than by the band vanishing where it stood.
        -->
        <!--
          Drawn for the season the swipe hands back rather than for the one the page holds. The
          two are the same season today and one panel is drawn; a page that read its own held
          season here would look right until the day it is asked for two. Which is why the one
          handed down is renamed on the way in: `season` is also the page's own, and the whole
          point is that what is drawn does not read that.
        -->
        <!--
          The seasons a finger may reach are the strip's own, which is how a season this game was
          never fielded in stays reachable: the strip carries the season being read whether the
          game played it or not, and the gesture offers exactly what the nodes offer. Both
          neighbours are asked about the moment a gesture begins, because a season is read one
          season at a time and the one being dragged in does not exist until somebody asks.
        -->
        <season-swipe
          :refused="refused"
          :season="season"
          :seasons="stripSeasons"
          @reaching="ids => ids.forEach(askAhead)"
          @travel="travelTo"
        >
          <template #default="{season: shown}">
            <!--
              Only while there is nothing to show. A season switch has the season before it on
              screen and keeps it there until the next answer lands: swapping the band for a
              pulsing block and back again is the blink, and it says nothing a visitor needs.
            -->
            <div
              v-if="loadingFor(shown) && !hasRostersFor(shown)"
              class="flex min-h-[22rem] w-full animate-pulse bg-surface motion-reduce:animate-none"
              data-testid="esports-loading"
            />

            <!--
              A visitor is told, and pointed at the last season this game did play. A reader who
              may edit is told in the band itself, in a slice with the way in beside it.
            -->
            <div
              v-else-if="!hasRostersFor(shown) && !mayEdit"
              class="flex min-h-[22rem] w-full flex-col items-center justify-center gap-2 bg-surface px-5 text-center font-body text-sm text-ash"
              data-testid="esports-empty"
            >
              <p>No teams recorded for {{ shown?.name ?? "this season" }} yet.</p>
              <!--
                A page opening on a season this game sat out is not a dead end: the last season
                it did play is one click away, and named so the click is worth making.
              -->
              <router-link
                v-if="lastPlayedFor(shown)"
                class="slice__link"
                data-testid="esports-empty-last-played"
                :to="`${route.path}?season=${lastPlayedFor(shown)?.id}`"
              >
                {{ identity.name }} last played {{ lastPlayedFor(shown)?.name }} →
              </router-link>
            </div>

            <!--
              The band is rebuilt on a season change, because the change is now something the
              visitor watches happen. But which team they were reading is carried across it, so
              the movement is the season travelling and not the subject changing under them.
              Within one season the band still updates in place, and only what changed moves.
            -->
            <Motion
              v-if="hasRostersFor(shown) || mayEdit"
              v-bind="entrance(0)"
            >
              <slice-band
                :accent="identity.accent"
                add-label="Add a team"
                :empty-label="`No teams played ${shown?.name ?? 'this season'} yet`"
                :items="slicesFor(shown)"
                :may-add="mayEdit"
                :may-edit="mayEdit"
                :open-id="justAdded ?? carried"
                testid-prefix="team-roster"
                @add="addingTeam = true"
                @edit="id => editLineup(id, shown)"
                @open="id => carried = id == null ? null : Number(id)"
              >
                <template #empty>
                  <router-link
                    v-if="lastPlayedFor(shown)"
                    class="slice__link"
                    data-testid="esports-empty-last-played"
                    :to="`${route.path}?season=${lastPlayedFor(shown)?.id}`"
                  >
                    {{ identity.name }} last played {{ lastPlayedFor(shown)?.name }} →
                  </router-link>
                </template>

                <template #details="{item}">
                  <span
                    v-for="group in rosterOf(item.id as number, shown)"
                    :key="group.role"
                    class="slice__group"
                  >
                    <span class="slice__group-label">
                      {{ group.members.length === 1 ? group.one : group.many }}
                    </span>
                    <span class="slice__entries">
                      <span
                        v-for="member in group.members"
                        :key="member.handle"
                        class="slice__entry"
                      >
                        <span class="slice__entry-handle">{{ member.handle }}</span>
                        <!-- What they did in the team's own words, beside the part they played. -->
                        <span
                          v-if="member.roleTitle"
                          class="slice__entry-role"
                        >{{ member.roleTitle }}</span>
                        <!-- Only ever present for a member who said their name may be shown. -->
                        <span
                          v-if="member.name"
                          class="slice__entry-name"
                        >{{ member.name }}</span>
                        <!-- Written by an admin, but read on a public page, so it is sanitised. -->
                        <span
                          v-if="member.description"
                          class="slice__entry-note"
                          v-html="$markdownToHtml(member.description)"
                        />
                      </span>
                    </span>
                  </span>
                </template>
              </slice-band>
            </Motion>
          </template>
        </season-swipe>

        <!--
          Over the page, the same as adding a team and editing a season. A line-up is a form,
          and the band rearranging itself around one read as the page coming apart rather than
          as something being filled in.
        -->
        <lineup-editor
          :accent="identity.accent"
          :game="props.game"
          :open="lineupOpen"
          :season="season"
          :team-id="editingTeam?.id ?? null"
          :team-banner="editingTeam?.banner ?? null"
          :team-icon="editingTeam?.icon ?? null"
          :team-name="editingTeam?.name ?? ''"
          @removed="lineupSaved"
          @saved="lineupSaved"
          @update:open="lineupOpen = $event"
        />
      </section>

      <call-band v-bind="JOIN_CALL" />
    </island>
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

/*
 * The banner is carried to nothing before the header ends.
 *
 * Drawn across the header's whole box it stopped at that box's edge, which put a hard line
 * across the page exactly where the header meets the season strip, and the darker the
 * picture the more it read as a panel sitting on the page rather than as the top of it.
 *
 * Masked rather than covered by a gradient: an overlay has to be the colour of whatever is
 * behind it, and what is behind it is the game's own accent wash, which is a different colour
 * on every page.
 */
.page-banner {
  -webkit-mask-image: linear-gradient(to bottom, #000 30%, transparent 100%);
  mask-image: linear-gradient(to bottom, #000 30%, transparent 100%);
}
</style>
