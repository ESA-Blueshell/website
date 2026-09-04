/**
 * A board's colour, and what reads on top of it.
 *
 * The accent is a fill rather than an ink — it washes a band, lights the timeline, backs a
 * swatch — so the question is not whether the colour is readable but which ink reads on it: a
 * pale fill takes dark text, a deep fill light. The colour is painted as chosen, being the
 * board's own. Board knowledge, so it lives in the domain and imports nothing (frontend ADR-001).
 */

/**
 * The association's blue, which is what a board with no colour of its own is drawn in.
 *
 * Spelled out because a luminance cannot be read out of `var(--color-brand)`: the token in
 * `styles/island.css` is the one this follows, and the two are the same colour by hand. It is
 * only ever used to answer which ink pairs with the blue, never painted from here.
 */
export const BOARD_BLUE = "#3387fa"

/** Which of the two inks reads on a fill: the near-black one, or the near-white one. */
export type AccentInk = "light" | "dark"

/** The two inks the island paints on a fill: `--color-void`, and the numeral's own white. */
const DARK_INK = 0.011
const LIGHT_INK = 0.905

/** The channels of a `#rgb`, `#rrggbb` or `#rrggbbaa` colour, or nothing where none reads. */
function channelsOf(colour: string): [number, number, number] | null {
  const written = colour.trim().replace(/^#/, "")
  if (!/^[0-9a-f]+$/i.test(written)) return null
  if (written.length === 3) {
    const [r, g, b] = [...written].map(one => Number.parseInt(one + one, 16) / 255)
    return [r!, g!, b!]
  }
  // An alpha may ride along and nothing here reads it: a fill is painted over the page.
  if (written.length !== 6 && written.length !== 8) return null
  const pairs = [written.slice(0, 2), written.slice(2, 4), written.slice(4, 6)]
  const [r, g, b] = pairs.map(pair => Number.parseInt(pair, 16) / 255)
  return [r!, g!, b!]
}

/** How bright a colour is to an eye, on the scale WCAG's contrast ratio is built on. */
function luminance([r, g, b]: [number, number, number]): number {
  const linear = (one: number) => (one <= 0.04045 ? one / 12.92 : ((one + 0.055) / 1.055) ** 2.4)
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
}

/**
 * Which ink reads on a board's colour: whichever of the two the fill contrasts with more.
 *
 * That is the whole rule, and it is why it cannot be a threshold on lightness alone — the two inks
 * are not equally far from the middle of the scale, so the colour where the answer turns over is
 * where their two ratios cross rather than at any round number. A board with no colour of its own
 * is drawn in the association's blue, so that is what the blank case is answered against — as is a
 * colour written in some notation this cannot read. The fill is the blue in both cases, so the
 * pairing is right rather than merely safe.
 */
export function inkOnAccent(accent?: string | null): AccentInk {
  const fill = luminance(channelsOf(accent?.trim() || BOARD_BLUE) ?? channelsOf(BOARD_BLUE)!)
  const onDark = (fill + 0.05) / (DARK_INK + 0.05)
  const onLight = (LIGHT_INK + 0.05) / (fill + 0.05)
  return onDark >= onLight ? "dark" : "light"
}
