import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, shallowMount} from "@vue/test-utils"
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
vi.mock("@/domains/esports/island/useSeasons", () => ({useSeasons: () => ({seasons: ref([])})}))
vi.mock("@/domains/esports/island/useMayEditEsports", () => ({useMayEditEsports: () => ref(true)}))
vi.mock("@/components/island/useSwipeArrival", () => ({
  useSwipeArrival: () => ({arrival: ref(null), asked: ref(null), pending: ref(false), refused: ref(false), travelTo: vi.fn()}),
}))
vi.mock("@/domains/esports/composables/useEsportsPage", () => ({
  useEsportsPage: () => ({
    page: ref(null), loading: ref(false), teams: ref([]), seasons: ref([]), season: ref(null), chosen: ref(null),
    showSeason: vi.fn(), reload: vi.fn(), askAhead: vi.fn(), answerFor: () => null,
  }),
}))

const GameDialog = {name: "GameDialog", props: ["accent", "game", "open"], emits: ["removed", "saved", "update:open"], template: "<div />"}
const HeaderBand = {name: "HeaderBand", template: "<div><slot name=\"head\" /><slot /></div>"}

const mountPage = () => shallowMount(EsportsGamePage, {props: {game: "VALORANT"}, global: {stubs: {GameDialog, HeaderBand, VMain: {template: "<main><slot /></main>"}, Island: {template: "<div><slot /></div>"}}}})

beforeEach(() => {
  router.push.mockReset()
  router.replace.mockReset()
  games.refresh.mockReset().mockResolvedValue([])
  record.slug = "valorant"
})

describe("a game's competition page", () => {
  it("follows the game to its new competition address once edited, and stays where it is otherwise", async () => {
    const wrapper = mountPage()

    wrapper.getComponent(GameDialog).vm.$emit("saved")
    await flushPromises()
    expect(router.replace).not.toHaveBeenCalled()

    record.slug = "valo"
    wrapper.getComponent(GameDialog).vm.$emit("saved")
    await flushPromises()
    expect(router.replace).toHaveBeenCalledWith("/competition/valo")
  })

  it("goes back to the competition index once the game is removed", () => {
    mountPage().getComponent(GameDialog).vm.$emit("removed")

    expect(router.push).toHaveBeenCalledWith("/competition")
  })
})
