import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount, RouterLinkStub} from "@vue/test-utils"
import {ref} from "vue"
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

const committees = vi.hoisted(() => ({listed: null as never, refresh: vi.fn()}))
vi.mock("@/domains/committees", async importOriginal => ({
  ...(await importOriginal<typeof import("@/domains/committees")>()),
  useCommittees: () => committees,
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
  ScopedEvents: {name: "ScopedEvents", props: ["scope", "testid"], template: "<section><slot /></section>"},
}

const mountPage = (game: CasualGame) => mount(CasualGamePage, {props: {game}, global: {stubs}})

beforeEach(() => {
  store.getters.isBoard = false
  router.push.mockReset()
  router.replace.mockReset()
  refresh.mockReset().mockResolvedValue([])
  committees.refresh.mockReset().mockResolvedValue([])
  committees.listed = ref([
    {id: 1, name: "LanCie", slug: "lancie", description: "LANs", listed: true, archived: false, banner: null, gameCodes: ["VALORANT"]},
    {id: 2, name: "YapCie", slug: "yapcie", description: "Streams", listed: true, archived: false, banner: null, gameCodes: ["VALORANT"]},
    {id: 3, name: "MCie", slug: "mcie", description: "Blocks", listed: true, archived: false, banner: null, gameCodes: ["MINECRAFT"]},
  ]) as never
})

describe("one game's page", () => {
  it("heads the page with the game's banner, icon, name and intro, and leads to its competition page", () => {
    const wrapper = mountPage(valorant)
    const head = wrapper.get("[data-testid=casual-game-head]")

    expect(head.get("h1").text()).toBe("Valorant")
    expect(head.get("h1 img").attributes("src")).toBe("/v-icon.webp")
    expect(head.text()).toContain("Five-stacks, customs and clips.")
    expect(head.get(".record-head__art img").attributes("srcset")).toBe("/v.webp?w=640 640w, /v.webp 1600w")
    expect(wrapper.findAllComponents(RouterLinkStub).map(link => link.props("to")))
      .toEqual(["/casual", "/competition/valorant", "/committees/lancie", "/committees/yapcie"])
    expect(wrapper.find("[data-testid=casual-game-archived]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=casual-game-edit]").exists()).toBe(false)
  })

  it("links into each of the game's channels, and opens the first", () => {
    const wrapper = mountPage(valorant)
    const channels = wrapper.get("[data-testid=casual-game-channels]")

    expect(channels.get(".record-fact__label").text()).toBe("Channels")
    expect(channels.get(".record-fact__value").text()).toMatch(/#valorant\s+·\s+#hero-shooters/)
    expect(wrapper.get("[data-testid=casual-game-channel-6323]").attributes("href")).toBe("https://discord.com/channels/324/6323")
    expect(wrapper.get("[data-testid=casual-game-open-channel]").text()).toBe("Open #valorant")
    expect(mountPage({...valorant, channels: valorant.channels.slice(0, 1)}).get(".record-head__facts").text()).toContain("Channel#valorant")
  })

  it("lists the events that name the game, with a note for when none do", () => {
    const wrapper = mountPage(valorant)
    const events = wrapper.getComponent({name: "ScopedEvents"})

    expect(events.props("scope")).toEqual({gameCode: "VALORANT"})
    expect(events.text()).toBe("No event names Valorant yet. Events name their games from now on, so older ones are not listed here.")
  })

  it("says a game nobody fields is not played in competition, and marks an archived one", () => {
    const wrapper = mountPage(dota)

    expect(wrapper.get("[data-testid=casual-game-not-competitive] .record-fact__sub").text()).toBe("We don't currently play this game competitively")
    expect(wrapper.get("[data-testid=casual-game-archived]").text()).toBe("Archived")
    expect(wrapper.get(".record-head__plate").text()).toBe("D2")
    expect(wrapper.find("[data-testid=casual-game-competition]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=casual-game-channels]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=casual-game-open-channel]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=casual-game-committees]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=casual-game-organisers]").exists()).toBe(false)
  })

  it("names the committees that organise events for it, in the head and in their own band", async () => {
    const wrapper = mountPage(valorant)

    expect(wrapper.get("[data-testid=casual-game-committees] .record-fact__label").text()).toBe("Committees")
    expect(wrapper.get("[data-testid=casual-game-organisers]").text()).toContain("Who organises events for it")
    expect(wrapper.findAll("[data-testid^=casual-game-organisers-cell-]")).toHaveLength(2)
    await wrapper.get("[data-testid=casual-game-organisers-cell-1]").trigger("click", {button: 0})
    expect(router.push).toHaveBeenCalledWith("/committees/lancie")

    committees.listed = ref([{id: 1, name: "LanCie", slug: "lancie", description: "LANs", listed: true, archived: false, banner: null, gameCodes: ["VALORANT"]}]) as never
    expect(mountPage(valorant).get("[data-testid=casual-game-committees] .record-fact__label").text()).toBe("Committee")
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
    expect(committees.refresh).toHaveBeenCalledTimes(2)
  })
})
