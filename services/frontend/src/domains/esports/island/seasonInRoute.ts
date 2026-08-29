import type {RouteLocationNormalizedLoaded} from "vue-router"

/**
 * The season a url names, where it names one at all.
 *
 * The season lives in the url on the index and on a game's page alike, so a roster can be
 * linked to, the back button works, and following a game out of the index keeps the season
 * that was being read. Both pages ask here rather than each deciding for itself what counts
 * as a season being named.
 */
export function seasonInRoute(route: RouteLocationNormalizedLoaded): number | null {
  const raw = route.query.season
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(value) && value > 0 ? value : null
}
