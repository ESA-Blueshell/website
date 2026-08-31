import {computed, onMounted, ref, watch} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {useSeasons} from "../island/useSeasons"
import {loadEsportsPage, type EsportsPage, type Game, type Season, type TeamRoster} from "../adapters/esports"

/**
 * One game's page: the shown season, the seasons that can be, and that season's teams.
 *
 * The season is part of the url, so a roster can be linked to and a reload keeps its place.
 * Where the url names none, the page opens on the association's newest season rather than on
 * this game's own newest — every esports page agrees about what "now" is, and a game that has
 * not been fielded lately says so instead of quietly showing an old squad as a current one.
 */
export function useEsportsPage(game: Game, seasonFromRoute: () => number | null, onSeason: (id: number) => void) {
  const {ready: seasonsRead, newest} = useSeasons()
  const page = ref<EsportsPage | null>(null)
  const loading = ref<boolean>(true)
  /** The season the visitor asked for, which the strip follows before the answer arrives. */
  const chosen = ref<number | null>(null)

  const teams = computed<TeamRoster[]>(() => page.value?.teams ?? [])
  const seasons = computed<Season[]>(() => page.value?.seasons ?? [])
  const season = computed<Season | null>(() => page.value?.season ?? null)
  const hasRosters = computed<boolean>(() => teams.value.length > 0)

  /** Which read is the current one, so a slow answer cannot land on top of a newer one. */
  let asking = 0

  const load = async (seasonId?: number) => {
    const mine = (asking += 1)
    if (seasonId != null) chosen.value = seasonId
    loading.value = true
    try {
      await seasonsRead
      if (mine !== asking) return
      const wanted = seasonId ?? newest.value?.id
      if (wanted != null) chosen.value = wanted
      const answer = await loadEsportsPage(game, wanted)
      if (mine !== asking) return
      page.value = answer
    } catch (error) {
      $handleNetworkError(error)
    } finally {
      if (mine === asking) loading.value = false
    }
  }

  const showSeason = async (id: number) => {
    if (id === chosen.value) return
    onSeason(id)
    await load(id)
  }

  onMounted(() => load(seasonFromRoute() ?? undefined))

  // A season chosen elsewhere — the back button, a shared link — is still a season change.
  watch(seasonFromRoute, (next) => {
    if (next != null && next !== chosen.value) void load(next)
  })

  // `reload` re-asks about the season already shown, which `showSeason` declines to do.
  return {page, loading, teams, seasons, season, chosen, hasRosters, showSeason, reload: load}
}
