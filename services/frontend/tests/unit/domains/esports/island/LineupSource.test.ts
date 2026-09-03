import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import LineupSource from "@/domains/esports/island/LineupSource.vue"
import {loadRoster, loadTeamSeasons, loadTeams} from "@/domains/esports/adapters/esports"
import {settle} from "../../../helpers/testUtils"

vi.mock("@/domains/esports/adapters/esports", () => ({
  loadRoster: vi.fn(),
  loadTeamSeasons: vi.fn(),
  loadTeams: vi.fn(),
}))

const team = {id: 7, name: "Blueshell"}
const fielding = {game: "VAL", season: {id: 2, name: "2024/25", startDate: "2024-09-01", endDate: "2025-08-31"}}

const open = async () => {
  const wrapper = mount(LineupSource, {
    props: {game: "VAL", teamId: 7, seasonId: 3},
    global: {stubs: {IslandPicker: true}},
  })
  await settle()
  return wrapper
}

describe("LineupSource", () => {
  beforeEach(() => {
    vi.mocked(loadTeams).mockResolvedValue([team] as never)
    vi.mocked(loadTeamSeasons).mockResolvedValue([fielding] as never)
  })

  it("offers the people it read", async () => {
    vi.mocked(loadRoster).mockResolvedValue([
      {id: 1, handle: "nova", role: "PLAYER", sortIndex: 0, displayName: null},
    ] as never)

    const wrapper = await open()

    expect(wrapper.find('[data-testid="lineup-source-people"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="lineup-source-unknown"]').exists()).toBe(false)
  })

  it("says a line-up that could not be read is unread, not that nobody is on it", async () => {
    vi.mocked(loadRoster).mockResolvedValue(null)

    const wrapper = await open()

    expect(wrapper.find('[data-testid="lineup-source-unknown"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="lineup-source-people"]').exists()).toBe(false)
    // The notice is only what the reader sees; `unread` is what stops the parent writing.
    const carried = wrapper.emitted("update:carried") as Array<[{unread: boolean}]>
    expect(carried.at(-1)![0].unread).toBe(true)
  })

  it("carries nobody, and says nothing, from a line-up that really is empty", async () => {
    vi.mocked(loadRoster).mockResolvedValue([] as never)

    const wrapper = await open()

    expect(wrapper.find('[data-testid="lineup-source-unknown"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="lineup-source-people"]').exists()).toBe(false)
    const carried = wrapper.emitted("update:carried") as Array<[{unread: boolean}]>
    expect(carried.at(-1)![0].unread).toBe(false)
  })
})
