import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount, RouterLinkStub} from "@vue/test-utils"
import {ref} from "vue"
import CommitteePage from "@/domains/committees/components/CommitteePage.vue"

const router = vi.hoisted(() => ({push: vi.fn(), replace: vi.fn()}))
vi.mock("vue-router", async importOriginal => ({...(await importOriginal<typeof import("vue-router")>()), useRouter: () => router}))
const committees = vi.hoisted(() => ({committees: null as never, refresh: vi.fn()}))
vi.mock("@/domains/committees/useCommittees", async importOriginal => ({
  ...(await importOriginal<typeof import("@/domains/committees/useCommittees")>()),
  useCommittees: () => committees,
}))
const rights = vi.hoisted(() => ({isBoard: null as never, sits: false}))
vi.mock("@/domains/committees/island/useCommitteeRights", () => ({
  useCommitteeRights: () => ({isBoard: rights.isBoard, sitsOn: () => rights.sits}),
}))
const games = vi.hoisted(() => ({games: null as never}))
vi.mock("@/domains/games", async importOriginal => ({
  ...(await importOriginal<typeof import("@/domains/games")>()),
  useCasualGames: () => games,
}))

const dialog = (name: string) => ({name, props: {open: Boolean, committee: Object, asBoard: Boolean}, emits: ["update:open", "saved"], template: "<div />"})
const stubs = {
  RouterLink: RouterLinkStub,
  VMain: {template: "<main><slot /></main>"},
  ScopedEvents: {name: "ScopedEvents", props: ["scope", "testid"], template: "<section />"},
  CommitteeDialog: dialog("CommitteeDialog"),
  ArchiveCommitteeDialog: dialog("ArchiveCommitteeDialog"),
}

const page = (over: Record<string, unknown> = {}) => ({
  id: 7, name: "LanCie", slug: "lancie", description: "LanCie **runs** the LANs.", listed: true, archived: false, banner: null,
  gameCodes: ["CS2", "GONE"],
  members: [{discordTag: "nelly", avatar: "https://cdn/n.png", role: "Chair"}, {discordTag: null, avatar: null, role: null}],
  ...over,
})

const mountPage = (over: Record<string, unknown> = {}) => mount(CommitteePage, {props: {page: page(over)}, global: {stubs}})

beforeEach(() => {
  router.push.mockReset()
  router.replace.mockReset()
  committees.refresh.mockReset().mockResolvedValue([])
  committees.committees = ref([{...page(), members: undefined, version: 4, createdAt: "", updatedAt: ""}]) as never
  rights.isBoard = ref(false) as never
  rights.sits = false
  games.games = ref([{code: "CS2", name: "Counter-Strike 2", slug: "cs2", channels: [], archived: false, accent: null, banner: null, icon: null}]) as never
})

describe("one committee's page", () => {
  it("heads the page with its name, its description as markdown and its games, and lists its own events", () => {
    const wrapper = mountPage()
    const head = wrapper.get("[data-testid=committee-head]")

    expect(head.get("h1").text()).toBe("LanCie")
    expect(head.get(".record-head__intro strong").text()).toBe("runs")
    expect(head.get(".record-head__plate").text()).toBe("L")
    expect(wrapper.get("[data-testid=committee-games] .record-fact__label").text()).toBe("Game")
    expect(wrapper.findAllComponents(RouterLinkStub).map(link => link.props("to"))).toEqual(["/committees", "/casual/cs2"])
    expect(wrapper.getComponent({name: "ScopedEvents"}).props("scope")).toEqual({committeeId: 7})
    expect(wrapper.find("[data-testid=committee-archived]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=committee-edit]").exists()).toBe(false)
  })

  it("names its members by Discord only, or says their Discord is not linked", () => {
    const wrapper = mountPage()

    expect(wrapper.get("[data-testid=committee-seat-0]").text()).toContain("@nelly")
    expect(wrapper.get("[data-testid=committee-seat-0]").text()).toContain("Chair")
    expect(wrapper.get("[data-testid=committee-seat-0] img").attributes("src")).toBe("https://cdn/n.png")
    expect(wrapper.get("[data-testid=committee-seat-1]").text()).toBe("Discord not linked")
    expect(mountPage({members: []}).find("[data-testid=committee-members]").exists()).toBe(false)
  })

  it("draws the games it organises events for, each leading to its page", async () => {
    const wrapper = mountPage()

    await wrapper.get("[data-testid=committee-games-cell-CS2]").trigger("click", {button: 0})

    expect(router.push).toHaveBeenCalledWith("/casual/cs2")
    expect(mountPage({gameCodes: []}).find("[data-testid=committee-games-band]").exists()).toBe(false)
  })

  it("offers its own members Edit committee and Add an event, and the board archiving too", async () => {
    rights.sits = true
    const member = mountPage()
    expect(member.findAllComponents(RouterLinkStub).map(link => link.props("to"))).toContain("/events/create?committee=7")
    expect(member.find("[data-testid=committee-archive]").exists()).toBe(false)
    await member.get("[data-testid=committee-edit]").trigger("click")
    expect(member.getComponent({name: "CommitteeDialog"}).props()).toMatchObject({open: true, asBoard: false})
    expect(member.getComponent({name: "CommitteeDialog"}).props("committee").version).toBe(4)
    expect(member.findComponent({name: "ArchiveCommitteeDialog"}).exists()).toBe(false)

    rights.sits = false
    rights.isBoard = ref(true) as never
    const board = mountPage()
    await board.get("[data-testid=committee-archive]").trigger("click")
    expect(board.getComponent({name: "ArchiveCommitteeDialog"}).props("open")).toBe(true)
    board.getComponent({name: "ArchiveCommitteeDialog"}).vm.$emit("update:open", false)
    board.getComponent({name: "CommitteeDialog"}).vm.$emit("update:open", false)
    await board.vm.$nextTick()
    expect(board.getComponent({name: "ArchiveCommitteeDialog"}).props("open")).toBe(false)
  })

  it("marks an archived committee and offers no Add an event, even to its members", () => {
    rights.sits = true
    const wrapper = mountPage({archived: true, gameCodes: []})

    expect(wrapper.get("[data-testid=committee-archived]").text()).toBe("Archived")
    expect(wrapper.findAllComponents(RouterLinkStub).map(link => link.props("to"))).not.toContain("/events/create?committee=7")
    expect(wrapper.find("[data-testid=committee-edit]").exists()).toBe(true)
    expect(wrapper.find("[data-testid=committee-games]").exists()).toBe(false)
  })

  it("follows a committee to its new address once saved, and tells the page to read it again", async () => {
    rights.isBoard = ref(true) as never
    committees.committees = ref([]) as never
    const wrapper = mountPage()
    expect(wrapper.getComponent({name: "CommitteeDialog"}).props("committee")).toMatchObject({id: 7, version: 0})

    wrapper.getComponent({name: "CommitteeDialog"}).vm.$emit("saved", {...page(), slug: "lan"})
    await flushPromises()
    wrapper.getComponent({name: "ArchiveCommitteeDialog"}).vm.$emit("saved", page())
    await flushPromises()

    expect(router.replace).toHaveBeenCalledTimes(1)
    expect(router.replace).toHaveBeenCalledWith("/committees/lan")
    expect(committees.refresh).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted("changed")).toHaveLength(2)
  })
})
