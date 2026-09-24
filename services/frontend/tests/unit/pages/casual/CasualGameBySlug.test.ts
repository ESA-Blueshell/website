import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import CasualGameBySlug from "@/pages/casual/CasualGameBySlug.vue"
import {forgetCasualGames} from "@/domains/games"

const route = {params: {slug: "chess"}}
vi.mock("vue-router", async importOriginal => ({...(await importOriginal<typeof import("vue-router")>()), useRoute: () => route}))

const findCasualGames = vi.fn()
vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCasualGames: () => findCasualGames(),
}))

const stubs = {
  CasualGamePage: {name: "CasualGamePage", props: ["game"], template: "<div data-testid=page />"},
  NotFound: {name: "NotFound", template: "<div data-testid=missing />"},
}

beforeEach(() => {
  forgetCasualGames()
  findCasualGames.mockResolvedValue({data: [{code: "CHESS", name: "Chess", slug: "chess", accent: null, intro: null, banner: null, icon: null, sortIndex: 0, archived: false, inCompetition: false, channels: []}]})
})

describe("a game's page by its address", () => {
  it("shows the game the address names, and titles the tab after it", async () => {
    route.params.slug = "chess"
    const wrapper = mount(CasualGameBySlug, {global: {stubs}})
    await flushPromises()

    expect(wrapper.getComponent({name: "CasualGamePage"}).props("game").code).toBe("CHESS")
    expect(document.title).toBe("Chess — Blueshell")
  })

  it("reads as not found once the games have answered without it, and as nothing before", async () => {
    route.params.slug = "pong"
    const wrapper = mount(CasualGameBySlug, {global: {stubs}})
    expect(wrapper.find("[data-testid=missing]").exists()).toBe(false)

    await flushPromises()

    expect(wrapper.find("[data-testid=missing]").exists()).toBe(true)
  })
})
