import {describe, expect, it} from "vitest"
import {
  directionBetween,
  newestSeason,
  seasonStops,
  seasonsEitherSide,
  seasonsIncluding,
} from "@/domains/esports/island/seasonAxis"

const season = (id: number, name: string, startDate: string) =>
  ({id, name, startDate, endDate: startDate}) as never

describe("seasonStops", () => {
  it("puts the oldest season at the left of the strip and the newest at the right", () => {
    const stops = seasonStops([
      season(2, "Spring 2026", "2026-02-01"),
      season(1, "Autumn 2025", "2025-09-01"),
    ])

    // Given in whatever order the api felt like; read left to right in time.
    expect(stops.map(one => one.id)).toEqual([1, 2])
  })

  it("reads the half and the year out of a season's name", () => {
    const [stop] = seasonStops([season(1, "Autumn 2025", "2025-09-01")])

    expect(stop!.label).toBe("Autumn")
    expect(stop!.sublabel).toBe("2025")
  })

  it("gives a season named some other way a stop, and no year", () => {
    const [stop] = seasonStops([season(1, "Kick-off cup", "2025-06-01")])

    expect(stop!.label).toBe("Kick-off cup")
    expect(stop!.sublabel).toBe("")
  })

  it("names a stop for the whole season, which is what a reader is told it is", () => {
    const [stop] = seasonStops([season(1, "Autumn 2025", "2025-09-01")])

    expect(stop!.name).toBe("Autumn 2025")
  })
})

describe("newestSeason", () => {
  it("answers with the season that starts last, however the list was ordered", () => {
    const newest = newestSeason([
      season(1, "Autumn 2024", "2024-09-01"),
      season(3, "Autumn 2025", "2025-09-01"),
      season(2, "Spring 2025", "2025-02-01"),
    ])

    expect(newest?.id).toBe(3)
  })

  it("counts a season nobody has played yet, because it is still the season it is", () => {
    const newest = newestSeason([
      season(1, "Autumn 2025", "2025-09-01"),
      season(2, "Spring 2027", "2026-02-01"),
    ])

    expect(newest?.id).toBe(2)
  })

  it("separates two seasons that start on the same day by the order they were written down", () => {
    const newest = newestSeason([
      season(7, "Autumn 2025", "2025-09-01"),
      season(9, "Autumn 2025 again", "2025-09-01"),
    ])

    expect(newest?.id).toBe(9)
  })

  it("has no answer where there are no seasons", () => {
    expect(newestSeason([])).toBeNull()
  })
})

describe("directionBetween", () => {
  const older = season(1, "Autumn 2019", "2019-09-01")
  const newer = season(2, "Autumn 2025", "2025-09-01")

  it("travels to the past towards a season that started earlier", () => {
    expect(directionBetween(newer, older)).toBe("past")
  })

  it("travels to the future towards a season that started later", () => {
    expect(directionBetween(older, newer)).toBe("future")
  })

  it("goes nowhere between a season and itself", () => {
    expect(directionBetween(newer, newer)).toBe("same")
  })

  // A page arriving has no season to have come from, so there is nothing for it to travel.
  it("goes nowhere when either end is missing", () => {
    expect(directionBetween(null, newer)).toBe("same")
    expect(directionBetween(older, null)).toBe("same")
  })
})

describe("seasonsIncluding", () => {
  const played = [
    season(1, "Autumn 2019", "2019-09-01"),
    season(2, "Spring 2020", "2020-02-01"),
  ]

  it("adds the season being read where the list does not carry it", () => {
    const strip = seasonsIncluding(played, season(9, "Autumn 2025", "2025-09-01"))

    expect(strip.map(one => one.id)).toEqual([1, 2, 9])
  })

  it("puts it where it belongs in time, not on the end", () => {
    const strip = seasonsIncluding(played, season(9, "Autumn 2018", "2018-09-01"))

    expect(strip.map(one => one.id)).toEqual([9, 1, 2])
  })

  it("leaves a list that already carries it alone", () => {
    const strip = seasonsIncluding(played, played[1])

    expect(strip.map(one => one.id)).toEqual([1, 2])
  })

  it("reads a list on its own where no season is being shown", () => {
    expect(seasonsIncluding(played, null).map(one => one.id)).toEqual([1, 2])
  })
})

describe("seasonsEitherSide", () => {
  const strip = [
    season(1, "Autumn 2019", "2019-09-01"),
    season(2, "Spring 2020", "2020-02-01"),
    season(3, "Autumn 2020", "2020-09-01"),
  ]

  it("names the season before one and the season after it", () => {
    const sides = seasonsEitherSide(strip, strip[1]!)

    expect(sides.past?.id).toBe(1)
    expect(sides.future?.id).toBe(3)
  })

  it("gives the oldest season nothing behind it, so a gesture that way has nowhere to go", () => {
    expect(seasonsEitherSide(strip, strip[0]!).past).toBeNull()
  })

  it("gives the newest season nothing ahead of it", () => {
    expect(seasonsEitherSide(strip, strip[2]!).future).toBeNull()
  })

  it("answers in time rather than in the order the api listed them", () => {
    const sides = seasonsEitherSide([strip[2]!, strip[0]!, strip[1]!], strip[1]!)

    expect(sides.past?.id).toBe(1)
    expect(sides.future?.id).toBe(3)
  })

  it("counts the season being read even where the list does not carry it", () => {
    // A game's page stands on the association's newest season whether or not that game played
    // it, and the seasons it did play are what lie either side of where it is standing.
    const sat = season(9, "Spring 2020", "2020-02-15")
    const sides = seasonsEitherSide([strip[0]!, strip[2]!], sat)

    expect(sides.past?.id).toBe(1)
    expect(sides.future?.id).toBe(3)
  })

  it("has no sides at all where no season is being read", () => {
    expect(seasonsEitherSide(strip, null)).toEqual({past: null, future: null})
  })
})
