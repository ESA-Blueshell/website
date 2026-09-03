/**
 * A stop on the strip: what it is, and what it says about itself.
 *
 * The arithmetic below knows nothing about what a stop stands for (a season, a board, a year of
 * anything) so whoever draws a strip says what each stop reads as. Two lines, because a
 * stop is labelled twice: a word across the middle of the line and a smaller one under it.
 */
export interface Stop {
  id: number
  /** What a reader hears it as, and what an action on it is named for. */
  name: string
  /** The larger of its two labels. */
  label: string
  /** The smaller one beneath it, or empty where the stop has only the one. */
  sublabel: string
  /**
   * A word marking this stop out from the rest of the line, or empty where it is one of many.
   *
   * What that word is belongs to whoever draws the strip, which may mark a stop "In office" or
   * "Candidate", and the arithmetic here has no opinion about either. It says nothing about
   * which stop is being read: that is `selectedId`, and a reader moving down the line changes
   * it, so the mark stays where it is while they do.
   */
  mark?: string
  /**
   * Its own colour, where whatever it stands for has one.
   *
   * The strip lights the line in the colour of the stop under the pointer and highlights each
   * band in its own, so moving down the line is moving through their colours. Absent means the
   * strip's own accent stands, which is what every stop drew before any of them had a colour
   * recorded.
   */
  accent?: string
}

export interface StripBand {
  stop: Stop
  /** Its share of the strip: from and to as fractions, with the node in the middle. */
  from: number
  to: number
  at: number
  /** True when this stop's node sits above the middle of the strip. */
  high: boolean
}

/**
 * The stops as bands across a strip, in the order they were given.
 *
 * Each stop owns a share of the width rather than a point on it, so a node can sit in the
 * middle of its own band and the division between two stops falls halfway between their
 * nodes, which is where whatever the strip governs divides too.
 *
 * The order is the caller's: what makes one stop older than another is knowledge about what
 * the stops are, and this arithmetic has none.
 *
 * [trailing] reserves shares at the end for bands that are not stops: the one offering to
 * add another. Reserved here rather than taken out of the width afterwards, because that is
 * what keeps every node in the middle of its own band.
 */
export function bands(stops: Stop[], trailing = 0): StripBand[] {
  const share = 1 / Math.max(stops.length + trailing, 1)

  return stops.map((stop, index) => ({
    stop,
    from: index * share,
    to: (index + 1) * share,
    at: (index + 0.5) * share,
    high: index % 2 === 0,
  }))
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
   * so the strip stops shrinking and starts scrolling instead.
   *
   * A floor for a strip read with a pointer, and one that cannot currently bind: it only applies
   * at or above `stacks`, and six bands sharing 768px are already 128 wide. Kept because it is
   * the floor `tiles` is safe under rather than a figure this arithmetic happens not to reach —
   * showing more than six at once, or a strip in a narrower column than the page, and it is what
   * stops a node becoming a sliver. Below `stacks` a thumb asks for a great deal more, which is
   * `minBandStacked`.
   */
  minBand: 94,
  /**
   * And the narrowest it may be on a phone, which is a great deal wider.
   *
   * `minBand` is a figure for a strip with a pointer over it: a node wants to be hittable and
   * its two labels want to fit. A thumb and a phone want more of both. At the shared figure a
   * 390px screen fitted four bands, so every label was clipped at one end of the window or the
   * other and the outermost ones sat under the arrows that pan the strip. At this figure a
   * phone shows one stop and a little of its neighbours, which is what a strip that scrolls is
   * for.
   */
  minBandStacked: 320,
  /** Under this the strip is being read on a phone. The same figure the bands stack at. */
  stacks: 768,
  /**
   * How wide a bend is, as a multiple of the height it has to climb.
   *
   * The bend is a fixed window in the middle of the gap rather than the whole of it, so the
   * line runs straight for most of its length and then turns in a short, round corner.
   * Because the window is sized from the climb rather than from the gap, every bend turns
   * through the same radius however far apart two stops happen to sit.
   */
  bend: 1.28,
  /**
   * Where a bend's control points sit along it, as a fraction of its width.
   *
   * At a half they both land on the midpoint and the bend crosses at a lazy diagonal. Past a
   * half they cross over each other, holding the line flat for longer at each end and taking
   * it through the middle more steeply: a corner rather than a slope, without losing the
   * horizontal tangents that let it meet the straight runs cleanly.
   */
  corner: 0.63,
  /** The angle one band is divided from the next on, in degrees. */
  seam: 7,
  /**
   * How far the line runs past the ends of the strip.
   *
   * Past the strip is off the scroller, so an end that sits there cannot be seen at any
   * scroll position, which is the point: a line that ends in view reads as a drawing laid on
   * the strip rather than as the stops carrying on.
   */
  bleed: 48,
  /**
   * Over how much of its end the line dissolves.
   *
   * Where the block that adds a stop bounds it, the line has to stop somewhere in view.
   * Fading it out over its last stretch means the stop is not a stub with a cap on it, and
   * leaves neither stroke nor glow to fall on a block that is not a stop.
   */
  fade: 28,
} as const

/** A stop's node, in pixels along the track. */
export interface StripNode {
  id: number
  x: number
  y: number
}

/** The strip as it is drawn: its bands, how wide, where the nodes are, and the line through them. */
export interface StripLayout {
  /** The stops as bands across the strip, in the order they were given. */
  bands: StripBand[]
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
   * Where the stops stop and the block that adds one begins, or null where there is no
   * such block and the stops run to the end.
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
 * where it meets each flat run, so it reads as a straight stretch, a bend, another straight
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
    // A bend never eats more than two thirds of the gap, however tight the stops are.
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
 * stretch of the line came to stop short of the stop it was reporting.
 *
 * [trailing] counts the bands at the end that are not stops: the one offering to add
 * another. It takes a share of the track like a stop does, so every node stays in the
 * middle of its own band, and it bounds the line: the line is about the stops, so it stops
 * where they do.
 */
export function stripAxis(
  stops: Stop[],
  options: {width: number; trailing: number},
): StripLayout {
  const {width, trailing} = options
  // The bands are laid out here rather than passed in, because their shares and the nodes'
  // positions have to be reckoned from the same count. Handed a set of bands divided some
  // other way, every node would sit off the stop it belongs to.
  const laid = bands(stops, trailing)
  const count = laid.length + trailing
  // A band's floor is the reader's, not the strip's: a thumb on a phone needs more room than a
  // pointer on a desktop, and the strip is the same component either way.
  const floor = width > 0 && width < STRIP.stacks ? STRIP.minBandStacked : STRIP.minBand
  const track = Math.max(width, count * Math.max(width / STRIP.tiles, floor))
  const middle = STRIP.height / 2

  const nodes: StripNode[] = laid.map(band => ({
    id: band.stop.id,
    x: band.at * track,
    y: middle + (band.high ? -STRIP.amplitude : STRIP.amplitude),
  }))

  const last = nodes[nodes.length - 1]
  const seam = trailing > 0 && last ? (laid[laid.length - 1]?.to ?? 1) * track : null
  const from = -STRIP.bleed
  const to = seam == null ? track + STRIP.bleed : seam + lean(last?.y ?? middle)

  return {
    bands: laid,
    track,
    nodes,
    path: width > 0 ? lineThrough(nodes, from, to) : "",
    from,
    to,
    seam,
  }
}

/** How far along the track the line is lit for [id]: the middle of that stop's node. */
export function litAt(nodes: StripNode[], id: number | null): number {
  if (id == null) return 0
  return nodes.find(node => node.id === id)?.x ?? 0
}
