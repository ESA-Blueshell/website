import {describe, expect, it} from "vitest"
import {academicYear, boardEyebrow, boardName, romanNumeral} from "@/domains/boards"
import {seededBoards} from "./seed"

/**
 * One to twenty, because the association has ten boards and keeps electing them, plus the places
 * a numeral changes shape further out than any board will reach soon.
 */
const NUMERALS: [number, string][] = [
  [1, "I"],
  [2, "II"],
  [3, "III"],
  [4, "IV"],
  [5, "V"],
  [6, "VI"],
  [7, "VII"],
  [8, "VIII"],
  [9, "IX"],
  [10, "X"],
  [11, "XI"],
  [12, "XII"],
  [13, "XIII"],
  [14, "XIV"],
  [15, "XV"],
  [16, "XVI"],
  [17, "XVII"],
  [18, "XVIII"],
  [19, "XIX"],
  [20, "XX"],
  [24, "XXIV"],
  [39, "XXXIX"],
  [40, "XL"],
  [49, "XLIX"],
  [50, "L"],
  [89, "LXXXIX"],
  [90, "XC"],
  [99, "XCIX"],
  [100, "C"],
]

/** Numbers no board has, which still have to read as something true. */
const NOT_BOARDS: [number, string][] = [
  [0, "0"],
  [-4, "-4"],
  [1.5, "1.5"],
  [Number.NaN, "NaN"],
]

const FALLBACK_NAMES: [number, string][] = [
  [4, "Board IV"],
  [9, "Board IX"],
  [10, "Board X"],
]

const YEARS: [string, string | null | undefined, string][] = [
  // The real terms, from the seed. A board changes in the autumn, so a term spans two years.
  ["2025-09-01", "2026-09-16", "2025-2026"],
  ["2017-09-01", "2018-08-31", "2017-2018"],
  ["2026-09-17", "2027-08-31", "2026-2027"],
  // A board that has not handed over, or whose handover nobody wrote down.
  ["2025-09-01", null, "2025-2026"],
  ["2025-09-01", "", "2025-2026"],
  ["2025-09-01", undefined, "2025-2026"],
  // A term that closed inside its own opening year still ran that board year.
  ["2025-09-01", "2025-12-20", "2025-2026"],
  // Dates that say the board ran two years read as the stretch they say.
  ["2025-09-01", "2027-08-31", "2025-2027"],
  // A timestamp names its year the way a date does.
  ["2025-09-01T00:00:00Z", "2026-09-16T00:00:00Z", "2025-2026"],
]

describe("romanNumeral", () => {
  it.each(NUMERALS)("writes board %i as %s", (number, numeral) => {
    expect(romanNumeral(number), `board ${number} reads wrong`).toBe(numeral)
  })

  it("writes every board the association has as a numeral", () => {
    const written = seededBoards().map(board => ({number: board.number, numeral: romanNumeral(board.number)}))

    expect(
      written.filter(one => !/^[IVXLCDM]+$/.test(one.numeral)),
      "a seeded board's number did not read as a numeral",
    ).toEqual([])
    expect(written.find(one => one.number === 9)?.numeral).toBe("IX")
    expect(written.find(one => one.number === 10)?.numeral).toBe("X")
  })

  it.each(NOT_BOARDS)("falls back to the digits for %s, which no board is", (number, digits) => {
    expect(romanNumeral(number), `${number} should read as its digits`).toBe(digits)
  })
})

describe("boardName", () => {
  it("calls a board by the name it chose for itself", () => {
    expect(boardName(9, "Eeveelutions")).toBe("Eeveelutions")
  })

  it.each(FALLBACK_NAMES)("names board %i from its numeral where it recorded none", (number, name) => {
    expect(boardName(number, null), `board ${number} fell back wrong`).toBe(name)
  })

  it("counts a blank recorded name as none, which is how none arrives over the wire", () => {
    expect(boardName(4, "")).toBe("Board IV")
    expect(boardName(4, "   ")).toBe("Board IV")
  })

  it("names every seeded board, so none of them reads as nameless", () => {
    const named = seededBoards().map(board => boardName(board.number, board.name))

    expect(named.filter(one => one.trim() === ""), "a board read as nameless").toEqual([])
    // Every board in the seed happens to have chosen a name, so none of these is a fallback.
    expect(named).toContain("Eeveelutions")
    expect(named).toContain("Drieden")
  })
})

describe("academicYear", () => {
  it.each(YEARS)("reads %s to %s as %s", (startDate, endDate, year) => {
    expect(academicYear(startDate, endDate), `${startDate} to ${endDate} read wrong`).toBe(year)
  })

  it.each(["", "not a date", "20xx-09-01"])(
    "says nothing where the start date reads as %s",
    startDate => {
      expect(academicYear(startDate, "2026-09-16"), `'${startDate}' should read as nothing`).toBe("")
    },
  )

  it("reads every seeded board as the year it took office in and the year after", () => {
    const read = seededBoards().map(board => {
      const year = Number(board.startDate.slice(0, 4))
      return {
        number: board.number,
        year: academicYear(board.startDate, board.endDate),
        expected: `${year}-${year + 1}`,
      }
    })

    expect(
      read.filter(one => one.year !== one.expected),
      "a seeded board's term does not read as one academic year",
    ).toEqual([])
  })

  it("is derived, so a corrected end date cannot leave the year disagreeing with it", () => {
    // The ninth board's handover slipped a fortnight. The year it ran did not change.
    expect(academicYear("2025-09-01", "2026-08-31")).toBe(academicYear("2025-09-01", "2026-09-16"))
  })
})

describe("boardEyebrow", () => {
  it("names the board and the year it ran", () => {
    expect(boardEyebrow(9, "2025-09-01", "2026-09-16")).toBe("BOARD IX · 2025-2026")
  })

  it("names a board with no handover recorded", () => {
    expect(boardEyebrow(10, "2026-09-17", null)).toBe("BOARD X · 2026-2027")
  })

  it("drops the year and its separator where the dates cannot be read", () => {
    expect(boardEyebrow(9, "")).toBe("BOARD IX")
  })
})
