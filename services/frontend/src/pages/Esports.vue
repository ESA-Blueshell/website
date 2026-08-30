<script lang="ts" setup>
import {computed, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Motion} from "motion-v"
import EsportsIsland from "@/domains/esports/island/EsportsIsland.vue"
import SeasonTimeline from "@/domains/esports/island/SeasonTimeline.vue"
import SeasonSwipe from "@/domains/esports/island/SeasonSwipe.vue"
import BannerSlices from "@/domains/esports/island/BannerSlices.vue"
import AddTeamDialog from "@/domains/esports/island/AddTeamDialog.vue"
import JoinBand from "@/domains/esports/island/JoinBand.vue"
import SeasonDialog from "@/domains/esports/island/SeasonDialog.vue"
import GameDialog from "@/domains/esports/island/GameDialog.vue"
import {useMayEditEsports} from "@/domains/esports/island/useMayEditEsports"
import {seasonInRoute} from "@/domains/esports/island/seasonInRoute"
import {useGames} from "@/domains/esports/island/useGames"
import {useSeasons} from "@/domains/esports/island/useSeasons"
import {useSeasonLineup} from "@/domains/esports/island/useSeasonLineup"
import {useMotionAllowed} from "@/domains/esports/island/useMotionAllowed"
import type {Game, GameRecord, Season, Team} from "@/domains/esports/adapters/esports"

defineOptions({name: "EsportsPage"})

const route = useRoute()
const router = useRouter()
const motion = useMotionAllowed()

// Which games exist, what each is called and the art each carries are the records' answer;
// the index keeps no list of its own.
const {games: allGames, fielded: playedGames, ready, identityOf, recordOf, refresh: refreshGames} = useGames()

// Every game, not only the ones still fielded. `fielded` says whether a game is offered as
// current, which is what the menu and the add-a-team dialog want; the band below is about what
// was fielded in the season on show, and a retired game still played the seasons it played.
// Asking about all of them is safe: a game that fielded nothing in the season is dropped.
const gameCodes = computed<Game[]>(() => allGames.value.map(one => one.game))
const urlOf = (game: string) => {
  const record = recordOf(game)
  return record ? `/esports/${record.slug}` : "/esports"
}

const {seasons, selected, chosen, entries, loading, fielded, show, reload} =
  useSeasonLineup(gameCodes, () => seasonInRoute(route), ready)

const seasonName = computed(() =>
  seasons.value.find(s => s.id === selected.value)?.name ?? "",
)

/**
 * A game's own page, on the season being read here.
 *
 * Somebody who chose a season and then followed a game did so because of what that game did
 * in that season, so the season goes with them. The game page reads its season from the url
 * already, which is the whole of the other end of this.
 */
const onSeason = (url: string) => (selected.value == null ? url : `${url}?season=${selected.value}`)

const slices = computed(() =>
  entries.value.map(entry => {
    const identity = identityOf(entry.game)
    const teams = entry.teams.length
    return {
      id: entry.game,
      href: onSeason(urlOf(entry.game)),
      title: identity.name,
      meta: `${teams} team${teams === 1 ? "" : "s"} this season`,
      banner: identity.banner ?? "",
      srcset: identity.srcset,
      width: identity.width,
      height: identity.height,
      accent: identity.accent,
    }
  }),
)

const teamsOf = (game: string) => entries.value.find(e => e.game === game)?.teams ?? []

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
 *
 * The whole list is already read to settle which season is newest, so the editor's strip is
 * the same answer put to a second use rather than a second read of it.
 */
const {seasons: allSeasons} = useSeasons()

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

/** The game being corrected, from the slice it is shown on. */
const editingGame = ref<GameRecord | null>(null)
const gameEditorOpen = ref(false)

const editGame = (game: string) => {
  editingGame.value = recordOf(game)
  gameEditorOpen.value = true
}

/**
 * A game corrected is a game every slice draws differently; one marked no longer fielded, or
 * removed outright, leaves the band. Re-asking is the whole of showing either.
 */
const gameSaved = async () => {
  await refreshGames()
  await reload(selected.value ?? undefined)
}

const adding = ref(false)
/** The game the team just added belongs to, which is the slice to look at. */
const justAdded = ref<Game | null>(null)

/**
 * The game whose slice is open, held here because the band that holds it does not outlive a
 * season change. Handed to whichever band comes next, so somebody reading about Valorant in
 * one season is reading about Valorant in the next — where it was fielded in that one.
 */
const carried = ref<Game | null>(null)

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
        <div class="relative mx-auto w-full max-w-6xl px-5 pt-7 pb-6 sm:px-8 sm:pt-9 sm:pb-7">
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
      <!-- No room of its own above or below: the strip is a slice of the page, and a slice
           meets the one before it. -->
      <section
        v-if="stripSeasons.length > 1"
        class="w-full"
        data-testid="esports-index-seasons"
      >
        <season-timeline
          accent="var(--color-brand)"
          :may-edit="mayEdit"
          :seasons="stripSeasons"
          :selected-id="chosen"
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

      <section class="w-full">
        <!--
          What the season holds travels as one thing, the band and the word that there is no
          band alike: a season with nothing in it is that season's answer, and it arrives the
          way an answer does rather than by the band vanishing where it stood.
        -->
        <season-swipe :season="seasonOnShow">
          <!--
            Only while there is nothing to show: a season switch keeps the band it has until
            the next answer lands, rather than blinking through a pulsing block.
          -->
          <div
            v-if="loading && !fielded"
            class="flex min-h-[22rem] w-full animate-pulse bg-surface motion-reduce:animate-none"
            data-testid="esports-index-loading"
          />

          <p
            v-else-if="!fielded"
            class="flex min-h-[22rem] w-full items-center justify-center bg-surface text-center font-body text-sm text-ash"
            data-testid="esports-index-empty"
          >
            No teams were fielded in {{ seasonName || "this season" }}.
          </p>

          <!--
            The band is rebuilt on a season change, because the change is now something the
            visitor watches happen — but which game they were reading is carried across it, so
            the movement is the season travelling and not the subject changing under them.
            Within one season the band still updates in place, and only what changed moves.
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
              :open-id="justAdded ?? carried"
              :may-edit="mayEdit"
              testid-prefix="esports-game"
              @go="item => item.href && router.push(item.href)"
              @add="adding = true"
              @edit="id => editGame(String(id))"
              @open="id => carried = id == null ? null : String(id)"
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
                  :to="onSeason(urlOf(String(item.id)))"
                >
                  {{ seasonName ? `${item.title} in ${seasonName}` : `Every season of ${item.title}` }} →
                </router-link>
              </template>
            </banner-slices>
          </Motion>
        </season-swipe>

        <game-dialog
          accent="var(--color-brand)"
          :game="editingGame"
          :open="gameEditorOpen"
          @removed="gameSaved"
          @saved="gameSaved"
          @update:open="gameEditorOpen = $event"
        />

        <add-team-dialog
          accent="var(--color-brand)"
          :fielded-games="entries.map(entry => entry.game)"
          :fielded-team-ids="alreadyFielded"
          :games="playedGames.map(one => ({game: one.game, name: one.name}))"
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
