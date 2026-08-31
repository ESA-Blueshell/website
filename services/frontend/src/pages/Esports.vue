<script lang="ts" setup>
import {computed, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Motion} from "motion-v"
import EsportsIsland from "@/domains/esports/island/EsportsIsland.vue"
import SeasonTimeline from "@/domains/esports/island/SeasonTimeline.vue"
import SeasonSwipe from "@/domains/esports/island/SeasonSwipe.vue"
import BannerSlices from "@/domains/esports/island/BannerSlices.vue"
import JoinBand from "@/domains/esports/island/JoinBand.vue"
import SeasonDialog from "@/domains/esports/island/SeasonDialog.vue"
import GameDialog from "@/domains/esports/island/GameDialog.vue"
import {useMayEditEsports} from "@/domains/esports/island/useMayEditEsports"
import {seasonInRoute} from "@/domains/esports/island/seasonInRoute"
import {useGames} from "@/domains/esports/island/useGames"
import {useSeasons} from "@/domains/esports/island/useSeasons"
import {useSeasonLineup} from "@/domains/esports/island/useSeasonLineup"
import {useMotionAllowed} from "@/domains/esports/island/useMotionAllowed"
import {leaveGameInSeason} from "@/domains/esports/adapters/esports"
import type {Game, GameRecord, Season} from "@/domains/esports/adapters/esports"

defineOptions({name: "EsportsPage"})

const route = useRoute()
const router = useRouter()
const motion = useMotionAllowed()

// Which games exist, what each is called and the art each carries are the records' answer;
// the index keeps no list of its own.
const {ready, identityOf, recordOf, refresh: refreshGames} = useGames()

const urlOf = (game: string) => {
  const record = recordOf(game)
  return record ? `/esports/${record.slug}` : "/esports"
}

// The band is one read now: the api answers with the games of the shown season, and with
// the ones entered and not yet staffed where the reader may edit.
const {seasons, selected, chosen, entries, loading, fielded, show, reload} =
  useSeasonLineup(() => seasonInRoute(route), ready)

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
  }),
)

const teamsOf = (game: string) => entries.value.find(e => e.game === game)?.teams ?? []

/** Whether a visitor sees this game in the shown season, which the api decided. */
const isPublic = (game: string) => entries.value.find(e => e.game === game)?.public !== false

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

const addingGame = ref(false)
/** The game just put into the season, which is the slice to look at. */
const justAdded = ref<Game | null>(null)

/** Which games are already in the shown season, so the picker does not offer them again. */
const gamesInSeason = computed<Game[]>(() => entries.value.map(entry => entry.game))

const gameEntered = async (game: Game) => {
  await reload(selected.value ?? undefined)
  justAdded.value = game
}

/**
 * The game whose slice is open, held here because the band that holds it does not outlive a
 * season change. Handed to whichever band comes next, so somebody reading about Valorant in
 * one season is reading about Valorant in the next — where it was fielded in that one.
 */
const carried = ref<Game | null>(null)

/** The shown season, with whatever game the team was added to now among its slices. */
const seasonOnShow = computed<Season | null>(() =>
  seasons.value.find(one => one.id === selected.value) ?? null)

/**
 * Taking a game out of the shown season.
 *
 * Its own act rather than something that happens when the last team is dropped: "we entered
 * this and fielded nobody" is a fact worth keeping, and correcting it is a decision. Refused
 * while teams are still in it, and the reason says so.
 */
const dropFailure = ref<string | null>(null)

const takeOut = async (game: Game) => {
  const season = selected.value
  if (season == null) return
  dropFailure.value = null
  const result = await leaveGameInSeason(season, game)
  if (!result.ok) {
    dropFailure.value = result.reason
    return
  }
  await reload(season)
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

          <!--
            A visitor is told and there is nothing for them to do about it. A reader who may
            edit is told in the band itself, in a slice with the way in beside it, so the two
            are one row rather than a notice stacked over a band.
          -->
          <p
            v-else-if="!fielded && !mayEdit"
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
            v-if="fielded || mayEdit"
            v-bind="entrance"
          >
            <banner-slices
              accent="var(--color-brand)"
              add-label="Add a game"
              :empty-label="`No games ran in ${seasonName || 'this season'} yet`"
              :items="slices"
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
                  game's own page for the shown season — where a team is added.
                -->
                <p
                  v-if="!isPublic(String(item.id))"
                  class="esports-quiet"
                  :data-testid="`esports-quiet-${item.id}`"
                >
                  Nobody is fielded in {{ item.title }} this season, so visitors do not see it
                  here yet. Add a team on its own page, or take the game out of the season.
                </p>
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
                  :data-testid="`esports-link-${item.id}`"
                  :to="onSeason(urlOf(String(item.id)))"
                >
                  {{ seasonName ? `${item.title} in ${seasonName}` : `Every season of ${item.title}` }} →
                </router-link>
                <button
                  v-if="mayEdit && !isPublic(String(item.id))"
                  class="esports-quiet__drop"
                  :data-testid="`esports-take-out-${item.id}`"
                  type="button"
                  @click.stop="takeOut(String(item.id))"
                >
                  Take {{ item.title }} out of {{ seasonName }}
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

        <!-- The same dialog a game is corrected in, opened on nothing: it asks first which
             kind of adding this is, and is a picker or the whole editor accordingly. -->
        <game-dialog
          accent="var(--color-brand)"
          :already-in="gamesInSeason"
          :enter-in="seasonOnShow"
          :game="null"
          :open="addingGame"
          @saved="game => gameEntered(game.game)"
          @update:open="addingGame = $event"
        />
      </section>

      <join-band />
    </esports-island>
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
