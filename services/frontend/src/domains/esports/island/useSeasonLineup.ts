import {computed, onMounted, ref, unref, watch, type MaybeRef} from "vue"
import {loadEsportsPage, type EsportsPage, type Game, type Season} from "../adapters/esports"
import {seasonsIncluding} from "./seasonAxis"
import {useSeasons} from "./useSeasons"

export interface LineupEntry {
  game: Game
  teams: EsportsPage["teams"]
}

/** The same season twice is one season; two games that played it both name it. */
const dedupe = (seasons: Season[]): Season[] => {
  const byId = new Map<number, Season>()
  seasons.forEach(season => byId.set(season.id, season))
  return [...byId.values()]
}

/**
 * What the association fielded in one season, across every game.
 *
 * The api can say what one game did in one season; nothing can yet say what every game did,
 * so this asks each game in turn and assembles the answer. That is one request per game on
 * the index — five today — and it is why this lives behind a composable: when the read that
 * lists every game with its seasons lands, only the body of `load` changes.
 *
 * A game that fielded nothing in the season is left out rather than shown empty, since the
 * page is about what was fielded.
 *
 * Which games to ask about arrives from their records, so it is read at each load rather than
 * captured once, and the first load waits on `until` — otherwise it would ask about no games
 * and the page would read as a season nothing was fielded in.
 *
 * The season is part of the url, so a season can be linked to, the back button works, and
 * following a game out of the index arrives on the season that was being read.
 */
export function useSeasonLineup(
  games: MaybeRef<Game[]>,
  seasonFromRoute: () => number | null,
  until?: Promise<unknown>,
) {
  const {ready: seasonsRead, newest} = useSeasons()
  const seasons = ref<Season[]>([])
  const selected = ref<number | null>(null)
  const chosen = ref<number | null>(null)
  const entries = ref<LineupEntry[]>([])
  const loading = ref(true)

  /**
   * Which read is the current one.
   *
   * Seasons can be clicked faster than they can be answered, and nothing made the answers
   * come back in the order they were asked for — so a slow read of one season could land on
   * top of a fast read of another and leave the page showing a season nobody chose. Every
   * read carries a number and only the newest is allowed to write.
   */
  let asking = 0

  const load = async (seasonId?: number) => {
    const mine = (asking += 1)
    // What the visitor asked for is known before anything is read, and the strip follows it
    // straight away. Only the band waits for the answer.
    if (seasonId != null) chosen.value = seasonId
    loading.value = true
    try {
      await seasonsRead
      if (mine !== asking) return
      // Every read names its season. Left unsaid, the api answers with the newest season each
      // game was fielded in — which is a different season per game, and the whole of why the
      // index used to show every season of every game at once.
      const wanted = seasonId ?? newest.value?.id
      if (wanted != null) chosen.value = wanted

      const asked = unref(games)
      const pages = await Promise.all(asked.map(game => loadEsportsPage(game, wanted)))
      if (mine !== asking) return

      const answered = pages
        .map((page, index) => ({page, game: asked[index] as Game}))
        .filter((row): row is {page: EsportsPage; game: Game} => row.page != null)

      const first = answered[0]?.page
      // The strip carries every season any game played, and the season being read whether or
      // not anybody played it. Taking it from the first game to answer meant the strip was
      // one game's history standing in for the association's.
      seasons.value = seasonsIncluding(
        dedupe(answered.flatMap(row => row.page.seasons ?? [])),
        first?.season ?? null,
      )
      selected.value = first?.season?.id ?? wanted ?? null
      entries.value = answered
        .filter(row => row.page.teams.length > 0)
        .map(row => ({game: row.game, teams: row.page.teams}))
    } finally {
      if (mine === asking) loading.value = false
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

  // A season chosen elsewhere — the back button, a shared link — is still a season change.
  watch(seasonFromRoute, (next) => {
    if (next != null && next !== chosen.value) void load(next)
  })

  // `reload` re-asks about the season already on show, which `show` declines to do.
  return {
    seasons, selected, chosen, entries, loading, show, reload: load,
    fielded: computed(() => entries.value.length > 0),
  }
}
