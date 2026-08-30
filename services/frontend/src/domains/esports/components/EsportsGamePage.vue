<script lang="ts" setup>
import {computed, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Motion} from "motion-v"
import EsportsIsland from "@/domains/esports/island/EsportsIsland.vue"
import SeasonTimeline from "@/domains/esports/island/SeasonTimeline.vue"
import SeasonSwipe from "@/domains/esports/island/SeasonSwipe.vue"
import SeasonDialog from "@/domains/esports/island/SeasonDialog.vue"
import GameDialog from "@/domains/esports/island/GameDialog.vue"
import {useMayEditEsports} from "@/domains/esports/island/useMayEditEsports"
import {type EsportsImage} from "../adapters/esports"
import {sizeOf, srcsetOf} from "../pictures"
import BannerSlices from "@/domains/esports/island/BannerSlices.vue"
import LineupEditor from "@/domains/esports/island/LineupEditor.vue"
import JoinBand from "@/domains/esports/island/JoinBand.vue"
import $markdownToHtml from "@/plugins/markdownToHtml.ts"
import {seasonInRoute} from "@/domains/esports/island/seasonInRoute"
import {useGames} from "@/domains/esports/island/useGames"
import {useSeasons} from "@/domains/esports/island/useSeasons"
import {newestSeason, seasonsIncluding} from "@/domains/esports/island/seasonAxis"
import {useMotionAllowed} from "@/domains/esports/island/useMotionAllowed"
import {useEsportsPage} from "../composables/useEsportsPage"
import type {Game, Season} from "../adapters/esports"

defineOptions({name: "EsportsGamePage"})

const props = defineProps<{game: Game}>()

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

// The season lives in the url, so a roster can be linked to and the back button works.
const rememberSeason = (id: number) => {
  void router.replace({query: {...route.query, season: String(id)}})
}

const {page, loading, teams, seasons, season, chosen, hasRosters, showSeason, reload} = useEsportsPage(
  props.game,
  seasonFromRoute,
  rememberSeason,
)

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

// A team's own two pictures: the banner behind its slice and the icon beside its name. A team
// nobody has given either to draws neither, and the slice reads on the game's accent instead.
const slices = computed(() => teams.value.map(team => ({
  id: team.id,
  title: team.name,
  meta: `${team.members.length} on the roster`,
  banner: team.banner?.url ?? "",
  srcset: srcsetOf(team.banner),
  ...sizeOf(team.banner),
  icon: team.icon?.url ?? null,
  iconSrcset: srcsetOf(team.icon),
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

/**
 * The newest season this game was actually fielded in, where the one on show is not it.
 *
 * A page that opens on a season this game sat out has nothing to show and, without this,
 * nothing to offer either. The seasons it played are part of its own answer, so naming the
 * last of them costs nothing and turns an empty page into a way back to a full one.
 */
const lastPlayed = computed<Season | null>(() => {
  const played = newestSeason(seasons.value)
  return played && played.id !== season.value?.id ? played : null
})
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
  banner: EsportsImage | null
  icon: EsportsImage | null
} | null>(null)
const lineupOpen = ref(false)

const editLineup = (teamId: number | string) => {
  const team = teams.value.find(one => one.id === teamId)
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
 * from this, and reloading rebuilds the props it watches — so leaving this stale would undo
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
    <esports-island>
      <header class="relative isolate overflow-hidden">
        <div
          aria-hidden="true"
          class="pointer-events-none absolute -top-28 -left-20 h-72 w-[34rem] rounded-full opacity-30 blur-[90px]"
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
            is the only logo here — a team carries one only once somebody uploads it, so without
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
        <season-timeline
          :accent="identity.accent"
          :may-edit="mayEdit"
          :seasons="stripSeasons"
          :selected-id="chosen"
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
        <season-swipe :season="season">
          <!--
            Only while there is nothing to show. A season switch has the season before it on
            screen and keeps it there until the next answer lands: swapping the band for a
            pulsing block and back again is the blink, and it says nothing a visitor needs.
          -->
          <div
            v-if="loading && !hasRosters"
            class="flex min-h-[22rem] w-full animate-pulse bg-surface motion-reduce:animate-none"
            data-testid="esports-loading"
          />

          <div
            v-else-if="!hasRosters"
            class="flex min-h-[22rem] w-full flex-col items-center justify-center gap-2 bg-surface px-5 text-center font-body text-sm text-ash"
            data-testid="esports-empty"
          >
            <p>No teams recorded for {{ season?.name ?? "this season" }} yet.</p>
            <!--
              A page opening on a season this game sat out is not a dead end: the last season
              it did play is one click away, and named so the click is worth making.
            -->
            <router-link
              v-if="lastPlayed"
              class="team-slice__link"
              data-testid="esports-empty-last-played"
              :to="`${route.path}?season=${lastPlayed.id}`"
            >
              {{ identity.name }} last played {{ lastPlayed.name }} →
            </router-link>
          </div>

          <!--
            The band is rebuilt on a season change, because the change is now something the
            visitor watches happen — but which team they were reading is carried across it, so
            the movement is the season travelling and not the subject changing under them.
            Within one season the band still updates in place, and only what changed moves.
          -->
          <Motion
            v-else
            v-bind="entrance(0)"
          >
            <banner-slices
              :accent="identity.accent"
              add-label="Add a team"
              :items="slices"
              :may-add="mayEdit"
              :may-edit="mayEdit"
              :open-id="justAdded ?? carried"
              testid-prefix="team-roster"
              @add="addingTeam = true"
              @edit="editLineup"
              @open="id => carried = id == null ? null : Number(id)"
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

/*
 * The banner is carried to nothing before the header ends.
 *
 * Drawn across the header's whole box it stopped at that box's edge, which put a hard line
 * across the page exactly where the header meets the season strip — and the darker the
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
