import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import Esports from "@/pages/Esports.vue"

const seasons = [
  {id: 1, name: "Autumn 2025/26", startDate: "2025-09-01", endDate: "2026-01-31"},
  {id: 2, name: "Spring 2025/26", startDate: "2026-02-01", endDate: "2026-08-31"},
]

// The index asks each game what it fielded; only two of them answer with a team.
const pageFor = (game: string) => ({
  game,
  season: seasons[0],
  seasons,
  teams: game === "VALORANT" || game === "CS2"
    ? [{id: 1, name: `BS ${game}`, image: null, members: [{role: "PLAYER", handle: "Someone"}]}]
    : [],
})

vi.mock("@/domains/esports/adapters/esports", () => ({
  loadEsportsPage: vi.fn(async (game: string) => pageFor(game)),
}))

const mountPage = () =>
  mount(Esports, {
    global: {
      stubs: {
        RouterLink: {props: ["to"], template: "<a :data-to='to'><slot /></a>"},
        Motion: {template: "<div><slot /></div>"},
      },
    },
  })

describe("Esports page", () => {
  beforeEach(() => vi.clearAllMocks())

  it("sits inside the esports island", () => {
    // The island's root is what its reset and its tokens hang off.
    expect(mountPage().find('[data-testid="esports-island"]').exists()).toBe(true)
  })

  it("shows only the games that fielded a team in the season on show", async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="esports-game-VALORANT"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="esports-game-CS2"]').exists()).toBe(true)
    // Fielded nothing that season, so it is absent rather than shown empty.
    expect(wrapper.find('[data-testid="esports-game-GEOGUESSR"]').exists()).toBe(false)
  })

  it("links each game to its own page, where every season of it lives", async () => {
    const wrapper = mountPage()
    await flushPromises()

    const targets = wrapper.findAll("a[data-to]").map(node => node.attributes("data-to"))
    expect(targets).toContain("/esports/valorant")
    expect(targets).toContain("/esports/counter-strike-2")
  })

  it("offers the seasons it was told about", async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="esports-index-seasons"]').exists()).toBe(true)
    expect(wrapper.text()).toContain("Autumn")
  })
})
