import {computed, onMounted, ref, watch} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {loadEsportsPage, type EsportsPage, type Game, type Season, type TeamRoster} from "../adapters/esports"

/**
 * One game's page: the season on show, the seasons that can be, and that season's teams.
 *
 * The season is part of the url, so a roster can be linked to and a reload keeps its place.
 */
export function useEsportsPage(game: Game, seasonFromRoute: () => number | null, onSeason: (id: number) => void) {
  const page = ref<EsportsPage | null>(null)
  const loading = ref<boolean>(true)

  const teams = computed<TeamRoster[]>(() => page.value?.teams ?? [])
  const seasons = computed<Season[]>(() => page.value?.seasons ?? [])
  const season = computed<Season | null>(() => page.value?.season ?? null)
  const hasRosters = computed<boolean>(() => teams.value.length > 0)

  const load = async (seasonId?: number) => {
    loading.value = true
    try {
      page.value = await loadEsportsPage(game, seasonId)
    } catch (error) {
      $handleNetworkError(error)
    } finally {
      loading.value = false
    }
  }

  const showSeason = async (id: number) => {
    if (id === season.value?.id) return
    onSeason(id)
    await load(id)
  }

  onMounted(() => load(seasonFromRoute() ?? undefined))

  // A season chosen elsewhere — the back button, a shared link — is still a season change.
  watch(seasonFromRoute, (next) => {
    if (next != null && next !== season.value?.id) void load(next)
  })

  return {page, loading, teams, seasons, season, hasRosters, showSeason}
}
