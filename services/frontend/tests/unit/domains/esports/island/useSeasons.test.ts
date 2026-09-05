import {beforeEach, describe, expect, it, vi} from "vitest"
import {forgetSeasons, useSeasons} from "@/domains/esports/island/useSeasons"
import {loadSeasons} from "@/domains/esports/adapters/esports"

vi.mock("@/domains/esports/adapters/esports", () => ({
  loadSeasons: vi.fn(),
}))

const SPRING = {id: 19, name: "Spring 2025", startDate: "2025-02-01", endDate: "2025-08-31"}
const AUTUMN = {id: 20, name: "Autumn 2025", startDate: "2025-09-01", endDate: "2026-01-31"}

const read = async () => {
  const seasons = useSeasons()
  await seasons.ready
  return seasons
}

beforeEach(() => {
  vi.clearAllMocks()
  forgetSeasons()
  vi.mocked(loadSeasons).mockResolvedValue([SPRING, AUTUMN])
})

describe("useSeasons", () => {
  // Newest by start date whatever was fielded in it: a season written down ahead of time is the
  // association's current season the moment it exists.
  it("names the newest season by when it starts, whatever order they were read in", async () => {
    vi.mocked(loadSeasons).mockResolvedValue([AUTUMN, SPRING])

    const seasons = await read()

    expect(seasons.newest.value?.id).toBe(20)
  })

  it("names no season at all before any has been recorded", async () => {
    vi.mocked(loadSeasons).mockResolvedValue([])

    const seasons = await read()

    expect(seasons.newest.value).toBeNull()
  })

  // Both esports pages need this before they can ask anything else, and asking twice is the same
  // answer twice.
  it("reads the seasons once, however many pages ask for them", async () => {
    await read()
    await read()

    expect(vi.mocked(loadSeasons).mock.calls.length).toBe(1)
  })

  it("writes a season taken away back into what every page reads", async () => {
    const seasons = await read()
    vi.mocked(loadSeasons).mockResolvedValue([SPRING])

    await seasons.refresh()

    expect(seasons.seasons.value.map(one => one.id)).toEqual([19])
    expect(seasons.newest.value?.id).toBe(19)
  })
})
