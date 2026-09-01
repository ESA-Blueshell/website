import type {Stop} from "@/components/island/stripAxis"
import type {Season} from "../adapters/esports"

/** "Autumn 2025": the half of the year a season runs in, and the year it falls in. */
const NAME = /^(\p{L}+)\s+(\d{4})$/u

/** Which way one season lies from another along the strip. */
export type SeasonDirection = "past" | "future" | "same"

/**
 * The order seasons read in: oldest first, by the date they start.
 *
 * One comparator for the whole module, because the strip's left-to-right order and the
 * direction a season change travels in are the same question asked twice. Two seasons that
 * start on the same day are ordered by id, so a strip drawn twice is drawn the same way.
 */
const byAge = (a: Season, b: Season): number =>
  a.startDate.localeCompare(b.startDate) || a.id - b.id

/**
 * The newest of a set of seasons, or null where there are none.
 *
 * Newest by start date, whatever was fielded in it. A season written down ahead of time is
 * the association's current season the moment it exists: the pages say what season it is,
 * and answer separately whether anybody played in it.
 */
export function newestSeason(seasons: Season[]): Season | null {
  return seasons.reduce<Season | null>(
    (newest, season) => (newest == null || byAge(season, newest) > 0 ? season : newest),
    null,
  )
}

/**
 * Which way [to] lies from [from]: back down the strip, or on up it.
 *
 * The strip runs oldest to newest from left to right, so this is also which way the page
 * travels when the season changes. Either end being absent is "same": there is no direction
 * to travel from nowhere, which is what a page arriving for the first time does.
 */
export function directionBetween(from: Season | null, to: Season | null): SeasonDirection {
  if (!from || !to) return "same"
  const order = byAge(to, from)
  if (order < 0) return "past"
  if (order > 0) return "future"
  return "same"
}

/**
 * [seasons] with [onShow] among them, in the order the strip draws them.
 *
 * A page can be standing on a season that is not in its own list — a game's page opens on the
 * association's newest season whether or not that game played it. The season being read has
 * to have a node on the strip regardless: a strip with nothing lit says the visitor is
 * nowhere, and the seasons that do hold something are what they are being offered instead.
 */
export function seasonsIncluding(seasons: Season[], onShow: Season | null): Season[] {
  const listed = onShow == null || seasons.some(one => one.id === onShow.id)
  return (listed ? [...seasons] : [...seasons, onShow]).sort(byAge)
}

/**
 * The seasons as stops on the island's strip, oldest first.
 *
 * Where the two halves of this meet: the strip's arithmetic knows about stops and shares of a
 * width, and what makes one season older than another or splits "Autumn 2025" into a half and
 * a year is knowledge about seasons. Both live here, so the strip carries neither.
 *
 * A season named some other way still gets a stop; it simply has no year to group under.
 */
export function seasonStops(seasons: Season[]): Stop[] {
  return [...seasons].sort(byAge).map(season => {
    const parts = NAME.exec(season.name)
    return {
      id: season.id,
      name: season.name,
      label: parts?.[1] ?? season.name,
      sublabel: parts?.[2] ?? "",
    }
  })
}
