import {onMounted, ref, shallowRef, watch} from "vue"
import {loadSeasonGames, type GameCode, type Season, type SeasonGame, type TeamRoster} from "../adapters/esports"
import {asksInOrder} from "./asksInOrder"
import {heldAnswers} from "./heldAnswers"
import {seasonsIncluding} from "./seasonAxis"
import {useSeasons} from "./useSeasons"

export interface LineupEntry {
  game: GameCode
  teams: TeamRoster[]
  /**
   * Whether a visitor sees this game in this season.
   *
   * The api decides it, because it turns on who is asking: a game entered with nobody fielded
   * is answered to somebody who may edit and to nobody else. The page draws what it is given
   * and marks the ones that are not public yet; it does not decide who may see them.
   */
  public: boolean
}

/**
 * What each season fielded, kept by season and shared by every reading of the index.
 *
 * Held here rather than in the composable so that walking back along the strip to a season
 * already read costs nothing, and so that more than one season's answer can exist at once —
 * which is what a page being dragged towards its neighbour needs.
 */
const answers = heldAnswers<number, SeasonGame[]>(loadSeasonGames)

/**
 * The same answers again, where a template can watch them arrive.
 *
 * The holder is a plain module and keeps a plain map, which is the whole of why it can be proved
 * without a browser — but a panel drawn for a season nobody has navigated to has to redraw itself
 * the moment that season's answer lands, and a plain map says nothing when it is written to. So
 * what has arrived is mirrored here, and the band reads this while the holder goes on deciding
 * what is worth reading and what is already in flight.
 *
 * Shallow, and replaced rather than written into: the answers themselves are handed out by
 * identity — a band watches the array it was given and takes a new one for a new season — so
 * making the rosters inside them reactive would buy nothing and cost a proxy per player.
 */
const arrived = shallowRef(new Map<number, SeasonGame[]>())

const remember = (seasonId: number, answer: SeasonGame[]) => {
  arrived.value = new Map(arrived.value).set(seasonId, answer)
}

/** Forgets what was read, so a test or a page that writes a line-up can ask again. */
export const forgetSeasonLineups = () => {
  answers.forget()
  arrived.value = new Map()
}

/**
 * What the association ran in one season, across every game.
 *
 * One read rather than one per game: the api answers with the games of a season and what each
 * fielded, and with the ones entered but not yet staffed where the caller may edit. That read
 * is also where the rule about what is public lives, which is why it is a request rather than
 * a filter here — it turns on who is asking.
 *
 * The season is part of the url, so a season can be linked to, the back button works, and
 * following a game out of the index arrives on the season that was being read.
 */
export function useSeasonLineup(
  seasonFromRoute: () => number | null,
  until?: Promise<unknown>,
) {
  const {ready: seasonsRead, newest, seasons: allSeasons} = useSeasons()
  const seasons = ref<Season[]>([])
  const selected = ref<number | null>(null)
  const chosen = ref<number | null>(null)
  const entries = ref<LineupEntry[]>([])
  const loading = ref(true)

  /**
   * Which read this page is waiting on. Seasons can be clicked faster than they can be
   * answered, and the answer for a season nobody is looking at any more may not be the one
   * that ends up on screen — see `asksInOrder`, which keeps that reasoning.
   */
  const begin = asksInOrder()

  const load = async (seasonId?: number) => {
    const wanting = begin()
    // What the visitor asked for is known before anything is read, and the strip follows it
    // straight away. Only the band waits for the answer.
    if (seasonId != null) chosen.value = seasonId
    loading.value = true
    try {
      await seasonsRead
      if (!wanting()) return
      // Every read names its season. Left unsaid, the api answers with the newest season each
      // game was fielded in — which is a different season per game, and the whole of why the
      // index used to show every season of every game at once.
      const wanted = seasonId ?? newest.value?.id
      if (wanted != null) chosen.value = wanted

      if (wanted == null) {
        entries.value = []
        seasons.value = []
        return
      }
      const answered = await answers.ask(wanted)
      remember(wanted, answered)
      if (!wanting()) return

      // The strip carries every season anything was played in, and the season being read
      // whether or not anything was. Which of them a visitor is offered is the season\'s own
      // answer rather than this page\'s: a season nobody played is not one to arrive on.
      const onShow = allSeasons.value.find(one => one.id === wanted) ?? null
      seasons.value = seasonsIncluding(allSeasons.value.filter(one => one.played), onShow)
      selected.value = wanted
      entries.value = answered
    } finally {
      if (wanting()) loading.value = false
    }
  }

  onMounted(async () => {
    await until
    await load(seasonFromRoute() ?? undefined)
  })

  const show = async (seasonId: number) => {
    if (seasonId === chosen.value) return
    await load(seasonId)
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
  const askAhead = (seasonId: number) => {
    void answers.ask(seasonId).then(
      answer => remember(seasonId, answer),
      () => undefined,
    )
  }

  // A season chosen elsewhere — the back button, a shared link — is still a season change.
  watch(seasonFromRoute, (next) => {
    if (next != null && next !== chosen.value) void load(next)
  })

  /**
   * `reload` re-asks about the season already shown, which `show` declines to do.
   *
   * Everything held is dropped first, not just this season's answer: what is re-asked after a
   * write is asked because the api's account of the association has changed, and a season
   * taken away or a game corrected is a change to what every other season answers too.
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
    seasons, selected, chosen, entries, loading, show, askAhead, reload,
    /**
     * What is in hand for a season, and nothing at all where it has not been read.
     *
     * Nothing and an empty answer are different things and the band draws them differently: a
     * season nobody has asked about yet is still loading, and a season that was asked about and
     * fielded nobody is that season's answer. A page that could not tell them apart would draw
     * a spinner where a season should be saying it was quiet.
     */
    answerFor: (seasonId: number): LineupEntry[] | undefined => arrived.value.get(seasonId),
  }
}
