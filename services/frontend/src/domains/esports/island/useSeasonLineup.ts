import {computed, onMounted, ref} from "vue"
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
 */
export function useSeasonLineup(games: Game[]) {
  const seasons = ref<Season[]>([])
  const selected = ref<number | null>(null)
  const entries = ref<LineupEntry[]>([])
  const loading = ref(true)

  const load = async (seasonId?: number) => {
    loading.value = true
    try {
      const pages = await Promise.all(games.map(game => loadEsportsPage(game, seasonId)))
      const answered = pages
        .map((page, index) => ({page, game: games[index] as Game}))
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

  onMounted(() => load())

  const show = async (seasonId: number) => {
    if (seasonId === selected.value) return
    selected.value = seasonId
    await load(seasonId)
  }

  return {seasons, selected, entries, loading, show, fielded: computed(() => entries.value.length > 0)}
}
