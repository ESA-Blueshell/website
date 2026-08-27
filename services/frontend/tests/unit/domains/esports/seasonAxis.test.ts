import {describe, expect, it} from "vitest"
import {seasonBands} from "@/domains/esports/island/seasonAxis"

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
