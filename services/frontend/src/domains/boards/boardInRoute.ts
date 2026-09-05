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
import {wholeNumberInQuery} from "@/utils/wholeNumberInQuery"

export function boardInRoute(route: RouteLocationNormalizedLoaded): number | null {
  return wholeNumberInQuery(route, "board")
}
