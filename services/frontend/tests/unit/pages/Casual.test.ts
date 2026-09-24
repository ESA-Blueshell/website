import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import Casual from "@/pages/Casual.vue"
import {forgetCasualGames} from "@/domains/games"

const push = vi.fn()
vi.mock("vue-router", () => ({useRouter: () => ({push})}))
const store = vi.hoisted(() => ({getters: {isBoard: false}}))
vi.mock("vuex", async importOriginal => ({...(await importOriginal<typeof import("vuex")>()), useStore: () => store}))

const findCasualGames = vi.fn()
vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCasualGames: () => findCasualGames(),
}))

const game = (code: string, name: string, archived = false) => ({
  code, name, slug: name.toLowerCase(), accent: null, intro: null, banner: null, icon: null, sortIndex: 0, archived, inCompetition: false,
})

const stub = (name: string, props: string[] = []) => ({name, props: [...props, "testidPrefix"], emits: ["go"], template: "<div />"})
const stubs = {
  FlickReel: stub("FlickReel", ["items"]),
  DriftRow: stub("DriftRow", ["items"]),
  ArtCells: {name: "ArtCells", props: ["cells", "testidPrefix"], emits: ["go"], template: "<div><div v-for=\"cell in cells\" :key=\"cell.id\"><slot name=\"action\" :cell=\"cell\" /></div></div>"},
  CasualGameDialog: {name: "CasualGameDialog", props: ["open", "game"], emits: ["update:open", "saved"], template: "<div />"},
  ArchiveGameDialog: {name: "ArchiveGameDialog", props: ["open", "game"], emits: ["update:open", "saved"], template: "<div />"},
  CutButton: {name: "CutButton", props: ["href", "away", "tone", "testid"], template: "<a :href=\"href\" :data-testid=\"testid\"><slot /></a>"},
  VMain: {template: "<main><slot /></main>"},
}

const mountPage = async () => {
  const wrapper = mount(Casual, {global: {stubs}})
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  store.getters.isBoard = false
  forgetCasualGames()
  push.mockReset()
  findCasualGames.mockResolvedValue({data: [game("CHESS", "Chess"), game("DOTA_2", "Dota 2", true), game("WORDLE", "Wordle")]})
})

describe("the casual page", () => {
  it("puts the played games on the reel and the archived ones in the drifting band", async () => {
    const wrapper = await mountPage()

    expect(wrapper.getComponent({name: "FlickReel"}).props("items").map((one: {id: string}) => one.id)).toEqual(["CHESS", "WORDLE"])
    expect(wrapper.getComponent({name: "DriftRow"}).props("items").map((one: {id: string}) => one.id)).toEqual(["DOTA_2"])
    expect(wrapper.get("[data-testid=casual-olden]").text()).toContain("The games we used to play")
    expect(wrapper.get("[data-testid=casual-olden]").text()).toContain("we bring it back or open a channel for it")
  })

  it("lists every game, the played ones first and the archived ones marked", async () => {
    const cells = (await mountPage()).getComponent({name: "ArtCells"}).props("cells")

    expect(cells.map((one: {id: string, archived: boolean}) => [one.id, one.archived]))
      .toEqual([["CHESS", false], ["WORDLE", false], ["DOTA_2", true]])
  })

  it("follows a game from the reel, the drifting band or the cells to its page", async () => {
    const wrapper = await mountPage()

    wrapper.getComponent({name: "FlickReel"}).vm.$emit("go", {href: "/casual/chess"})
    wrapper.getComponent({name: "DriftRow"}).vm.$emit("go", {href: "/casual/dota-2"})
    wrapper.getComponent({name: "ArtCells"}).vm.$emit("go", {href: "/casual/wordle"})

    expect(push.mock.calls).toEqual([["/casual/chess"], ["/casual/dota-2"], ["/casual/wordle"]])
  })

  it("hides the bands it has nothing for", async () => {
    findCasualGames.mockResolvedValue({data: [game("CHESS", "Chess")]})
    const wrapper = await mountPage()

    expect(wrapper.find("[data-testid=casual-olden]").exists()).toBe(false)
    expect(wrapper.findComponent({name: "FlickReel"}).exists()).toBe(true)
  })

  it("offers the board a game to add, and shows a game added on its own page", async () => {
    const plain = await mountPage()
    expect(plain.find("[data-testid=casual-add]").exists()).toBe(false)
    expect(plain.find("[data-testid=casual-every-archive-CHESS]").exists()).toBe(false)

    store.getters.isBoard = true
    const wrapper = await mountPage()
    await wrapper.get("[data-testid=casual-add]").trigger("click")
    const dialog = wrapper.getComponent({name: "CasualGameDialog"})
    expect(dialog.props("open")).toBe(true)
    dialog.vm.$emit("saved", game("GO", "Go"))
    dialog.vm.$emit("update:open", false)
    await flushPromises()
    expect(dialog.props("open")).toBe(false)

    expect(push).toHaveBeenCalledWith("/casual/go")
  })

  it("lets the board archive a game, or bring one back, from its cell", async () => {
    store.getters.isBoard = true
    const wrapper = await mountPage()

    expect(wrapper.get("[data-testid=casual-every-archive-DOTA_2]").text()).toBe("Bring back")
    await wrapper.get("[data-testid=casual-every-archive-CHESS]").trigger("click")
    const archive = wrapper.getComponent({name: "ArchiveGameDialog"})
    expect(archive.props("game").code).toBe("CHESS")
    archive.vm.$emit("saved")
    archive.vm.$emit("update:open", false)
    await flushPromises()

    expect(wrapper.findComponent({name: "ArchiveGameDialog"}).exists()).toBe(false)
    expect(findCasualGames).toHaveBeenCalledTimes(2)
  })
})
