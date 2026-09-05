import {beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, ref, type Ref} from "vue"
import {mount} from "@vue/test-utils"
import {forgetSeasonLineups, useSeasonLineup} from "@/domains/esports/island/useSeasonLineup"
import {forgetSeasons} from "@/domains/esports/island/useSeasons"
import {loadSeasonGames, loadSeasons, type SeasonGame} from "@/domains/esports/adapters/esports"
import {settle} from "../../../helpers/testUtils"

vi.mock("@/domains/esports/adapters/esports", () => ({
  loadSeasonGames: vi.fn(),
  loadSeasons: vi.fn(),
}))

const SPRING = {id: 19, name: "Spring 2025", startDate: "2025-02-01", endDate: "2025-08-31", played: true}
const AUTUMN = {id: 20, name: "Autumn 2025", startDate: "2025-09-01", endDate: "2026-01-31", played: false}

const fielded = (game: string): SeasonGame =>
  ({game, public: true, teams: [{id: 1, name: "BS Waterboarders", members: []}]}) as never

const open = async (route: Ref<number | null>) => {
  let api!: ReturnType<typeof useSeasonLineup>
  mount(defineComponent({
    setup() {
      api = useSeasonLineup(() => route.value)
      return () => h("div")
    },
  }))
  await settle()
  return api
}

beforeEach(() => {
  vi.clearAllMocks()
  forgetSeasons()
  forgetSeasonLineups()
  vi.mocked(loadSeasons).mockResolvedValue([SPRING, AUTUMN])
  vi.mocked(loadSeasonGames).mockResolvedValue([fielded("VAL")])
})

describe("useSeasonLineup", () => {
  it("reads the season the url names, and what it fielded", async () => {
    const api = await open(ref<number | null>(19))

    expect(loadSeasonGames).toHaveBeenCalledWith(19)
    expect(api.selected.value).toBe(19)
    expect(api.entries.value.map(one => one.game)).toEqual(["VAL"])
  })

  it("opens on the association's newest season where the url names none", async () => {
    const api = await open(ref<number | null>(null))

    expect(api.selected.value).toBe(20)
  })

  // A season nobody played is not one to arrive on, but the season being read has to have a node
  // to stand on: a strip with nothing lit says the visitor is nowhere.
  it("offers the seasons something was played in, with the one being read among them", async () => {
    const api = await open(ref<number | null>(20))

    expect(api.seasons.value.map(one => one.id)).toEqual([19, 20])
  })

  it("shows nothing where the association has recorded no seasons at all", async () => {
    vi.mocked(loadSeasons).mockResolvedValue([])

    const api = await open(ref<number | null>(null))

    expect(loadSeasonGames).not.toHaveBeenCalled()
    expect(api.entries.value).toEqual([])
    expect(api.seasons.value).toEqual([])
  })

  it("declines to re-read the season already shown, which is what reload is for", async () => {
    const api = await open(ref<number | null>(19))

    await api.show(19)
    expect(vi.mocked(loadSeasonGames).mock.calls.length).toBe(1)

    await api.reload(19)

    expect(vi.mocked(loadSeasonGames).mock.calls.length).toBe(2)
  })

  // What was asked ahead is what the arrival reads, so walking the strip costs one read a season.
  it("asks about a season once, however many readings want it", async () => {
    const api = await open(ref<number | null>(19))

    api.askAhead(20)
    await settle()
    await api.show(20)

    expect(vi.mocked(loadSeasonGames).mock.calls.filter(call => call[0] === 20).length).toBe(1)
  })

  // Nothing and an empty answer are drawn differently: one is still loading, the other is a
  // season that fielded nobody.
  it("says nothing at all for a season nobody has asked about, and says quiet for one that was", async () => {
    vi.mocked(loadSeasonGames).mockResolvedValue([])
    const api = await open(ref<number | null>(19))

    expect(api.answerFor(20)).toBeUndefined()
    expect(api.answerFor(19)).toEqual([])
  })

  it("follows a season the url changes to", async () => {
    const route = ref<number | null>(19)
    const api = await open(route)

    route.value = 20
    await settle()

    expect(api.selected.value).toBe(20)
  })
})
