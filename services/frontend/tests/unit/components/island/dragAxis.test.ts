import {describe, expect, it} from "vitest"
import {commits, DRAG, directionOf, follow, paceOf} from "@/components/island/dragAxis"

/**
 * The gesture's thresholds, proved here rather than in a browser.
 *
 * A phone's width, so a quarter of it is a number worth arguing about: 390 gives 97.5.
 */
const WIDTH = 390
/** Where the band may lean at the end of the line: 2.5rem at the browser's default. */
const CAP = 40

describe("directionOf", () => {
  it("reads a finger dragged rightwards as going back down the line", () => {
    // Oldest is left on the strip, so pulling the page rightwards brings an older stop in from
    // the left, which is the pass a click on an earlier node already plays.
    expect(directionOf(60)).toBe("past")
  })

  it("reads a finger dragged leftwards as going on up it", () => {
    expect(directionOf(-60)).toBe("future")
  })

  it("gives no direction to a finger that has not moved", () => {
    expect(directionOf(0)).toBe("same")
  })
})

describe("paceOf", () => {
  it("reports pixels per millisecond, keeping the direction", () => {
    expect(paceOf(60, 100)).toBeCloseTo(0.6)
    expect(paceOf(-60, 100)).toBeCloseTo(-0.6)
  })

  it("calls a stretch with no time in it no speed rather than an infinite one", () => {
    // Two moves coalesced onto one timestamp is a thing browsers do.
    expect(paceOf(30, 0)).toBe(0)
    expect(paceOf(30, -1)).toBe(0)
  })
})

describe("commits", () => {
  it("takes a quick short flick, however little ground it covered", () => {
    // A fifth of what the distance rule wants, thrown at twice the pace it wants.
    expect(commits({travel: 20, pace: 1.1, width: WIDTH, onward: true})).toBe(true)
  })

  it("takes a slow haul past a quarter of the width, however slowly it ended", () => {
    expect(commits({travel: WIDTH * 0.3, pace: 0.02, width: WIDTH, onward: true})).toBe(true)
  })

  it("hands back a drag that was neither fast enough nor far enough", () => {
    expect(commits({travel: 40, pace: 0.2, width: WIDTH, onward: true})).toBe(false)
  })

  it("reads a finger that turned back before lifting as a change of mind", () => {
    // Pushed rightwards, pulled leftwards at the last moment, and not far enough on distance.
    // Unsigned, that retreat would have been taken for the flick that commits.
    expect(commits({travel: 40, pace: -1.4, width: WIDTH, onward: true})).toBe(false)
  })

  it("never commits at the end of the line, however the finger left the glass", () => {
    expect(commits({travel: 200, pace: 2, width: WIDTH, onward: false})).toBe(false)
  })

  it("never commits on a release that went nowhere at all", () => {
    expect(commits({travel: 0, pace: 0, width: WIDTH, onward: true})).toBe(false)
  })

  it("draws the two lines where the epic put them", () => {
    // Exactly on the pace and exactly on the share both count, so the thresholds are a floor
    // rather than something to get past.
    expect(commits({travel: 10, pace: DRAG.pace, width: WIDTH, onward: true})).toBe(true)
    expect(commits({travel: WIDTH * DRAG.share, pace: 0, width: WIDTH, onward: true})).toBe(true)
    expect(commits({travel: WIDTH * DRAG.share - 1, pace: 0, width: WIDTH, onward: true})).toBe(false)
  })
})

describe("follow", () => {
  it("follows the finger exactly where there is a stop that way", () => {
    expect(follow({travel: 123, width: WIDTH, onward: true, cap: CAP})).toBe(123)
    expect(follow({travel: -123, width: WIDTH, onward: true, cap: CAP})).toBe(-123)
  })

  it("stops at a full width, because a full width is the arrival", () => {
    expect(follow({travel: 900, width: WIDTH, onward: true, cap: CAP})).toBe(WIDTH)
    expect(follow({travel: -900, width: WIDTH, onward: true, cap: CAP})).toBe(-WIDTH)
  })

  it("leans a third of the way at the end of the line", () => {
    expect(follow({travel: 60, width: WIDTH, onward: false, cap: CAP})).toBeCloseTo(20)
    expect(follow({travel: -60, width: WIDTH, onward: false, cap: CAP})).toBeCloseTo(-20)
  })

  it("stops leaning at the cap, however far the finger hauls", () => {
    expect(follow({travel: 900, width: WIDTH, onward: false, cap: CAP})).toBe(CAP)
    expect(follow({travel: -900, width: WIDTH, onward: false, cap: CAP})).toBe(-CAP)
  })

  it("stands still for a finger that has not moved", () => {
    expect(follow({travel: 0, width: WIDTH, onward: true, cap: CAP})).toBe(0)
    expect(follow({travel: 0, width: WIDTH, onward: false, cap: CAP})).toBe(0)
  })
})
