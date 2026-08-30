import {describe, expect, it} from "vitest"
import {
  directionBetween,
  litAt,
  newestSeason,
  seasonBands,
  seasonStrip,
  seasonsIncluding,
  STRIP,
} from "@/domains/esports/island/seasonAxis"

const season = (id: number, name: string, startDate: string) =>
  ({id, name, startDate, endDate: startDate}) as never

describe("seasonBands", () => {
  it("puts the oldest season at the left of the strip and the newest at the right", () => {
    const bands = seasonBands([
      season(2, "Spring 2025/26", "2026-02-01"),
      season(1, "Autumn 2025/26", "2025-09-01"),
    ])

    // Given in whatever order the api felt like; read left to right in time.
    expect(bands.map(b => b.season.id)).toEqual([1, 2])
  })

  it("gives every season an equal share of the width", () => {
    const bands = seasonBands([
      season(1, "Autumn 2024/25", "2024-09-01"),
      season(2, "Spring 2024/25", "2025-02-01"),
      season(3, "Autumn 2025/26", "2025-09-01"),
      season(4, "Spring 2025/26", "2026-02-01"),
    ])

    expect(bands.map(b => b.from)).toEqual([0, 0.25, 0.5, 0.75])
    expect(bands.map(b => b.to)).toEqual([0.25, 0.5, 0.75, 1])
  })

  it("sits a node in the middle of its own season, not on its edge", () => {
    const bands = seasonBands([
      season(1, "Autumn 2024/25", "2024-09-01"),
      season(2, "Spring 2024/25", "2025-02-01"),
    ])

    expect(bands.map(b => b.at)).toEqual([0.25, 0.75])
    // Which puts the division between them exactly halfway between the two nodes.
    expect(bands[0].to).toBe((bands[0].at + bands[1].at) / 2)
  })

  it("fills the whole strip with a single season", () => {
    const bands = seasonBands([season(1, "Autumn 2025/26", "2025-09-01")])

    expect(bands[0].from).toBe(0)
    expect(bands[0].to).toBe(1)
    expect(bands[0].at).toBe(0.5)
  })

  it("alternates which side of the middle a node sits, so the line has to bend", () => {
    const bands = seasonBands([
      season(1, "Autumn 2024/25", "2024-09-01"),
      season(2, "Spring 2024/25", "2025-02-01"),
      season(3, "Autumn 2025/26", "2025-09-01"),
    ])

    expect(bands.map(b => b.high)).toEqual([true, false, true])
  })

  it("reads the half and the year out of a season's name", () => {
    const [band] = seasonBands([season(1, "Autumn 2025/26", "2025-09-01")])

    expect(band.half).toBe("Autumn")
    expect(band.year).toBe("2025/26")
  })

  it("gives a season named some other way a band, and no year", () => {
    const [band] = seasonBands([season(1, "Summer cup", "2025-06-01")])

    expect(band.half).toBe("Summer cup")
    expect(band.year).toBe("")
  })
})

describe("seasonStrip", () => {
  const four = [
    season(1, "Autumn 2024/25", "2024-09-01"),
    season(2, "Spring 2024/25", "2025-02-01"),
    season(3, "Autumn 2025/26", "2025-09-01"),
    season(4, "Spring 2025/26", "2026-02-01"),
  ]

  it("fills the strip's width when the seasons are few", () => {
    const strip = seasonStrip(four, {width: 1200, trailing: 0})

    expect(strip.track).toBe(1200)
  })

  it("grows past the strip once a band would be narrower than it can be read at", () => {
    const many = Array.from({length: 12}, (_, i) =>
      season(i + 1, `Autumn ${2010 + i}/${11 + i}`, `${2010 + i}-09-01`))
    const strip = seasonStrip(many, {width: 1200, trailing: 0})

    // Twelve bands at the width six of them would have: the strip scrolls rather than
    // shrinking a band to a sliver.
    expect(strip.track).toBe(2400)
  })

  it("sits each node in the middle of its own band", () => {
    const strip = seasonStrip(four, {width: 1200, trailing: 0})

    expect(strip.nodes.map(n => n.x)).toEqual([150, 450, 750, 1050])
  })

  it("keeps a node in the middle of its own band when a band is reserved on the end", () => {
    const strip = seasonStrip(four, {width: 1000, trailing: 1})

    // Five shares of 200: the four seasons take the first four, and their nodes sit in the
    // middle of those rather than being squeezed by the share the plus takes.
    strip.nodes.forEach((node, index) => expect(node.x).toBeCloseTo(100 + index * 200, 6))
  })

  it("alternates the nodes above and below the middle of the strip", () => {
    const strip = seasonStrip(four, {width: 1200, trailing: 0})
    const middle = STRIP.height / 2

    expect(strip.nodes.map(n => n.y)).toEqual([
      middle - STRIP.amplitude, middle + STRIP.amplitude,
      middle - STRIP.amplitude, middle + STRIP.amplitude,
    ])
  })

  it("starts the line left of the strip, so its beginning is never in view", () => {
    const strip = seasonStrip(four, {width: 1200, trailing: 0})

    expect(strip.from).toBeLessThan(0)
    expect(strip.path.startsWith(`M ${strip.from},`)).toBe(true)
  })

  it("runs the line past the strip where nothing bounds it", () => {
    const strip = seasonStrip(four, {width: 1200, trailing: 0})

    expect(strip.seam).toBeNull()
    expect(strip.to).toBeGreaterThan(strip.track)
  })

  it("stops the line on the slant that divides the last season from the block that adds one", () => {
    const strip = seasonStrip(four, {width: 1000, trailing: 1})
    const last = strip.nodes[strip.nodes.length - 1]!

    // Four shares of 200 for the seasons, the fifth left for the plus.
    expect(strip.seam).toBe(800)
    // The division is drawn as a slanted rule, so a line that stopped at the upright
    // boundary would cross it. It stops where the rule actually is at its own height.
    const lean = Math.tan((STRIP.seam * Math.PI) / 180) * (STRIP.height / 2 - last.y)
    expect(strip.to).toBeCloseTo(800 + lean, 6)
    // The last node sits below the middle, so the rule leans away to the left of the
    // boundary there and the line has to stop short of it.
    expect(strip.to).toBeLessThan(800)
  })

  it("lights the line to the middle of the node it is asked about", () => {
    const strip = seasonStrip(four, {width: 1000, trailing: 1})

    // Not near it, and not a share of some other box: the node's own centre, in pixels.
    expect(litAt(strip.nodes, 1)).toBeCloseTo(100, 6)
    expect(litAt(strip.nodes, 3)).toBeCloseTo(500, 6)
    expect(litAt(strip.nodes, 4)).toBeCloseTo(700, 6)
  })

  it("lights nothing when it is asked about no season at all", () => {
    const strip = seasonStrip(four, {width: 1000, trailing: 1})

    expect(litAt(strip.nodes, null)).toBe(0)
    expect(litAt(strip.nodes, 99)).toBe(0)
  })

  it("draws a season on its own as one straight run", () => {
    const strip = seasonStrip([season(1, "Autumn 2025/26", "2025-09-01")], {width: 800, trailing: 0})

    expect(strip.nodes).toHaveLength(1)
    expect(strip.path).toBe(`M ${strip.from},${strip.nodes[0]!.y} L ${strip.to},${strip.nodes[0]!.y}`)
  })

  it("has nothing to draw before the strip has been measured", () => {
    const strip = seasonStrip(four, {width: 0, trailing: 0})

    expect(strip.path).toBe("")
  })

  it("has nothing to draw for no seasons at all", () => {
    const strip = seasonStrip([], {width: 1200, trailing: 1})

    expect(strip.path).toBe("")
    expect(strip.nodes).toEqual([])
  })
})

describe("newestSeason", () => {
  it("answers with the season that starts last, however the list was ordered", () => {
    const newest = newestSeason([
      season(1, "Autumn 2024/25", "2024-09-01"),
      season(3, "Autumn 2025/26", "2025-09-01"),
      season(2, "Spring 2024/25", "2025-02-01"),
    ])

    expect(newest?.id).toBe(3)
  })

  it("counts a season nobody has played yet, because it is still the season it is", () => {
    const newest = newestSeason([
      season(1, "Autumn 2025/26", "2025-09-01"),
      season(2, "Spring 2026/27", "2026-02-01"),
    ])

    expect(newest?.id).toBe(2)
  })

  it("separates two seasons that start on the same day by the order they were written down", () => {
    const newest = newestSeason([
      season(7, "Autumn 2025/26", "2025-09-01"),
      season(9, "Autumn 2025/26 again", "2025-09-01"),
    ])

    expect(newest?.id).toBe(9)
  })

  it("has no answer where there are no seasons", () => {
    expect(newestSeason([])).toBeNull()
  })
})

describe("directionBetween", () => {
  const older = season(1, "Autumn 2019/20", "2019-09-01")
  const newer = season(2, "Autumn 2025/26", "2025-09-01")

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
    season(1, "Autumn 2019/20", "2019-09-01"),
    season(2, "Spring 2019/20", "2020-02-01"),
  ]

  it("adds the season being read where the list does not carry it", () => {
    const strip = seasonsIncluding(played, season(9, "Autumn 2025/26", "2025-09-01"))

    expect(strip.map(one => one.id)).toEqual([1, 2, 9])
  })

  it("puts it where it belongs in time, not on the end", () => {
    const strip = seasonsIncluding(played, season(9, "Autumn 2018/19", "2018-09-01"))

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
