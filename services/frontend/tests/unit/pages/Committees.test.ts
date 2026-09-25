import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import Committees from "@/pages/Committees.vue"
import {forgetCommittees} from "@/domains/committees"
import {forgetCasualGames} from "@/domains/games"

const push = vi.fn()
vi.mock("vue-router", () => ({useRouter: () => ({push})}))
const store = vi.hoisted(() => ({getters: {isBoard: false, isLoggedIn: false}}))
vi.mock("vuex", async importOriginal => ({...(await importOriginal<typeof import("vuex")>()), useStore: () => store}))

const findCommittees = vi.fn()
const findCasualGames = vi.fn()
vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCommittees: () => findCommittees(),
  findCasualGames: () => findCasualGames(),
  findCommitteesByUserId: async () => ({data: []}),
}))

const committee = (id: number, name: string, over: Record<string, unknown> = {}) => ({
  id, name, slug: name.toLowerCase(), description: `${name} runs things.`, listed: true, archived: false, banner: null, gameCodes: [],
  version: 0, createdAt: "", updatedAt: "", ...over,
})

const stub = (name: string, props: string[] = []) => ({name, props: [...props, "testidPrefix"], emits: ["go"], template: "<div />"})
const stubs = {
  FlickReel: stub("FlickReel", ["items"]),
  DriftRow: stub("DriftRow", ["items"]),
  ArtCells: {name: "ArtCells", props: ["cells", "testidPrefix"], emits: ["go"], template: "<div><div v-for=\"cell in cells\" :key=\"cell.id\"><slot name=\"action\" :cell=\"cell\" /></div></div>"},
  ArchiveCommitteeDialog: {name: "ArchiveCommitteeDialog", props: ["open", "committee"], emits: ["update:open", "saved"], template: "<div />"},
  CutButton: {name: "CutButton", props: ["href", "away", "tone", "testid"], template: "<a :href=\"href\" :data-testid=\"testid\"><slot /></a>"},
  VMain: {template: "<main><slot /></main>"},
}

const mountPage = async () => {
  const wrapper = mount(Committees, {global: {stubs}})
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  store.getters.isBoard = false
  store.getters.isLoggedIn = false
  forgetCommittees()
  forgetCasualGames()
  push.mockReset()
  findCasualGames.mockResolvedValue({data: [{code: "CS2", name: "Counter-Strike 2", slug: "cs2", channels: [], archived: false}]})
  findCommittees.mockResolvedValue({data: [
    committee(1, "LanCie", {gameCodes: ["CS2", "GONE"]}),
    committee(2, "Board", {listed: false}),
    committee(3, "OldCie", {archived: true}),
    committee(4, "YapCie"),
  ]})
})

describe("the committees page", () => {
  it("puts the committees that run on the reel and the archived ones in the drifting band, unlisted ones nowhere", async () => {
    const wrapper = await mountPage()

    expect(wrapper.getComponent({name: "FlickReel"}).props("items").map((one: {id: number}) => one.id)).toEqual([1, 4])
    expect(wrapper.getComponent({name: "FlickReel"}).props("items")[0].chips).toEqual(["Counter-Strike 2"])
    expect(wrapper.getComponent({name: "DriftRow"}).props("items").map((one: {id: number}) => one.id)).toEqual([3])
    expect(wrapper.get("[data-testid=committees-olden]").text()).toContain("The committees we used to have")
    expect(wrapper.get("[data-testid=committees-olden]").text()).toContain("any member can run a one-off event as a member's initiative")
    expect(wrapper.getComponent({name: "ArtCells"}).props("cells").map((one: {id: number, archived: boolean}) => [one.id, one.archived]))
      .toEqual([[1, false], [4, false], [3, true]])
  })

  it("follows a committee from the reel, the drifting band or the cells to its page", async () => {
    const wrapper = await mountPage()

    wrapper.getComponent({name: "FlickReel"}).vm.$emit("go", {href: "/committees/lancie"})
    wrapper.getComponent({name: "DriftRow"}).vm.$emit("go", {href: "/committees/oldcie"})
    wrapper.getComponent({name: "ArtCells"}).vm.$emit("go", {href: "/committees/yapcie"})

    expect(push.mock.calls).toEqual([["/committees/lancie"], ["/committees/oldcie"], ["/committees/yapcie"]])
  })

  it("hides the bands it has nothing for", async () => {
    findCommittees.mockResolvedValue({data: []})
    const wrapper = await mountPage()

    expect(wrapper.findComponent({name: "FlickReel"}).exists()).toBe(false)
    expect(wrapper.find("[data-testid=committees-olden]").exists()).toBe(false)
    expect(wrapper.findComponent({name: "ArtCells"}).exists()).toBe(false)
    expect(wrapper.find("[data-testid=committees-ask]").exists()).toBe(true)
  })

  it("offers the board a committee to add on its own page, and archiving from a cell", async () => {
    const plain = await mountPage()
    expect(plain.find("[data-testid=committees-add]").exists()).toBe(false)
    expect(plain.find("[data-testid=committees-every-archive-1]").exists()).toBe(false)

    store.getters.isBoard = true
    const wrapper = await mountPage()
    expect(wrapper.get("[data-testid=committees-add]").attributes("href")).toBe("/committees/new")

    expect(wrapper.get("[data-testid=committees-every-archive-3]").text()).toBe("Bring back")
    await wrapper.get("[data-testid=committees-every-archive-1]").trigger("click")
    const archive = wrapper.getComponent({name: "ArchiveCommitteeDialog"})
    expect(archive.props("committee").id).toBe(1)
    archive.vm.$emit("saved")
    archive.vm.$emit("update:open", false)
    await flushPromises()
    expect(wrapper.findComponent({name: "ArchiveCommitteeDialog"}).exists()).toBe(false)
  })
})
