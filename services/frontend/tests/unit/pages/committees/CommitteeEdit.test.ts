import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {ref} from "vue"
import CommitteeEdit from "@/pages/committees/CommitteeEdit.vue"

const route = vi.hoisted(() => ({params: {} as Record<string, string>, query: {}, meta: {}}))
const state = vi.hoisted(() => ({back: null as string | null}))
const router = vi.hoisted(() => ({replace: vi.fn(), options: {history: {state}}}))
vi.mock("vue-router", async importOriginal => ({...(await importOriginal<typeof import("vue-router")>()), useRoute: () => route, useRouter: () => router}))
const lan = {id: 1, name: "LanCie", slug: "lancie"}
const store = vi.hoisted(() => ({refresh: vi.fn()}))
const rights = vi.hoisted(() => ({board: false, sits: false}))
vi.mock("@/domains/committees", () => ({
  useCommittees: () => ({committees: ref([lan]), ready: Promise.resolve([lan]), refresh: store.refresh}),
  useCommitteeRights: () => ({isBoard: ref(rights.board), sitsOn: () => rights.sits}),
}))

const CommitteeEditor = {name: "CommitteeEditor", props: ["committee", "asBoard", "back"], emits: ["saved", "cancel"], template: "<div />"}
const stubs = {CommitteeEditor, NotFound: {template: "<div data-testid=missing />"}}

const mountPage = async (address?: string) => {
  route.params = address ? {address} : {}
  const wrapper = mount(CommitteeEdit, {global: {stubs}})
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  router.replace.mockReset()
  store.refresh.mockReset().mockResolvedValue([])
  Object.assign(rights, {board: false, sits: false})
  state.back = null
})

describe("the committee edit page", () => {
  it("lets the board correct a committee, and lands on its page at the address it has once saved", async () => {
    rights.board = true
    const wrapper = await mountPage("lancie")
    const editor = wrapper.getComponent(CommitteeEditor)

    expect(editor.props()).toMatchObject({committee: lan, asBoard: true, back: "/committees/lancie"})
    editor.vm.$emit("saved", {...lan, slug: "lan"})
    await flushPromises()
    editor.vm.$emit("cancel")

    expect(store.refresh).toHaveBeenCalled()
    expect(router.replace.mock.calls).toEqual([["/committees/lan"], ["/committees/lancie"]])
  })

  it("lets a committee's own members reach their committee's page, not as the board", async () => {
    rights.sits = true

    expect((await mountPage("lancie")).getComponent(CommitteeEditor).props("asBoard")).toBe(false)
  })

  it("adds a committee for the board only, going back to the index", async () => {
    rights.board = true
    const wrapper = await mountPage()
    expect(wrapper.getComponent(CommitteeEditor).props()).toMatchObject({committee: null, back: "/committees"})

    rights.board = false
    expect((await mountPage()).find("[data-testid=missing]").exists()).toBe(true)
  })

  it("is not found for somebody off the committee, or for an address nobody holds", async () => {
    expect((await mountPage("lancie")).find("[data-testid=missing]").exists()).toBe(true)
    rights.board = true
    expect((await mountPage("nobody")).find("[data-testid=missing]").exists()).toBe(true)
  })
})
