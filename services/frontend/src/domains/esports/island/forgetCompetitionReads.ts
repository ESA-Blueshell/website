import {forgetEsportsPages} from "../composables/useEsportsPage"
import {forgetSeasonLineups} from "./useSeasonLineup"

/**
 * Forgets what the competition pages have read about seasons and teams, so a page reached after
 * an edit page has written something asks again rather than drawing what it held before.
 */
export function forgetCompetitionReads(): void {
  forgetSeasonLineups()
  forgetEsportsPages()
}
