import type {Season} from "../adapters/esports"

export interface SeasonNode {
  season: Season
  /** Where it sits along the axis, 0 at the oldest and 1 at the newest. */
  at: number
  /** "Autumn" or "Spring" where the name says so, otherwise the whole name. */
  half: string
  /** "2025/26" where the name says so, otherwise empty. */
  year: string
}

export interface YearSpan {
  year: string
  /** The middle of this year's seasons, for the label above the line. */
  at: number
  from: number
  to: number
}

const NAME = /^(\p{L}+)\s+(\d{4}\/\d{2,4})$/u

/**
 * The seasons as points on a line, oldest first.
 *
 * A season is named for the half of a board year it covers — "Autumn 2025/26" — so the axis
 * carries two readings: the years along the top, and which half of each year below. Anything
 * not named that way still gets a node; it simply has no year to group under.
 */
export function seasonAxis(seasons: Season[]): {nodes: SeasonNode[]; years: YearSpan[]} {
  const ordered = [...seasons].sort((a, b) => a.startDate.localeCompare(b.startDate))
  const last = Math.max(ordered.length - 1, 1)

  const nodes: SeasonNode[] = ordered.map((season, index) => {
    const parts = NAME.exec(season.name)
    return {
      season,
      at: ordered.length === 1 ? 0.5 : index / last,
      half: parts?.[1] ?? season.name,
      year: parts?.[2] ?? "",
    }
  })

  const years: YearSpan[] = []
  for (const node of nodes) {
    if (!node.year) continue
    const open = years.find(y => y.year === node.year)
    if (open) {
      open.to = node.at
      open.at = (open.from + open.to) / 2
    } else {
      years.push({year: node.year, at: node.at, from: node.at, to: node.at})
    }
  }
  return {nodes, years}
}
