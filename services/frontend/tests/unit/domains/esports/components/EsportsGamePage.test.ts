import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, RouterLinkStub, shallowMount} from "@vue/test-utils"
import {ref} from "vue"
import EsportsGamePage from "@/domains/esports/components/EsportsGamePage.vue"

const router = vi.hoisted(() => ({push: vi.fn(), replace: vi.fn()}))
const route = vi.hoisted(() => ({params: {slug: "valorant"}, query: {}}))
vi.mock("vue-router", async importOriginal => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRouter: () => router,
  useRoute: () => route,
}))
const record = vi.hoisted(() => ({slug: "valorant"}))
const games = vi.hoisted(() => ({refresh: vi.fn()}))
vi.mock("@/domains/esports/island/useGames", () => ({
  useGames: () => ({
    identityOf: () => ({name: "Valorant", accent: "#ff4655", icon: null}),
    recordOf: () => ({code: "VALORANT", slug: record.slug, intro: ""}),
    refresh: games.refresh,
  }),
}))
const seasons = [{id: 3, name: "Autumn 2025", startDate: "2025-09-01", endDate: "2026-01-31", played: true}, {id: 4, name: "Spring 2026", startDate: "2026-02-01", endDate: "2026-06-30", played: true}]
vi.mock("@/domains/esports/island/useSeasons", () => ({useSeasons: () => ({seasons: ref(seasons)})}))
vi.mock("@/domains/esports/island/useMayEditEsports", () => ({useMayEditEsports: () => ref(true)}))
vi.mock("@/components/island/useSwipeArrival", () => ({
  useSwipeArrival: () => ({arrival: ref(null), asked: ref(null), pending: ref(false), refused: ref(false), travelTo: vi.fn()}),
}))
vi.mock("@/domains/esports/composables/useEsportsPage", () => ({
  useEsportsPage: () => ({
    page: ref(null), loading: ref(false), teams: ref([]), seasons: ref(seasons), season: ref(seasons[1]), chosen: ref(4),
    showSeason: vi.fn(), reload: vi.fn(), askAhead: vi.fn(), answerFor: () => null,
  }),
}))

const EsportsGameHead = {name: "EsportsGameHead", template: "<div><slot name=\"edit\" /></div>"}
const Timeline = {name: "Timeline", props: ["stops", "selectedId"], emits: ["add", "edit", "select"], template: "<div />"}
const SeasonSwipe = {name: "SeasonSwipe", props: ["season"], template: "<div><slot :season=\"season\" /></div>"}
const SliceBand = {name: "SliceBand", props: ["items"], emits: ["add", "edit", "open"], template: "<div />"}
const stubs = {
  EsportsGameHead, Timeline, SeasonSwipe, SliceBand, RouterLink: RouterLinkStub,
  "motion.div": {template: "<div><slot /></div>"}, VMain: {template: "<main><slot /></main>"}, Island: {template: "<div><slot /></div>"},
}

const mountPage = () => shallowMount(EsportsGamePage, {props: {game: "VALORANT"}, global: {stubs}})

beforeEach(() => {
  router.push.mockReset()
  router.replace.mockReset()
  games.refresh.mockReset().mockResolvedValue([])
  record.slug = "valorant"
})

describe("a game's competition page", () => {
  it("leads to the game's own edit page", () => {
    expect(mountPage().getComponent(RouterLinkStub).props("to")).toBe("/competition/valorant/edit")
  })

  it("adds and corrects a season on the season's own page", () => {
    const strip = mountPage().getComponent(Timeline)

    strip.vm.$emit("add")
    strip.vm.$emit("edit", 3)

    expect(router.push.mock.calls).toEqual([["/competition/seasons/new"], ["/competition/seasons/3/edit"]])
  })

  it("adds a team to the shown season and corrects its line-up on the team's own page", async () => {
    const band = mountPage().getComponent(SliceBand)

    band.vm.$emit("add")
    band.vm.$emit("edit", 9)
    band.vm.$emit("open", 9)
    await flushPromises()

    expect(router.push.mock.calls).toEqual([
      ["/competition/valorant/teams/new?season=4"],
      ["/competition/valorant/teams/9/edit?season=4"],
    ])
  })
})
