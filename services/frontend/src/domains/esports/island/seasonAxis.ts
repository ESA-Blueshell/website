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

/**
 * The strip's fixed proportions.
 *
 * Shared by the geometry below and the component that draws it, because a node's position, a
 * bend's radius and the slant the bands are divided on are all the same handful of numbers
 * read from different directions.
 */
export const STRIP = {
  /** How tall the strip is. */
  height: 104,
  /** How far a node sits above or below the middle of it. */
  amplitude: 15,
  /**
   * How many bands the strip shows at once.
   *
   * Fewer than this and they stretch to fill the width; more and the strip scrolls, so a band
   * never becomes a sliver just because the association has been running a long time.
   */
  tiles: 6,
  /**
   * The narrowest a band may be. Below this a node is untappable and its labels unreadable,
   * so the strip stops shrinking and starts scrolling instead — which is what a phone gets.
   */
  minBand: 94,
  /**
   * How wide a bend is, as a multiple of the height it has to climb.
   *
   * The bend is a fixed window in the middle of the gap rather than the whole of it, so the
   * line runs straight for most of its length and then turns in a short, round corner.
   * Because the window is sized from the climb rather than from the gap, every bend turns
   * through the same radius however far apart two seasons happen to sit.
   */
  bend: 1.28,
  /**
   * Where a bend's control points sit along it, as a fraction of its width.
   *
   * At a half they both land on the midpoint and the bend crosses at a lazy diagonal. Past a
   * half they cross over each other, holding the line flat for longer at each end and taking
   * it through the middle more steeply — a corner rather than a slope, without losing the
   * horizontal tangents that let it meet the straight runs cleanly.
   */
  corner: 0.63,
  /** The angle one band is divided from the next on, in degrees. */
  seam: 7,
  /**
   * How far the line runs past the ends of the strip.
   *
   * Past the strip is off the scroller, so an end that sits there cannot be seen at any
   * scroll position — which is the point: a line that ends in view reads as a drawing laid on
   * the strip rather than as the seasons carrying on.
   */
  bleed: 48,
  /**
   * Over how much of its end the line dissolves.
   *
   * Where the block that adds a season bounds it, the line has to stop somewhere in view.
   * Fading it out over its last stretch means the stop is not a stub with a cap on it, and
   * leaves neither stroke nor glow to fall on a block that is not a season.
   */
  fade: 28,
} as const

/** A season's node, in pixels along the track. */
export interface StripNode {
  id: number
  x: number
  y: number
}

/** The strip as it is drawn: its bands, how wide, where the nodes are, and the line through them. */
export interface SeasonStrip {
  /** The seasons as bands across the strip, oldest first. */
  bands: SeasonBand[]
  /** How wide the strip's content is. As wide as the strip, or as wide as its bands need. */
  track: number
  nodes: StripNode[]
  /** The line through the nodes, as an svg path. */
  path: string
  /** Where the line begins, which is left of the strip. */
  from: number
  /** Where it ends: on the slant below, or past the right of the strip. */
  to: number
  /**
   * Where the seasons stop and the block that adds one begins, or null where there is no
   * such block and the seasons run to the end.
   */
  seam: number | null
}

/** How far the slanted division between two bands leans at a given height. */
function lean(y: number): number {
  return Math.tan((STRIP.seam * Math.PI) / 180) * (STRIP.height / 2 - y)
}

/**
 * The line: flat through each node, then an eased bend to the level of the next.
 *
 * Both control points of a bend sit near its midpoint, which leaves the curve horizontal
 * where it meets each flat run — so it reads as a straight stretch, a bend, another straight
 * stretch, rather than as a zigzag with rounded corners.
 */
function lineThrough(nodes: StripNode[], from: number, to: number): string {
  const first = nodes[0]
  const last = nodes[nodes.length - 1]
  if (!first || !last) return ""
  const parts = [`M ${from},${first.y}`]
  for (let i = 1; i < nodes.length; i += 1) {
    const before = nodes[i - 1]
    const node = nodes[i]
    if (!before || !node) continue
    const gap = node.x - before.x
    const climb = Math.abs(node.y - before.y)
    // A bend never eats more than two thirds of the gap, however tight the seasons are.
    const bend = Math.min(gap * 0.66, climb * STRIP.bend)
    const start = before.x + (gap - bend) / 2
    const end = start + bend
    parts.push(`L ${start},${before.y}`)
    parts.push(
      `C ${start + bend * STRIP.corner},${before.y} ${end - bend * STRIP.corner},${node.y} ${end},${node.y}`,
    )
  }
  parts.push(`L ${to},${last.y}`)
  return parts.join(" ")
}

/**
 * The strip's geometry, in pixels.
 *
 * Pixels rather than fractions because that is what the line is drawn in and what the nodes
 * are placed at, and a length measured against one box and drawn in another is how the lit
 * stretch of the line came to stop short of the season it was reporting.
 *
 * [trailing] counts the bands at the end that are not seasons — the one offering to add
 * another. It takes a share of the track like a season does, so every node stays in the
 * middle of its own band, and it bounds the line: the line is about seasons, so it stops
 * where they do.
 */
export function seasonStrip(
  seasons: Season[],
  options: {width: number; trailing: number},
): SeasonStrip {
  const {width, trailing} = options
  // The bands are laid out here rather than passed in, because their shares and the nodes'
  // positions have to be reckoned from the same count. Handed a set of bands divided some
  // other way, every node would sit off the season it belongs to.
  const bands = seasonBands(seasons, trailing)
  const count = bands.length + trailing
  const track = Math.max(width, count * Math.max(width / STRIP.tiles, STRIP.minBand))
  const middle = STRIP.height / 2

  const nodes: StripNode[] = bands.map(band => ({
    id: band.season.id,
    x: band.at * track,
    y: middle + (band.high ? -STRIP.amplitude : STRIP.amplitude),
  }))

  const last = nodes[nodes.length - 1]
  const seam = trailing > 0 && last ? (bands[bands.length - 1]?.to ?? 1) * track : null
  const from = -STRIP.bleed
  const to = seam == null ? track + STRIP.bleed : seam + lean(last?.y ?? middle)

  return {
    bands,
    track,
    nodes,
    path: width > 0 ? lineThrough(nodes, from, to) : "",
    from,
    to,
    seam,
  }
}

/** How far along the track the line is lit for [id]: the middle of that season's node. */
export function litAt(nodes: StripNode[], id: number | null): number {
  if (id == null) return 0
  return nodes.find(node => node.id === id)?.x ?? 0
}
