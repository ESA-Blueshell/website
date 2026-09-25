import {ref, type Ref} from "vue"
import {loadEsportsPage, type GameCode, type Season, type TeamRoster} from "../adapters/esports"

/**
 * What a team's edit page needs about one game in one season: the season itself, the teams already
 * fielded in it, and the team being edited among them. Read once, when the page opens.
 *
 * No season asked for is the game's own answer for the newest one, as the game page does.
 */
export function useTeamToEdit(game: GameCode, teamId: number | null, seasonId: number | null): {
  season: Ref<Season | null>
  fielded: Ref<TeamRoster[]>
  team: Ref<TeamRoster | null>
  answered: Promise<void>
} {
  const season = ref<Season | null>(null)
  const fielded = ref<TeamRoster[]>([])
  const team = ref<TeamRoster | null>(null)
  const answered = loadEsportsPage(game, seasonId ?? undefined).then(page => {
    season.value = page?.season ?? null
    fielded.value = page?.teams ?? []
    team.value = fielded.value.find(one => one.id === teamId) ?? null
  })
  return {season, fielded, team, answered}
}
