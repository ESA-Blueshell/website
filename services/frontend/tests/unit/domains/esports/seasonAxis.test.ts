import {describe, expect, it} from "vitest"
import {seasonAxis} from "@/domains/esports/island/seasonAxis"

const season = (id: number, name: string, startDate: string) =>
  ({id, name, startDate, endDate: startDate}) as never

describe("seasonAxis", () => {
  it("puts the oldest season at the start of the line and the newest at the end", () => {
    const {nodes} = seasonAxis([
      season(2, "Spring 2025/26", "2026-02-01"),
      season(1, "Autumn 2025/26", "2025-09-01"),
    ])

    // Given in the order the api felt like; read left to right in time.
    expect(nodes.map(n => n.season.id)).toEqual([1, 2])
    expect(nodes[0].at).toBe(0)
    expect(nodes[1].at).toBe(1)
  })

  it("spaces the nodes evenly along the line", () => {
    const {nodes} = seasonAxis([
      season(1, "Autumn 2024/25", "2024-09-01"),
      season(2, "Spring 2024/25", "2025-02-01"),
      season(3, "Autumn 2025/26", "2025-09-01"),
    ])

    expect(nodes.map(n => n.at)).toEqual([0, 0.5, 1])
  })

  it("centres a single season rather than pinning it to one end", () => {
    const {nodes} = seasonAxis([season(1, "Autumn 2025/26", "2025-09-01")])

    expect(nodes[0].at).toBe(0.5)
  })

  it("reads the half and the year out of a season's name", () => {
    const {nodes} = seasonAxis([season(1, "Autumn 2025/26", "2025-09-01")])

    expect(nodes[0].half).toBe("Autumn")
    expect(nodes[0].year).toBe("2025/26")
  })

  it("gives a season named some other way a node, and no year to group under", () => {
    const {nodes, years} = seasonAxis([season(1, "Summer cup", "2025-06-01")])

    expect(nodes).toHaveLength(1)
    expect(nodes[0].half).toBe("Summer cup")
    expect(years).toHaveLength(0)
  })

  it("centres a year over the seasons it covers", () => {
    const {years} = seasonAxis([
      season(1, "Autumn 2024/25", "2024-09-01"),
      season(2, "Spring 2024/25", "2025-02-01"),
      season(3, "Autumn 2025/26", "2025-09-01"),
      season(4, "Spring 2025/26", "2026-02-01"),
    ])

    expect(years.map(y => y.year)).toEqual(["2024/25", "2025/26"])
    // Each label sits between its own two halves rather than over one of them.
    expect(years[0].at).toBeCloseTo(1 / 6)
    expect(years[1].at).toBeCloseTo(5 / 6)
  })

  it("does not reorder the years when the seasons arrive shuffled", () => {
    const {years} = seasonAxis([
      season(4, "Spring 2025/26", "2026-02-01"),
      season(1, "Autumn 2024/25", "2024-09-01"),
      season(3, "Autumn 2025/26", "2025-09-01"),
      season(2, "Spring 2024/25", "2025-02-01"),
    ])

    expect(years.map(y => y.year)).toEqual(["2024/25", "2025/26"])
  })
})
