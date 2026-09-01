import {describe, expect, it} from "vitest"
import type {RouteLocationNormalizedLoaded} from "vue-router"
import {boardInRoute, boardStops} from "@/domains/boards"
import {seededBoards} from "./seed"

/** The seeded history, as the strip is handed it: newest first, the way the adapter answers. */
const seeded = () => [...seededBoards()].reverse()

const on = "2026-09-01"

describe("boardStops", () => {
  it("runs oldest to newest, whichever order the api answered in", () => {
    const stops = boardStops(seeded(), on)

    expect(stops.map(stop => stop.id)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
  })

  it("identifies a stop by the board's number rather than by its key", () => {
    // `?board=9` is the ninth board, and the strip reports the same value back when it is
    // clicked. A stop carrying a database key would put the wrong board in the url.
    const [stop] = boardStops([{number: 4, name: null, startDate: "2020-09-01", endDate: "2021-08-31"}], on)

    expect(stop!.id).toBe(4)
  })

  it("labels a stop with the board's own name and the academic year beneath it", () => {
    const stops = boardStops(seeded(), on)
    const ninth = stops.find(stop => stop.id === 9)

    expect(ninth!.label).toBe("Eeveelutions")
    expect(ninth!.sublabel).toBe("2025-2026")
  })

  it("names a board that has none from its number, so no stop reads as blank", () => {
    const [stop] = boardStops([{number: 4, name: "", startDate: "2020-09-01", endDate: "2021-08-31"}], on)

    expect(stop!.label).toBe("Board IV")
  })

  it("marks the board in office and the one that has not taken office yet", () => {
    const marks = boardStops(seeded(), on).map(stop => [stop.id, stop.mark])

    expect(marks).toEqual([
      [1, ""], [2, ""], [3, ""], [4, ""], [5, ""], [6, ""], [7, ""], [8, ""],
      [9, "In office"],
      [10, "Candidate"],
    ])
  })

  it("moves the mark on when the tenth board takes office", () => {
    const marks = boardStops(seeded(), "2026-09-17")
      .filter(stop => stop.mark !== "")
      .map(stop => [stop.id, stop.mark])

    expect(marks).toEqual([[10, "In office"]])
  })

  it("speaks a stop as its name, its year and its mark, since all three are drawn for the eye", () => {
    const stops = boardStops(seeded(), on)

    expect(stops.find(stop => stop.id === 9)!.name).toBe("Eeveelutions, 2025-2026, in office")
    expect(stops.find(stop => stop.id === 10)!.name).toBe("Rainbow road, 2026-2027, candidate")
    expect(stops.find(stop => stop.id === 3)!.name).toBe("Drieden, 2019-2020")
  })
})

const route = (query: Record<string, string | string[]>) =>
  ({query}) as RouteLocationNormalizedLoaded

describe("boardInRoute", () => {
  it("reads the board a url names", () => {
    expect(boardInRoute(route({board: "9"}))).toBe(9)
  })

  it("reads nothing where the url names no board, which is where the page opens on the sitting one", () => {
    expect(boardInRoute(route({}))).toBeNull()
    expect(boardInRoute(route({board: ""}))).toBeNull()
    expect(boardInRoute(route({season: "3"}))).toBeNull()
  })

  it("reads nothing out of a value that is not a board's number", () => {
    expect(boardInRoute(route({board: "nine"}))).toBeNull()
    expect(boardInRoute(route({board: "0"}))).toBeNull()
    expect(boardInRoute(route({board: "-2"}))).toBeNull()
    expect(boardInRoute(route({board: "9.5"}))).toBeNull()
  })

  it("takes the first of a query repeated, which is what a hand-written url does", () => {
    expect(boardInRoute(route({board: ["7", "8"]}))).toBe(7)
  })
})
