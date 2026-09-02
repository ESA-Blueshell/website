import {describe, expect, it} from "vitest"
import {BOARD_BLUE, inkOnAccent} from "@/domains/boards/accent"

/**
 * Which ink reads on a board's colour, read against the colours the boards actually carry.
 *
 * Four of the ten have one recorded and they split two pale and two deep, so between them they
 * catch a rule that only ever answers one way. The contrast ratios below are the ones that made
 * the pairing: every accent clears 4.5:1 under the ink this answers with, and none of them
 * clears 3:1 under the other.
 */
const ACCENTS = {
  /** Board VI, Don't starve together. 2.00:1 under white, 9.60:1 under near-black. */
  pink: "#eaa4b6",
  /** Board VII, Overcooked. 6.78:1 under white, 2.84:1 under near-black. */
  magenta: "#b00b69",
  /** Board VIII, Wasted. 6.74:1 under white, 2.85:1 under near-black. */
  purple: "#9100d0",
  /** Board X, Rainbow road, whose colour fills a whole band: it has no photograph. */
  cyan: "#65c6cd",
} as const

describe("inkOnAccent", () => {
  it("puts dark ink on a pale fill", () => {
    expect(inkOnAccent(ACCENTS.pink)).toBe("dark")
    expect(inkOnAccent(ACCENTS.cyan)).toBe("dark")
  })

  it("puts light ink on a deep fill", () => {
    expect(inkOnAccent(ACCENTS.magenta)).toBe("light")
    expect(inkOnAccent(ACCENTS.purple)).toBe("light")
  })

  it("answers for the association's blue, which is what a board with no colour is drawn in", () => {
    // 3.51:1 under white against 5.48:1 under near-black, so the blue takes dark ink too.
    expect(inkOnAccent(BOARD_BLUE)).toBe("dark")
    expect(inkOnAccent(null)).toBe(inkOnAccent(BOARD_BLUE))
    expect(inkOnAccent("")).toBe(inkOnAccent(BOARD_BLUE))
    expect(inkOnAccent("   ")).toBe(inkOnAccent(BOARD_BLUE))
  })

  it("reads the ends of the scale the way they are drawn", () => {
    expect(inkOnAccent("#ffffff")).toBe("dark")
    expect(inkOnAccent("#000000")).toBe("light")
  })

  it("reads a three-digit colour, which is how a colour is often written down", () => {
    expect(inkOnAccent("#fff")).toBe(inkOnAccent("#ffffff"))
    expect(inkOnAccent("#000")).toBe(inkOnAccent("#000000"))
  })

  it("ignores an alpha riding along, because a fill is painted over the page", () => {
    expect(inkOnAccent("#eaa4b6ff")).toBe("dark")
  })

  it("falls back to the blue's answer for a colour it cannot read", () => {
    // A free string is what the column holds, so the notation is not this rule's to insist on.
    for (const written of ["rebeccapurple", "rgb(234 164 182)", "#12", "not a colour"]) {
      expect(inkOnAccent(written)).toBe(inkOnAccent(BOARD_BLUE))
    }
  })
})
