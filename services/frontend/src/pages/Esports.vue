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
import {seasonInRoute} from "@/domains/esports/island/seasonInRoute"
import {useGames} from "@/domains/esports/island/useGames"
import {useSeasons} from "@/domains/esports/island/useSeasons"
import {useSeasonLineup, type LineupEntry} from "@/domains/esports/island/useSeasonLineup"
import {seasonStops} from "@/domains/esports/island/seasonAxis"
import {JOIN_CALL} from "@/domains/esports/island/joinCall"
import {leaveGameInSeason} from "@/domains/esports/adapters/esports"
import type {GameCode, Game, Season} from "@/domains/esports/adapters/esports"

defineOptions({name: "EsportsPage"})

const route = useRoute()
const router = useRouter()
const motion = useMotionAllowed()

// Which games exist, what each is called and the art each carries are the records' answer;
// the index keeps no list of its own.
const {ready, games, identityOf, recordOf, refresh: refreshGames} = useGames()

const urlOf = (game: string) => {
  const record = recordOf(game)
  return record ? `/esports/${record.slug}` : "/esports"
}

// The band is one read now: the api answers with the games of the shown season, and with
// the ones entered and not yet staffed where the reader may edit.
const {
  seasons, selected, chosen, entries, loading, show, reload,
  askAhead, answerFor: answerBySeason,
} = useSeasonLineup(() => seasonInRoute(route), ready)

/** The shown season, with whatever game the team was added to now among its slices. */
const seasonOnShow = computed<Season | null>(() =>
  seasons.value.find(one => one.id === selected.value) ?? null)

/**
 * What is in hand about a season, and nothing where nobody has asked yet.
 *
 * Everything the band draws goes through this rather than reaching for `entries` or `loading`
 * directly, because those are the season the page is *holding* and a panel is not necessarily
 * that season: under a finger there are two of them on screen, the one being read and the one
 * being dragged in, and the second is only ever known by season.
 *
 * Nothing and an empty answer are different: a season nobody has asked about is still loading,
 * and a season that fielded nobody is that season's answer, which is why it arrives as an answer
 * rather than as the band vanishing.
 *
 * No season at all is a stop too. A url can name a season the association never recorded, and a
 * page with no seasons has none to name: the read answers, nothing can be found to call it, and
 * the page reads its own held answer for the panel drawn for no season.
 */
const answerFor = (season: Season | null): LineupEntry[] | undefined => {
  if (season == null) return loading.value ? undefined : entries.value
  return answerBySeason(season.id)
}

/** The season's name, and nothing while the page is still finding out which season it is on. */
const nameOf = (season: Season | null) => season?.name ?? ""

/**
 * A game's own page, on the season the band is drawing.
 *
 * Somebody who chose a season and then followed a game did so because of what that game did
 * in that season, so the season goes with them. The game page reads its season from the url
 * already, which is the whole of the other end of this.
 */
const onSeason = (url: string, season: Season | null) =>
  (season == null ? url : `${url}?season=${season.id}`)

const sliceOf = (entry: LineupEntry, season: Season | null) => {
  const identity = identityOf(entry.game)
  const teams = entry.teams.length
  return {
    id: entry.game,
    href: onSeason(urlOf(entry.game), season),
    title: identity.name,
    // A game entered with nobody in it says so, because it is the board's list of what is
    // left to do and a visitor is not being shown it at all.
    meta: entry.public
      ? `${teams} team${teams === 1 ? "" : "s"} this season`
      : "no teams yet · not public",
    banner: identity.banner ?? "",
    srcset: identity.srcset,
    width: identity.width,
    height: identity.height,
    icon: identity.icon,
    iconSrcset: identity.iconSrcset,
    accent: identity.accent,
  }
}

/**
 * The one empty set of slices and the one empty answer, shared by every season that has none.
 *
 * Shared rather than made on the spot because a band reads a set it has not seen before as a
 * different season and drops everything it had measured. A panel with nothing in it handed a
 * fresh empty array every render would do that on every render.
 */
const NO_SLICES: ReturnType<typeof sliceOf>[] = []
const NO_ENTRIES: LineupEntry[] = []

/**
 * Each season's games as slices, built when that season's answer arrives and kept afterwards.
 *
 * The same identity problem as the empty set above, and under a finger it is sharper than it
 * looks: a neighbour's answer landing mid-drag would rebuild every season's slices at once, so a
 * band composed as one map of all of them would throw away what it had measured of the season
 * the visitor is actually looking at, halfway through the gesture that fetched the other one.
 *
 * Rebuilt where the answer it was drawn from is a new answer, or where the games' own records
 * have been re-read: the name, the art and the colour on a slice are the game record's, so a
 * game corrected has to be redrawn on every season it was fielded in.
 */
const built = new Map<number, {
  from: LineupEntry[]
  drawn: Game[]
  slices: ReturnType<typeof sliceOf>[]
}>()

const loadingFor = (season: Season | null) => answerFor(season) === undefined
const fieldedFor = (season: Season | null) => (answerFor(season)?.length ?? 0) > 0
const entriesFor = (season: Season | null) => answerFor(season) ?? NO_ENTRIES

const slicesFor = (season: Season | null) => {
  const answer = answerFor(season)
  if (season == null || answer == null || answer.length === 0) return NO_SLICES
  const had = built.get(season.id)
  if (had && had.from === answer && had.drawn === games.value) return had.slices
  const slices = answer.map(entry => sliceOf(entry, season))
  built.set(season.id, {from: answer, drawn: games.value, slices})
  return slices
}

const teamsOf = (game: string, season: Season | null) =>
  entriesFor(season).find(e => e.game === game)?.teams ?? []

/** Whether a visitor sees this game in the season being drawn, which the api decided. */
const isPublic = (game: string, season: Season | null) =>
  entriesFor(season).find(e => e.game === game)?.public !== false

const chooseSeason = (id: number) => {
  void router.replace({query: {...route.query, season: String(id)}})
  void show(id)
}

/**
 * The bookkeeping a committed gesture needs, which is the island's rather than this page's.
 *
 * Two things are this page's, though. The entry is pushed rather than replaced, because a swipe
 * is a navigation like any other and the back button has to return the way the finger came, which
 * a replaced entry cannot do — a hit on a node keeps replacing, as this page has always done. And
 * the read is waited on, because a gesture has already carried the screen by this point and is
 * holding the season it brought in: whether that season arrived is asked of the page rather than
 * of the read, since a read the api refused and a season that was quiet are the same answer here.
 */
const {arrival, refused, travelTo} = useSwipeArrival({
  inRoute: () => seasonInRoute(route),
  following: () => chosen.value,
  reach: async (id) => {
    void router.push({query: {...route.query, season: String(id)}})
    await show(id).catch(() => undefined)
    return selected.value === id
  },
})

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

/** The strip is about stops on a line; which of them is a season is this page's knowledge. */
const stripStops = computed(() => seasonStops(stripSeasons.value))
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
  seasons.value = seasons.value.filter(one => one.id !== gone.id)
  const next = stripSeasons.value[0] ?? null
  if (next) await show(next.id)
  else await reload()
}

/** The game being corrected, from the slice it is shown on. */
const editingGame = ref<Game | null>(null)
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

const addingGame = ref(false)
/** The game just put into the season, which is the slice to look at. */
const justAdded = ref<GameCode | null>(null)

/** Which games are already in the shown season, so the picker does not offer them again. */
const gamesInSeason = computed<GameCode[]>(() => entries.value.map(entry => entry.game))

const gameEntered = async (game: GameCode) => {
  await reload(selected.value ?? undefined)
  justAdded.value = game
}

/**
 * The game whose slice is open, held here because the band that holds it does not outlive a
 * season change. Handed to whichever band comes next, so somebody reading about Valorant in
 * one season is reading about Valorant in the next, where it was fielded in that one.
 */
const carried = ref<GameCode | null>(null)

/**
 * Taking a game out of the shown season.
 *
 * Its own act rather than something that happens when the last team is dropped: "we entered
 * this and fielded nobody" is a fact worth keeping, and correcting it is a decision. Refused
 * while teams are still in it, and the reason says so.
 */
const dropFailure = ref<string | null>(null)

const takeOut = async (game: GameCode, season: Season | null) => {
  if (season == null) return
  dropFailure.value = null
  const result = await leaveGameInSeason(season.id, game)
  if (!result.ok) {
    dropFailure.value = result.reason
    return
  }
  await reload(season.id)
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
    <island testid="esports-island">
      <header class="island-header relative isolate overflow-hidden">
        <div
          aria-hidden="true"
          class="island-header__blob pointer-events-none absolute -top-32 -left-24 h-80 w-[36rem] rounded-full bg-brand opacity-[0.18] blur-[90px]"
        />
        <div class="relative mx-auto w-full max-w-6xl px-5 pt-7 pb-6 sm:px-8 sm:pt-9 sm:pb-7">
          <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
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
        <timeline
          accent="var(--color-brand)"
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
        <!--
          Drawn for the season the swipe hands back rather than for the one the page holds. The
          two are the same season today and one panel is drawn; a page that read its own held
          season here would look right until the day it is asked for two.
        -->
        <!--
          The seasons a finger may reach are the strip's own, and both neighbours are asked about
          the moment a gesture begins: a season is read one season at a time, so the one being
          dragged in does not exist until somebody asks for it, and the travel of the gesture is
          what hides the round trip.
        -->
        <season-swipe
          :refused="refused"
          :season="seasonOnShow"
          :seasons="stripSeasons"
          @reaching="ids => ids.forEach(askAhead)"
          @travel="travelTo"
        >
          <template #default="{season}">
            <!--
              Only while there is nothing to show: a season switch keeps the band it has until
              the next answer lands, rather than blinking through a pulsing block.
            -->
            <div
              v-if="loadingFor(season) && !fieldedFor(season)"
              class="flex min-h-[22rem] w-full animate-pulse bg-surface motion-reduce:animate-none"
              data-testid="esports-index-loading"
            />

            <!--
              A visitor is told and there is nothing for them to do about it. A reader who may
              edit is told in the band itself, in a slice with the way in beside it, so the two
              are one row rather than a notice stacked over a band.
            -->
            <p
              v-else-if="!fieldedFor(season) && !mayEdit"
              class="flex min-h-[22rem] w-full items-center justify-center bg-surface text-center font-body text-sm text-ash"
              data-testid="esports-index-empty"
            >
              No teams were fielded in {{ nameOf(season) || "this season" }}.
            </p>

            <!--
              The band is rebuilt on a season change, because the change is now something the
              visitor watches happen. But which game they were reading is carried across it, so
              the movement is the season travelling and not the subject changing under them.
              Within one season the band still updates in place, and only what changed moves.
            -->
            <Motion
              v-if="fieldedFor(season) || mayEdit"
              v-bind="entrance"
            >
              <slice-band
                accent="var(--color-brand)"
                add-label="Add a game"
                :empty-label="`No games ran in ${nameOf(season) || 'this season'} yet`"
                :items="slicesFor(season)"
                :may-add="mayEdit"
                :open-id="justAdded ?? carried"
                :may-edit="mayEdit"
                testid-prefix="esports-game"
                @go="item => item.href && router.push(item.href)"
                @add="addingGame = true"
                @edit="id => editGame(String(id))"
                @open="id => carried = id == null ? null : String(id)"
              >
                <template #details="{item}">
                  <!--
                    A game entered with nobody in it, which only the board is answered with. It
                    says what it is rather than reading as an empty game, and the way on is the
                    game's own page for the shown season, where a team is added.
                  -->
                  <p
                    v-if="!isPublic(String(item.id), season)"
                    class="esports-quiet"
                    :data-testid="`esports-quiet-${item.id}`"
                  >
                    Nobody is fielded in {{ item.title }} this season, so visitors do not see it
                    here yet. Add a team on its own page, or take the game out of the season.
                  </p>
                  <span class="slice__group">
                    <span class="slice__group-label">
                      {{ nameOf(season) }}
                    </span>
                    <span class="slice__entries">
                      <span
                        v-for="team in teamsOf(String(item.id), season)"
                        :key="team.id"
                        class="slice__entry"
                      >
                        <span class="slice__entry-handle">{{ team.name }}</span>
                        <span class="slice__entry-name">
                          {{ team.members.length }} on the roster
                        </span>
                      </span>
                    </span>
                  </span>
                  <router-link
                    class="slice__link"
                    :data-testid="`esports-link-${item.id}`"
                    :to="onSeason(urlOf(String(item.id)), season)"
                  >
                    {{ nameOf(season) ? `${item.title} in ${nameOf(season)}` : `Every season of ${item.title}` }} →
                  </router-link>
                  <button
                    v-if="mayEdit && !isPublic(String(item.id), season)"
                    class="esports-quiet__drop"
                    :data-testid="`esports-take-out-${item.id}`"
                    type="button"
                    @click.stop="takeOut(String(item.id), season)"
                  >
                    Take {{ item.title }} out of {{ nameOf(season) }}
                  </button>
                  <p
                    v-if="dropFailure"
                    class="esports-quiet__failure"
                    data-testid="esports-game-take-out-failure"
                    role="alert"
                  >
                    {{ dropFailure }}
                  </p>
                </template>
              </slice-band>
            </Motion>
          </template>
        </season-swipe>

        <game-dialog
          accent="var(--color-brand)"
          :game="editingGame"
          :open="gameEditorOpen"
          @removed="gameSaved"
          @saved="gameSaved"
          @update:open="gameEditorOpen = $event"
        />

        <!-- The same dialog a game is corrected in, opened on nothing: it asks first which
             kind of adding this is, and is a picker or the whole editor accordingly. -->
        <game-dialog
          accent="var(--color-brand)"
          :already-in="gamesInSeason"
          :enter-in="seasonOnShow"
          :game="null"
          :open="addingGame"
          @saved="game => gameEntered(game.code)"
          @update:open="addingGame = $event"
        />
      </section>

      <call-band v-bind="JOIN_CALL" />
    </island>
  </v-main>
</template>

<style scoped>
/* A game the board can see and a visitor cannot, marked so it does not read as a finished one. */
.esports-quiet {
  margin: 0 0 0.5rem;
  font-family: var(--font-body);
  font-size: 0.8rem;
  line-height: 1.4;
  opacity: 0.85;
}

.esports-quiet__drop {
  padding: 0;
  font-family: var(--font-body);
  font-size: 0.8rem;
  color: inherit;
  text-decoration: underline;
  cursor: pointer;
  background: none;
  border: 0;
  opacity: 0.85;
}

.esports-quiet__failure {
  margin: 0.35rem 0 0;
  font-family: var(--font-body);
  font-size: 0.8rem;
  color: var(--color-danger, #ff6b6b);
}
</style>
