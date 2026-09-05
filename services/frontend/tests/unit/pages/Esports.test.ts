import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {reactive} from "vue"
import Esports from "@/pages/Esports.vue"
// Statically, so the test does not spend its own timeout transforming the module.
import {loadSeasonGames} from "@/domains/esports/adapters/esports"
import {forgetSeasonLineups} from "@/domains/esports/island/useSeasonLineup"
import {forgetGames} from "@/domains/esports/island/useGames"
import {mountInApp} from "./helpers"

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
  {id: 1, name: "Autumn 2025", startDate: "2025-09-01", endDate: "2026-01-31", played: true},
  {id: 2, name: "Spring 2026", startDate: "2026-02-01", endDate: "2026-08-31", played: true},
]

/**
 * Written down, but nobody was ever fielded in it, so a visitor's strip does not carry it.
 *
 * Older than the two that were played rather than newer. The shown season is always on the
 * strip -- that is how a reader sees where they are -- so an empty season that is also the
 * newest would be carried anyway, and the rule this asserts would go untested.
 */
const emptySeason = {
  id: 3, name: "Autumn 2024", startDate: "2024-09-01", endDate: "2025-01-31", played: false,
}

/**
 * The band is one read: the games of a season, and what each fielded.
 *
 * Which of them come back is the api's answer rather than this page's filtering — a game
 * entered with nobody in it is answered to somebody who may edit and to nobody else — so the
 * stub answers with the two that have teams, exactly as the api would for a visitor.
 */
const seasonGames = [
  {
    game: "VALORANT",
    teams: [{id: 1, name: "BS VALORANT", members: [{role: "PLAYER", handle: "Someone"}]}],
    public: true,
  },
  {
    game: "CS2",
    teams: [{id: 2, name: "BS CS2", members: [{role: "PLAYER", handle: "Someone"}]}],
    public: true,
  },
]

/** The season the page opens on when the url names none: the association's newest. */
const newest = seasons[1]!

/** The games as their records have them: the index keeps no list of its own to fall back on. */
const games = [
  {code: "VALORANT", name: "Valorant", slug: "valorant", accent: "#ff4655", banner: null, icon: null, intro: null, sortIndex: 1, current: true},
  {code: "CS2", name: "Counter-Strike 2", slug: "counter-strike-2", accent: "#e8842a", banner: null, icon: null, intro: null, sortIndex: 2, current: true},
  {code: "LEAGUE_OF_LEGENDS", name: "League of Legends", slug: "league-of-legends", accent: "#c8963c", banner: null, icon: null, intro: null, sortIndex: 3, current: true},
]

vi.mock("@/domains/esports/adapters/esports", () => ({
  loadSeasonGames: vi.fn(async () => seasonGames),
  loadGames: vi.fn(async () => games),
  saveSeasonOrReason: vi.fn(async () => ({ok: true, season: seasons[0]})),
  leaveGameInSeason: vi.fn(async () => ({ok: true})),
  // Every season written down, which is more than the games were fielded in.
  loadSeasons: vi.fn(async () => [...seasons, emptySeason]),
}))

// The page asks the store whether the reader may change esports, so every mount answers.
const mountPage = ({board = false}: {board?: boolean} = {}) =>
  mountInApp(Esports, {
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
  // The line-ups too: a season already read is not read again, so a case asserting what the page
  // asked the api for would otherwise be asserting about the mount before it.
  beforeEach(() => {
    vi.clearAllMocks()
    forgetGames()
    forgetSeasonLineups()
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

  it("shows only the games that fielded a team in the shown season", async () => {
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
    expect(targets).toContain(`/esports/valorant?season=${newest.id}`)
    expect(targets).toContain(`/esports/counter-strike-2?season=${newest.id}`)
  })

  it("opens on the season its own url names", async () => {
    route.query = {season: String(seasons[1]!.id)}
    mountPage()
    await flushPromises()

    // The band is asked about that season rather than about whichever one is newest — and it is
    // asked, which is not the same claim: every call being about that season is also true of a
    // page that made none because the season was already held from an earlier mount.
    expect(vi.mocked(loadSeasonGames)).toHaveBeenCalledWith(seasons[1]!.id)
    expect(vi.mocked(loadSeasonGames).mock.calls.every(([id]) => id === seasons[1]!.id)).toBe(true)
  })

  it("offers the seasons it was told about", async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="esports-index-seasons"]').exists()).toBe(true)
    expect(wrapper.text()).toContain("Autumn")
  })
})
