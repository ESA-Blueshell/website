/**
 * The board a url names, where it names one at all.
 *
 * `?board=<number>` rather than a path, mirroring `?season=`: the board being read is the page's
 * own state, and keeping it in the url is what makes it linkable and the back button correct.
 * The **number** travels, being what a reader sees and says. Asked here rather than off the
 * query wherever it is needed, so there is one answer to what counts as a board being named;
 * whether that board exists is the page's, which is the only thing that knows.
 */
import type {RouteLocationNormalizedLoaded} from "vue-router"

export function boardInRoute(route: RouteLocationNormalizedLoaded): number | null {
  const raw = route.query.board
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isInteger(value) && value > 0 ? value : null
}
