import {computed, onMounted, ref, watch} from "vue"
import {loadSeasonGames, type Game, type Season, type TeamRoster} from "../adapters/esports"
import {seasonsIncluding} from "./seasonAxis"
import {useSeasons} from "./useSeasons"

export interface LineupEntry {
  game: Game
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

      if (wanted == null) {
        entries.value = []
        seasons.value = []
        return
      }
      const answered = await loadSeasonGames(wanted)
      if (mine !== asking) return

      // The strip carries every season anything was played in, and the season being read
      // whether or not anything was. Which of them a visitor is offered is the season\'s own
      // answer rather than this page\'s: a season nobody played is not one to arrive on.
      const onShow = allSeasons.value.find(one => one.id === wanted) ?? null
      seasons.value = seasonsIncluding(allSeasons.value.filter(one => one.played), onShow)
      selected.value = wanted
      entries.value = answered
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
