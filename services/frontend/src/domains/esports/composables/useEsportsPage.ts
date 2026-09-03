import {computed, onMounted, ref, shallowRef, watch, type ShallowRef} from "vue"
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
interface GameAnswers {
  answers: HeldAnswers<number, EsportsPage | null>
  /**
   * The same answers again, where a template can watch them arrive.
   *
   * The holder is a plain module and keeps a plain map, which is the whole of why it can be
   * proved without a browser — but a panel drawn for a season nobody has navigated to has to
   * redraw itself the moment that season's answer lands, and a plain map says nothing when it is
   * written to. So what has arrived is mirrored here, and the band reads this while the holder
   * goes on deciding what is worth reading and what is already in flight.
   *
   * Shallow, and replaced rather than written into: a band watches the set of teams it was given
   * and takes a new one for a new season, so the answers are handed out by identity and making
   * the rosters inside them reactive would buy nothing and cost a proxy per player.
   */
  arrived: ShallowRef<Map<number, EsportsPage | null>>
}

const byGame = new Map<GameCode, GameAnswers>()

const answersFor = (game: GameCode): GameAnswers => {
  let held = byGame.get(game)
  if (!held) {
    held = {
      answers: heldAnswers<number, EsportsPage | null>(seasonId => loadEsportsPage(game, seasonId)),
      arrived: shallowRef(new Map<number, EsportsPage | null>()),
    }
    byGame.set(game, held)
  }
  return held
}

/** Forgets what was read, so a test or a page that writes a roster can ask again. */
export const forgetEsportsPages = () => byGame.clear()

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
  const {answers, arrived} = answersFor(game)

  /**
   * An answer written down where the panel drawn for its season can find it.
   *
   * Keyed by the season the api answered *about* rather than by the one that was asked for,
   * because those come apart: a page read without naming a season is answered about whichever
   * one the api chose, and the panel standing on that season would otherwise never find it.
   */
  const remember = (seasonId: number | null | undefined, answer: EsportsPage | null) => {
    const key = seasonId ?? answer?.season?.id
    if (key == null) return
    arrived.value = new Map(arrived.value).set(key, answer)
  }
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
      remember(wanted, answer)
      if (!wanting()) return
      page.value = answer
    } catch (error) {
      $handleNetworkError(error)
    } finally {
      if (wanting()) loading.value = false
    }
  }

  const showSeason = async (id: number) => {
    if (id === chosen.value) return
    onSeason(id)
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
    void answers.ask(id).then(answer => remember(id, answer), () => undefined)
  }

  onMounted(() => load(seasonFromRoute() ?? undefined))

  // A season chosen elsewhere — the back button, a shared link — is still a season change.
  watch(seasonFromRoute, (next) => {
    if (next != null && next !== chosen.value) void load(next)
  })

  /**
   * `reload` re-asks about the season already shown, which `showSeason` declines to do.
   *
   * Everything held for this game is dropped first, not just this season's answer: what is
   * re-asked after a write is asked because the api's account of the game has changed, and a
   * season taken away or a team renamed is a change to what its other seasons answer too.
   *
   * What has already arrived is left where the band can still read it, though, because the band
   * is looking at it: a season switch keeps the band it has until the next answer lands, and
   * emptying that would swap it for a pulsing block and back again over a correction the visitor
   * has just made. Each stale answer is replaced as its season is read again, which the holder
   * having forgotten it is what guarantees.
   */
  const reload = async (seasonId?: number) => {
    answers.forget()
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
    answerFor: (id: number): EsportsPage | null | undefined => arrived.value.get(id),
  }
}
