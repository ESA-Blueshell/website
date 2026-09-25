import {describe, expect, it, vi} from "vitest"
import {useTeamToEdit} from "@/domains/esports"
import {loadEsportsPage} from "@/domains/esports/adapters/esports"

vi.mock("@/domains/esports/adapters/esports", async importOriginal => ({
  ...(await importOriginal<typeof import("@/domains/esports/adapters/esports")>()),
  loadEsportsPage: vi.fn(),
}))

const season = {id: 4, name: "Spring 2026", startDate: "2026-02-01", endDate: "2026-06-30", played: true}

describe("what a team's edit page reads", () => {
  it("reads the season, the teams fielded in it and the one being edited", async () => {
    vi.mocked(loadEsportsPage).mockResolvedValue({game: "VAL", season, seasons: [season], teams: [{id: 9, name: "Blueshell"}, {id: 10, name: "Two"}]} as never)

    const read = useTeamToEdit("VAL", 9, 4)
    await read.answered

    expect(loadEsportsPage).toHaveBeenCalledWith("VAL", 4)
    expect(read.season.value).toEqual(season)
    expect(read.fielded.value.map(one => one.id)).toEqual([9, 10])
    expect(read.team.value?.name).toBe("Blueshell")
  })

  it("reads the newest season where none is asked for, and nothing where the game could not be read", async () => {
    vi.mocked(loadEsportsPage).mockResolvedValue(null)

    const read = useTeamToEdit("VAL", null, null)
    await read.answered

    expect(loadEsportsPage).toHaveBeenLastCalledWith("VAL", undefined)
    expect(read.season.value).toBeNull()
    expect(read.fielded.value).toEqual([])
    expect(read.team.value).toBeNull()
  })
})
