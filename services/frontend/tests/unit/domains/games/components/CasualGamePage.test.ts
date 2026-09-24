import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount, RouterLinkStub} from "@vue/test-utils"
import CasualGamePage from "@/domains/games/components/CasualGamePage.vue"
import type {CasualGame} from "@/domains/games"

const store = vi.hoisted(() => ({getters: {isBoard: false}}))
vi.mock("vuex", async importOriginal => ({...(await importOriginal<typeof import("vuex")>()), useStore: () => store}))
const router = vi.hoisted(() => ({push: vi.fn(), replace: vi.fn()}))
vi.mock("vue-router", async importOriginal => ({...(await importOriginal<typeof import("vue-router")>()), useRouter: () => router}))
const refresh = vi.hoisted(() => vi.fn())
vi.mock("@/domains/games/useCasualGames", async importOriginal => ({
  ...(await importOriginal<typeof import("@/domains/games/useCasualGames")>()),
  useCasualGames: () => ({refresh}),
}))

const picture = (url: string) => ({url, path: url, width: 1600, height: 900, renditions: [{url: `${url}?w=640`, width: 640}]})

const valorant: CasualGame = {
  code: "VALORANT", name: "Valorant", slug: "valorant", accent: "#ff4655", intro: "Five-stacks, customs and clips.", sortIndex: 1,
  archived: false, inCompetition: true, channels: [{id: "6322", guildId: "324", name: "valorant"}, {id: "6323", guildId: "324", name: "hero-shooters"}], banner: picture("/v.webp"), icon: picture("/v-icon.webp"),
}
const dota: CasualGame = {code: "DOTA_2", name: "Dota 2", slug: "dota-2", accent: null, intro: null, sortIndex: 2, archived: true, inCompetition: false, banner: null, icon: null, channels: []}

const dialog = (name: string) => ({name, props: ["open", "game"], emits: ["update:open", "saved", "removed"], template: "<div />"})
const stubs = {
  RouterLink: RouterLinkStub,
  VMain: {template: "<main><slot /></main>"},
  CasualGameDialog: dialog("CasualGameDialog"),
  ArchiveGameDialog: dialog("ArchiveGameDialog"),
  RemoveGameDialog: dialog("RemoveGameDialog"),
}

const mountPage = (game: CasualGame) => mount(CasualGamePage, {props: {game}, global: {stubs}})

beforeEach(() => {
  store.getters.isBoard = false
  router.push.mockReset()
  router.replace.mockReset()
  refresh.mockReset().mockResolvedValue([])
})

describe("one game's page", () => {
  it("heads the page with the game's banner, icon, name and intro, and leads to its competition page", () => {
    const wrapper = mountPage(valorant)
    const head = wrapper.get("[data-testid=casual-game-head]")

    expect(head.get("h1").text()).toBe("Valorant")
    expect(head.get("h1 img").attributes("src")).toBe("/v-icon.webp")
    expect(head.text()).toContain("Five-stacks, customs and clips.")
    expect(head.get(".game-page__art img").attributes("srcset")).toBe("/v.webp?w=640 640w, /v.webp 1600w")
    expect(wrapper.findAllComponents(RouterLinkStub).map(link => link.props("to"))).toEqual(["/casual", "/competition/valorant"])
    expect(wrapper.find("[data-testid=casual-game-archived]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=casual-game-edit]").exists()).toBe(false)
  })

  it("links into each of the game's channels, and opens the first", () => {
    const wrapper = mountPage(valorant)
    const channels = wrapper.get("[data-testid=casual-game-channels]")

    expect(channels.get(".game-page__fact-label").text()).toBe("Channels")
    expect(channels.get(".game-page__fact-value").text()).toMatch(/#valorant\s+·\s+#hero-shooters/)
    expect(wrapper.get("[data-testid=casual-game-channel-6323]").attributes("href")).toBe("https://discord.com/channels/324/6323")
    expect(wrapper.get("[data-testid=casual-game-open-channel]").text()).toBe("Open #valorant")
    expect(mountPage({...valorant, channels: valorant.channels.slice(0, 1)}).get(".game-page__facts").text()).toContain("Channel#valorant")
  })

  it("says a game nobody fields is not played in competition, and marks an archived one", () => {
    const wrapper = mountPage(dota)

    expect(wrapper.get("[data-testid=casual-game-not-competitive]").text()).toBe("We don't currently play this game competitively")
    expect(wrapper.get("[data-testid=casual-game-archived]").text()).toBe("Archived")
    expect(wrapper.get(".game-page__plate").text()).toBe("D2")
    expect(wrapper.find("[data-testid=casual-game-competition]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=casual-game-channels]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=casual-game-open-channel]").exists()).toBe(false)
  })

  it("offers the board editing and archiving, and removal only once archived", async () => {
    store.getters.isBoard = true
    const played = mountPage(valorant)
    expect(played.get("[data-testid=casual-game-archive]").text()).toBe("Archive")
    expect(played.find("[data-testid=casual-game-remove]").exists()).toBe(false)

    const old = mountPage(dota)
    expect(old.get("[data-testid=casual-game-archive]").text()).toBe("Bring it back")
    await old.get("[data-testid=casual-game-remove]").trigger("click")
    expect(old.findComponent({name: "RemoveGameDialog"}).exists()).toBe(true)
    await old.get("[data-testid=casual-game-edit]").trigger("click")
    expect(old.getComponent({name: "CasualGameDialog"}).props("open")).toBe(true)
    await old.get("[data-testid=casual-game-archive]").trigger("click")
    expect(old.getComponent({name: "ArchiveGameDialog"}).props("open")).toBe(true)

    old.getComponent({name: "CasualGameDialog"}).vm.$emit("update:open", false)
    old.getComponent({name: "ArchiveGameDialog"}).vm.$emit("update:open", false)
    await old.vm.$nextTick()
    expect(old.getComponent({name: "CasualGameDialog"}).props("open")).toBe(false)
    expect(old.getComponent({name: "ArchiveGameDialog"}).props("open")).toBe(false)
  })

  it("follows a game to its new address once edited, and back to the index once removed", async () => {
    store.getters.isBoard = true
    const wrapper = mountPage(dota)

    wrapper.getComponent({name: "CasualGameDialog"}).vm.$emit("saved", {...dota, slug: "dota"})
    await flushPromises()
    wrapper.getComponent({name: "CasualGameDialog"}).vm.$emit("saved", dota)
    await flushPromises()
    await wrapper.get("[data-testid=casual-game-remove]").trigger("click")
    wrapper.getComponent({name: "RemoveGameDialog"}).vm.$emit("removed", dota)
    await flushPromises()
    wrapper.getComponent({name: "RemoveGameDialog"}).vm.$emit("update:open", false)

    expect(router.replace).toHaveBeenCalledTimes(1)
    expect(router.replace).toHaveBeenCalledWith("/casual/dota")
    expect(router.push).toHaveBeenCalledWith("/casual")
    expect(refresh).toHaveBeenCalledTimes(3)
  })
})
