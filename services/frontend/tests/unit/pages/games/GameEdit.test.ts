import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {ref} from "vue"
import GameEdit from "@/pages/games/GameEdit.vue"

const route = vi.hoisted(() => ({params: {} as Record<string, string>, query: {} as Record<string, string>, meta: {} as Record<string, string>}))
const state = vi.hoisted(() => ({back: null as string | null}))
const router = vi.hoisted(() => ({
  replace: vi.fn(),
  resolve: (to: string) => ({path: new URL(to, "http://x").pathname}),
  options: {history: {state}},
}))
vi.mock("vue-router", async importOriginal => ({...(await importOriginal<typeof import("vue-router")>()), useRoute: () => route, useRouter: () => router}))
const chess = {code: "CHESS", name: "Chess", slug: "chess"}
vi.mock("@/domains/games", () => ({useCasualGames: () => ({games: ref([chess]), ready: Promise.resolve([chess])})}))

const GameEditor = {name: "GameEditor", props: ["game", "area", "back", "enterIn"], emits: ["saved", "removed", "cancel"], template: "<div />"}
const stubs = {GameEditor, NotFound: {template: "<div data-testid=missing />"}}

const mountPage = async (at: {params?: Record<string, string>, query?: Record<string, string>, area?: string, back?: string | null}) => {
  route.params = at.params ?? {}
  route.query = at.query ?? {}
  route.meta = at.area ? {area: at.area} : {}
  state.back = at.back ?? null
  const wrapper = mount(GameEdit, {global: {stubs}})
  await flushPromises()
  return wrapper
}

beforeEach(() => router.replace.mockReset())

describe("the game edit page", () => {
  it("corrects a casual game and goes back where it came from, or to the game's new address", async () => {
    const wrapper = await mountPage({params: {slug: "chess"}, back: "/casual/chess"})
    const editor = wrapper.getComponent(GameEditor)

    expect(editor.props()).toMatchObject({game: chess, area: "casual", back: "/casual/chess", enterIn: null})
    editor.vm.$emit("saved", {...chess, slug: "schaak"})
    editor.vm.$emit("saved", chess)
    editor.vm.$emit("cancel")
    editor.vm.$emit("removed")

    expect(router.replace.mock.calls).toEqual([["/casual/schaak"], ["/casual/chess"], ["/casual/chess"], ["/casual"]])
  })

  it("goes back to the page it came from when that was not the game's own", async () => {
    const wrapper = await mountPage({params: {slug: "chess"}, area: "competition", back: "/competition?season=3"})

    wrapper.getComponent(GameEditor).vm.$emit("saved", {...chess, slug: "schaak"})

    expect(router.replace).toHaveBeenCalledWith("/competition?season=3")
  })

  it("adds a game: a casual one lands on its page, a competition one goes back to the season it was entered in", async () => {
    const casual = await mountPage({})
    expect(casual.getComponent(GameEditor).props()).toMatchObject({game: null, back: "/casual"})
    casual.getComponent(GameEditor).vm.$emit("saved", chess)
    expect(router.replace).toHaveBeenLastCalledWith("/casual/chess")

    const competition = await mountPage({area: "competition", query: {season: "4"}, back: "/competition/seasons/4/edit"})
    expect(competition.getComponent(GameEditor).props("back")).toBe("/competition/seasons/4/edit")
    expect(competition.getComponent(GameEditor).props("enterIn")).toBe(4)
    competition.getComponent(GameEditor).vm.$emit("saved", chess)
    expect(router.replace).toHaveBeenLastCalledWith("/competition/seasons/4/edit")
  })

  it("reads an address no game answers to as not found", async () => {
    const wrapper = await mountPage({params: {slug: "pong"}})

    expect(wrapper.find("[data-testid=missing]").exists()).toBe(true)
  })
})
