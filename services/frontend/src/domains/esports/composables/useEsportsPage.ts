import {computed, onMounted, ref, watch} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {asksInOrder} from "../island/asksInOrder"
import {heldAnswers, type HeldAnswers} from "../island/heldAnswers"
import {useSeasons} from "../island/useSeasons"
import {loadEsportsPage, type EsportsPage, type GameCode, type Season, type TeamRoster} from "../adapters/esports"

/**
 * Each game's pages, kept by season and shared by every reading of that game.
 *
 * One holder per game rather than one keyed by both, because the season is the only thing that
 * changes while a page is open and the game is what a holder is for: the read it was made with
 * already knows its game. A visitor who has read Valorant's spring and gone back to its autumn
 * asks the api for neither a second time, and both answers exist at once, which is what a page
 * being dragged towards its neighbour needs.
 */
const byGame = new Map<GameCode, HeldAnswers<EsportsPage | null>>()

const answersFor = (game: GameCode): HeldAnswers<EsportsPage | null> => {
  let held = byGame.get(game)
  if (!held) {
    held = heldAnswers<EsportsPage | null>(seasonId => loadEsportsPage(game, seasonId))
    byGame.set(game, held)
  }
  return held
}

/**
 * One game's page: the shown season, the seasons that can be, and that season's teams.
 *
 * The season is part of the url, so a roster can be linked to and a reload keeps its place.
 * Where the url names none, the page opens on the association's newest season rather than on
 * this game's own newest — every esports page agrees about what "now" is, and a game that has
 * not been fielded lately says so instead of quietly showing an old squad as a current one.
 */
export function useEsportsPage(game: GameCode, seasonFromRoute: () => number | null, onSeason: (id: number) => void) {
  const {ready: seasonsRead, newest} = useSeasons()
  const answers = answersFor(game)

  const page = ref<EsportsPage | null>(null)
  const loading = ref<boolean>(true)
  /** The season the visitor asked for, which the strip follows before the answer arrives. */
  const chosen = ref<number | null>(null)

  const teams = computed<TeamRoster[]>(() => page.value?.teams ?? [])
  const seasons = computed<Season[]>(() => page.value?.seasons ?? [])
  const season = computed<Season | null>(() => page.value?.season ?? null)

  /**
   * Which read this page is waiting on, so a slow answer cannot land on top of a newer one —
   * see `asksInOrder`, which keeps that reasoning.
   */
  const begin = asksInOrder()

  const load = async (seasonId?: number) => {
    const wanting = begin()
    if (seasonId != null) chosen.value = seasonId
    loading.value = true
    try {
      await seasonsRead
      if (!wanting()) return
      const wanted = seasonId ?? newest.value?.id
      if (wanted != null) chosen.value = wanted
      // Where no season is named there is nothing to key an answer by: the api decides which
      // season that is, and the page cannot ask for the same one twice until it knows.
      const answer = wanted == null ? await loadEsportsPage(game) : await answers.ask(wanted)
      if (wanted == null) {
        // Read without naming a season, so it is written down under the season it turned out to
        // be about: the api chose that season, and the panel standing on it is the one that has
        // to be able to find this.
        if (answer?.season?.id != null) answers.keep(answer.season.id, answer)
      } else if (answer?.season?.id !== wanted) {
        // An answer that turned out not to be about the season it was asked for is not an answer
        // about that season, so it is not held under its name: a read the api refused comes back
        // as a body rather than as an exception, and held it would be drawn for a season it says
        // nothing about and answered with for ever after.
        answers.drop(wanted)
      }
      if (!wanting()) return
      page.value = answer
    } catch (error) {
      $handleNetworkError(error)
    } finally {
      if (wanting()) loading.value = false
    }
  }

  /**
   * A season read, unless it is the one already on show.
   *
   * Against the season *arrived on* rather than the one asked for, and the two come apart exactly
   * where it matters: a read the api would not make leaves the season chosen and another one still
   * drawn, and a visitor asking for it again — its node hit a second time, a finger swiped back to
   * it — is asking for the retry that a page which declined would never make. Asking about the
   * season already drawn still declines, which is what `reload` is for.
   */
  const showSeason = async (id: number) => {
    // The url is written whatever happens next, because the strip and the address bar are about
    // what the visitor asked for rather than about what the api managed to answer. Declining
    // before this left the two stranded: after a read that could not be reached the page draws
    // one season while `chosen` names another, and asking for the one being drawn returned in
    // silence, so the url and the lit node stayed on the season that never arrived.
    onSeason(id)
    // And the read is declined only where there is nothing to ask for: the season on the page
    // *is* the one wanted and it got there. `reload` is what re-asks about that one on purpose.
    if (id === season.value?.id && id === chosen.value) return
    await load(id)
  }

  /**
   * Asks about a season before anybody has been sent there, and does not wait for the answer.
   *
   * Nothing on screen changes yet: the answer goes into the holder and is written down where a
   * panel drawn for that season can read it, so the read has already happened by the time a
   * visitor arrives — and where it had happened already, the holder answers from what it has
   * and nothing is asked at all. A read that fails is not reported here; the arrival will ask
   * again and say so then.
   */
  const askAhead = (id: number) => {
    void answers.ask(id).catch(() => undefined)
  }

  onMounted(() => load(seasonFromRoute() ?? undefined))

  /**
   * A season chosen elsewhere — the back button, a shared link — is still a season change.
   *
   * Including a url that names no season at all, which means the newest one: it is what this page
   * opens on, and it is the same reading the board page gives a url naming no board, where the
   * board in office answers for it. Only a named season counted, a gesture's own history entry
   * could be walked back out of and leave the url naming nothing while the band went on showing
   * the season the finger had reached.
   */
  watch(seasonFromRoute, (next) => {
    const wanted = next ?? newest.value?.id ?? null
    if (wanted === chosen.value) return
    void load(wanted ?? undefined)
  })

  /**
   * `reload` re-asks about the season already shown, which `showSeason` declines to do.
   *
   * Everything held for this game is dropped first, not just this season's answer: what is
   * re-asked after a write is asked because the api's account of the game has changed, and a
   * season taken away or a team renamed is a change to what its other seasons answer too.
   *
   * Called out of date rather than dropped, so what has arrived is left where the band can still
   * read it: the band is looking at it, and emptying the holder under it would swap it for a
   * pulsing block and back again over a correction the visitor has just made. Each answer is
   * replaced as its season is read again.
   */
  const reload = async (seasonId?: number) => {
    answers.outdate()
    await load(seasonId)
  }

  return {
    page, loading, teams, seasons, season, chosen, showSeason, askAhead, reload,
    /**
     * What is in hand for a season, and nothing at all where it has not been read.
     *
     * Nothing and an empty answer are different things and the band draws them differently: a
     * season nobody has asked about yet is still loading, and a season this game sat out is that
     * season's answer. A page that could not tell them apart would draw a spinner where a season
     * should be saying the game was not fielded.
     */
    answerFor: (id: number): EsportsPage | null | undefined => answers.held(id),
  }
}
