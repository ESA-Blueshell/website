import {describe, expect, it} from "vitest"
import {nextBoardNumber} from "@/domains/boards/numbering"

/** The ten boards as they are recorded, which is the case the suggestion exists for. */
const history = Array.from({length: 10}, (_, index) => ({number: index + 1}))

describe("nextBoardNumber", () => {
  it("suggests the eleventh board after the ten there are", () => {
    expect(nextBoardNumber(history)).toBe(11)
  })

  it("suggests the first board where none is recorded", () => {
    expect(nextBoardNumber([])).toBe(1)
  })

  it("goes by the highest number rather than by the count", () => {
    // A board nobody wrote down, or one removed since: counting would suggest a taken number,
    // and the api refuses a number twice over.
    expect(nextBoardNumber([{number: 1}, {number: 2}, {number: 9}])).toBe(10)
  })

  it("reads the boards in whatever order they arrive in", () => {
    // The adapter answers newest first, which is the order the page holds them in.
    expect(nextBoardNumber([{number: 10}, {number: 9}, {number: 1}])).toBe(11)
  })

  it("passes over a number that is not a place in the line", () => {
    expect(nextBoardNumber([{number: 4}, {number: Number.NaN}])).toBe(5)
    expect(nextBoardNumber([{number: -3}])).toBe(1)
  })
})
