import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {reactive} from "vue"
import Esports from "@/pages/Esports.vue"
import {forgetGames} from "@/domains/esports/island/useGames"

/**
 * The season lives in the url, so the page reads one and writes one back. Reactive, because
 * the page watches it: a season chosen elsewhere — the back button, a shared link — is a
 * season change like any other.
 */
const route = reactive<{query: Record<string, string>}>({query: {}})
const replace = vi.fn(({query}: {query: Record<string, string>}) => {
  route.query = query
})

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => route,
    useRouter: () => ({replace, push: vi.fn()}),
  }
})

const seasons = [
  {id: 1, name: "Autumn 2025/26", startDate: "2025-09-01", endDate: "2026-01-31"},
  {id: 2, name: "Spring 2025/26", startDate: "2026-02-01", endDate: "2026-08-31"},
]

/** Written down, but nobody has been fielded in it, so no game's page reports it. */
const emptySeason = {id: 3, name: "Autumn 2026/27", startDate: "2026-09-01", endDate: "2027-01-31"}

// The index asks each game what it fielded; only two of them answer with a team.
const pageFor = (game: string) => ({
  game,
  season: seasons[0],
  seasons,
  teams: game === "VALORANT" || game === "CS2"
    ? [{id: 1, name: `BS ${game}`, image: null, members: [{role: "PLAYER", handle: "Someone"}]}]
    : [],
})

/** The games as their records have them: the index keeps no list of its own to fall back on. */
const games = [
  {game: "VALORANT", name: "Valorant", slug: "valorant", accent: "#ff4655", banner: null, icon: null, intro: null, sortIndex: 1, fielded: true},
  {game: "CS2", name: "Counter-Strike 2", slug: "counter-strike-2", accent: "#e8842a", banner: null, icon: null, intro: null, sortIndex: 2, fielded: true},
  {game: "LEAGUE_OF_LEGENDS", name: "League of Legends", slug: "league-of-legends", accent: "#c8963c", banner: null, icon: null, intro: null, sortIndex: 3, fielded: true},
]

vi.mock("@/domains/esports/adapters/esports", () => ({
  loadEsportsPage: vi.fn(async (game: string) => pageFor(game)),
  loadGames: vi.fn(async () => games),
  saveSeasonOrReason: vi.fn(async () => ({ok: true, season: seasons[0]})),
  // Every season written down, which is more than the games were fielded in.
  loadSeasons: vi.fn(async () => [...seasons, emptySeason]),
}))

// The page asks the store whether the reader may change esports, so every mount answers.
const mountPage = ({board = false}: {board?: boolean} = {}) =>
  mount(Esports, {
    global: {
      provide: {store: {getters: {isBoard: board}}},
      stubs: {
        RouterLink: {props: ["to"], template: "<a :data-to='to'><slot /></a>"},
        Motion: {template: "<div><slot /></div>"},
      },
    },
  })

describe("Esports page", () => {
  // The records are read once and shared, so a case that changes them must clear them first.
  beforeEach(() => {
    vi.clearAllMocks()
    forgetGames()
    route.query = {}
  })

  it("sits inside the esports island", () => {
    // The island's root is what its reset and its tokens hang off.
    expect(mountPage().find('[data-testid="esports-island"]').exists()).toBe(true)
  })

  it("offers no way to change a season to somebody who may not", async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.findAll('[data-testid^="esports-season-edit-"]')).toHaveLength(0)
  })

  it("offers the board a way to change each season it shows", async () => {
    const wrapper = mountPage({board: true})
    await flushPromises()

    // One per season on the strip, so the affordance belongs to a season rather than to the page.
    expect(wrapper.findAll('[data-testid^="esports-season-edit-"]')).toHaveLength(seasons.length + 1)
  })

  it("shows a visitor only the seasons something was fielded in", async () => {
    const wrapper = mountPage()
    await flushPromises()

    // The empty season is nothing to somebody who cannot put a team in it.
    expect(wrapper.find(`[data-testid="esports-season-node-${emptySeason.id}"]`).exists()).toBe(false)
    expect(wrapper.findAll('[data-testid^="esports-season-node-"]')).toHaveLength(seasons.length)
  })

  it("shows the board a season nothing was fielded in, so a team can be put in it", async () => {
    const wrapper = mountPage({board: true})
    await flushPromises()

    expect(wrapper.find(`[data-testid="esports-season-node-${emptySeason.id}"]`).exists()).toBe(true)
  })

  it("shows only the games that fielded a team in the season on show", async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="esports-game-VALORANT"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="esports-game-CS2"]').exists()).toBe(true)
    // Fielded nothing that season, so it is absent rather than shown empty.
    expect(wrapper.find('[data-testid="esports-game-GEOGUESSR"]').exists()).toBe(false)
  })

  it("links each game to its own page, on the season being read here", async () => {
    const wrapper = mountPage()
    await flushPromises()

    // Somebody who chose a season and then follows a game wants that game in that season.
    const targets = wrapper.findAll("a[data-to]").map(node => node.attributes("data-to"))
    expect(targets).toContain(`/esports/valorant?season=${seasons[0]!.id}`)
    expect(targets).toContain(`/esports/counter-strike-2?season=${seasons[0]!.id}`)
  })

  it("opens on the season its own url names", async () => {
    route.query = {season: String(seasons[1]!.id)}
    mountPage()
    await flushPromises()

    const {loadEsportsPage} = await import("@/domains/esports/adapters/esports")
    // Every game asked about that season rather than about whichever one is newest.
    expect(vi.mocked(loadEsportsPage).mock.calls.every(([, id]) => id === seasons[1]!.id)).toBe(true)
  })

  it("offers the seasons it was told about", async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="esports-index-seasons"]').exists()).toBe(true)
    expect(wrapper.text()).toContain("Autumn")
  })
})
