import {describe, expect, it} from "vitest"
import {boardInOffice, isCandidate, standingOf, type Termed} from "@/domains/boards"
import {seededBoards} from "./seed"

/**
 * Which board is in office, read out of the dates.
 *
 * Tested against the seeded history rather than against invented boards wherever it can be,
 * because the shape that broke this rule is real: the ninth board's term runs to the sixteenth of
 * September and the tenth's opens on the seventeenth, so for one day nobody is in office and the
 * day after that the tenth board is a candidate no longer. A test with tidy terms — the first of
 * September to the thirty-first of August — never meets either case.
 */
const boards = (): Termed[] => seededBoards().map(board => ({
  number: board.number,
  startDate: board.startDate,
  endDate: board.endDate,
}))

/** The tenth board's term, which is a candidate's on every day before the seventeenth. */
const TENTH = {number: 10, startDate: "2026-09-17", endDate: "2027-08-31"}
const NINTH = {number: 9, startDate: "2025-09-01", endDate: "2026-09-16"}

describe("a board that has not taken office", () => {
  it("is a candidate on every day before its term opens", () => {
    expect(isCandidate(TENTH, "2026-09-01")).toBe(true)
    expect(isCandidate(TENTH, "2026-09-16")).toBe(true)
  })

  it("stops being one on the day its term opens", () => {
    expect(isCandidate(TENTH, "2026-09-17")).toBe(false)
    expect(isCandidate(TENTH, "2027-01-01")).toBe(false)
  })

  it("is never what a reader is shown, however new it is", () => {
    // The whole reason the rule exists: the tenth board is the newest board recorded and the
    // ninth is the one running the association.
    expect(boardInOffice(boards(), "2026-09-01")?.number).toBe(9)
  })

  it("is a candidate whatever else has been written down", () => {
    expect(standingOf(TENTH, boards(), "2026-09-01")).toBe("candidate")
  })
})

describe("the board in office", () => {
  it("is the one whose term contains today", () => {
    expect(boardInOffice(boards(), "2025-09-01")?.number).toBe(9)
    expect(boardInOffice(boards(), "2026-09-16")?.number).toBe(9)
    expect(boardInOffice(boards(), "2026-09-17")?.number).toBe(10)
    expect(boardInOffice(boards(), "2019-12-24")?.number).toBe(3)
    expect(boardInOffice(boards(), "2021-09-01")?.number).toBe(5)
  })

  it("is the newest board that has taken office where no term contains today", () => {
    // Every board the seed records hands over the day before the next takes office, so the gap
    // is the autumn after the last board written down: the tenth board has handed over and
    // nobody has recorded the eleventh. Somebody arriving then is asking who runs the
    // association, and the answer is the board that has been running it.
    expect(boardInOffice(boards(), "2027-09-01")?.number).toBe(10)

    // And the same gap in the middle of the history: a board whose end date was written as the
    // end of August, and the board after it not yet written down at all.
    const between = [NINTH, {number: 1, startDate: "2017-09-01", endDate: "2018-08-31"}]
    expect(boardInOffice(between, "2026-09-30")?.number).toBe(9)
  })

  it("is nothing at all before the association had one", () => {
    expect(boardInOffice(boards(), "2016-01-01")).toBeNull()
  })

  it("is nothing where every board recorded is still a candidate", () => {
    expect(boardInOffice([TENTH], "2026-09-01")).toBeNull()
  })

  it("is nothing where there are no boards", () => {
    expect(boardInOffice([], "2026-09-01")).toBeNull()
  })

  it("keeps running after its term where nothing has replaced it", () => {
    // The last board recorded, past its end date, with no candidate behind it. That is what a
    // page shows in the autumn nobody got round to writing the next board down in.
    expect(boardInOffice([NINTH], "2026-10-01")?.number).toBe(9)
    expect(standingOf(NINTH, [NINTH], "2026-10-01")).toBe("in office")
  })

  it("is the one whose term is still open where no handover was written down", () => {
    const open = {number: 11, startDate: "2027-09-01", endDate: null}
    expect(boardInOffice([...boards(), open], "2028-05-01")?.number).toBe(11)
  })
})

describe("where a board stands", () => {
  it("marks exactly one board in office, on every day the association has existed", () => {
    const days = ["2018-01-01", "2021-09-01", "2024-08-31", "2026-09-01", "2026-09-17"]
    for (const day of days) {
      const standings = boards().map(board => standingOf(board, boards(), day))
      expect(standings.filter(one => one === "in office"), `on ${day}`).toHaveLength(1)
    }
  })

  it("puts every board that has handed over in the past", () => {
    const past = boards()
      .filter(board => board.number < 9)
      .map(board => standingOf(board, boards(), "2026-09-01"))
    expect(new Set(past)).toEqual(new Set(["past"]))
  })

  it("reads the same answer whichever board asks it", () => {
    // The standing of one board depends on the whole line, so the answers have to agree: a page
    // that marked two boards in office would be reading the rule one board at a time.
    const day = "2026-09-17"
    const sitting = boardInOffice(boards(), day)
    expect(standingOf(sitting!, boards(), day)).toBe("in office")
  })
})

describe("dates that are not dates", () => {
  it("leaves a board with no start date out of the reckoning", () => {
    // An undated board cannot be shown to be running the association, so it is passed over
    // rather than answered with — a broken record would otherwise outrank every dated board.
    const undated = {number: 12, startDate: "", endDate: ""}
    expect(isCandidate(undated, "2026-09-01")).toBe(false)
    expect(boardInOffice([undated], "2026-09-01")).toBeNull()
    expect(boardInOffice([undated, NINTH], "2026-10-01")?.number).toBe(9)
  })

  it("reads a stored timestamp as the day it falls on", () => {
    const stamped = {number: 9, startDate: "2025-09-01T00:00:00Z", endDate: "2026-09-16T23:59:59Z"}
    expect(boardInOffice([stamped], "2026-09-16")?.number).toBe(9)
    expect(isCandidate(stamped, "2025-08-31")).toBe(true)
  })
})
