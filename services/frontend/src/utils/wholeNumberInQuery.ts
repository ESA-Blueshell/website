import type {RouteLocationNormalizedLoaded} from "vue-router"

/**
 * The whole number a url's query names under one key, where it names one at all.
 *
 * Boards and seasons both put one in the url and both mean a row that either exists or does not,
 * so a fraction, a word or a sign is nothing to ask the api about and the page opens on whatever
 * it opens on unasked. Shared because the two readers were the same four lines apiece and drifted
 * apart on exactly this, one of them passing `19.5` through to the api (#1110).
 */
export function wholeNumberInQuery(
  route: RouteLocationNormalizedLoaded,
  key: string,
): number | null {
  const raw = route.query[key]
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isInteger(value) && value > 0 ? value : null
}
