/**
 * The board a url names, where it names one at all.
 *
 * `?board=<number>` rather than a path of its own, mirroring the esports `?season=`: the board
 * being read is the page's own state, and keeping it in the url is what makes a board linkable
 * and the back button correct. The board's **number** is what travels, because that is what a
 * reader sees and says — `?board=9` is the ninth board, whatever its database key happens to be.
 *
 * Asked here rather than read off the query where it is needed, so there is one answer to what
 * counts as a board being named. Nothing else is: a board that is not among the ones the api
 * answered with is settled by the page, which is the only thing that knows what those are.
 */
import type {RouteLocationNormalizedLoaded} from "vue-router"

export function boardInRoute(route: RouteLocationNormalizedLoaded): number | null {
  const raw = route.query.board
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isInteger(value) && value > 0 ? value : null
}
