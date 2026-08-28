import {computed, onMounted, ref, unref, watch, type MaybeRef} from "vue"
import {loadEsportsPage, type EsportsPage, type Game, type Season} from "../adapters/esports"

export interface LineupEntry {
  game: Game
  teams: EsportsPage["teams"]
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
  const seasons = ref<Season[]>([])
  const selected = ref<number | null>(null)
  const entries = ref<LineupEntry[]>([])
  const loading = ref(true)

  const load = async (seasonId?: number) => {
    loading.value = true
    try {
      const asking = unref(games)
      const pages = await Promise.all(asking.map(game => loadEsportsPage(game, seasonId)))
      const answered = pages
        .map((page, index) => ({page, game: asking[index] as Game}))
        .filter((row): row is {page: EsportsPage; game: Game} => row.page != null)

      const first = answered[0]?.page
      if (first) {
        seasons.value = first.seasons ?? []
        selected.value = first.season?.id ?? null
      }
      entries.value = answered
        .filter(row => row.page.teams.length > 0)
        .map(row => ({game: row.game, teams: row.page.teams}))
    } finally {
      loading.value = false
    }
  }

  onMounted(async () => {
    await until
    await load(seasonFromRoute() ?? undefined)
  })

  const show = async (seasonId: number) => {
    if (seasonId === selected.value) return
    selected.value = seasonId
    await load(seasonId)
  }

  // A season chosen elsewhere — the back button, a shared link — is still a season change.
  watch(seasonFromRoute, (next) => {
    if (next != null && next !== selected.value) void load(next)
  })

  // `reload` re-asks about the season already on show, which `show` declines to do.
  return {
    seasons, selected, entries, loading, show, reload: load,
    fielded: computed(() => entries.value.length > 0),
  }
}
