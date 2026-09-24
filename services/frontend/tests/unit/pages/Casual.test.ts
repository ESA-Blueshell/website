import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import Casual from "@/pages/Casual.vue"
import {forgetCasualGames} from "@/domains/games"

const push = vi.fn()
vi.mock("vue-router", () => ({useRouter: () => ({push})}))

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
  ArtCells: stub("ArtCells", ["cells"]),
  CutButton: {name: "CutButton", props: ["href", "away", "tone", "testid"], template: "<a :href=\"href\"><slot /></a>"},
  VMain: {template: "<main><slot /></main>"},
}

const mountPage = async () => {
  const wrapper = mount(Casual, {global: {stubs}})
  await flushPromises()
  return wrapper
}

beforeEach(() => {
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
})
