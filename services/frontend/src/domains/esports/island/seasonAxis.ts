import type {Season} from "../adapters/esports"

export interface SeasonBand {
  season: Season
  /** Its share of the strip: from and to as fractions, with the node in the middle. */
  from: number
  to: number
  at: number
  /** "Autumn" or "Spring" where the name says so, otherwise the whole name. */
  half: string
  /** "2025/26" where the name says so, otherwise empty. */
  year: string
  /** True when this season's node sits above the middle of the strip. */
  high: boolean
}

const NAME = /^(\p{L}+)\s+(\d{4}\/\d{2,4})$/u

/**
 * The seasons as bands across a strip, oldest first.
 *
 * Each season owns a share of the width rather than a point on it, so a node can sit in the
 * middle of its own season and the division between two seasons falls halfway between their
 * nodes — which is where the teams below divide too.
 *
 * A season is named for the half of a board year it covers, "Autumn 2025/26", so a band
 * carries two readings. Anything named some other way still gets a band; it simply has no
 * year to group under.
 *
 * [trailing] reserves shares at the end for bands that are not seasons — the one offering to
 * add another. Reserved here rather than taken out of the width afterwards, because that is
 * what keeps every node in the middle of its own band.
 */
export function seasonBands(seasons: Season[], trailing = 0): SeasonBand[] {
  const ordered = [...seasons].sort((a, b) => a.startDate.localeCompare(b.startDate))
  const share = 1 / Math.max(ordered.length + trailing, 1)

  return ordered.map((season, index) => {
    const parts = NAME.exec(season.name)
    return {
      season,
      from: index * share,
      to: (index + 1) * share,
      at: (index + 0.5) * share,
      half: parts?.[1] ?? season.name,
      year: parts?.[2] ?? "",
      high: index % 2 === 0,
    }
  })
}
