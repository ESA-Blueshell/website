import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {ref} from "vue"
import BoardEdit from "@/pages/board/BoardEdit.vue"
import BoardMemberEdit from "@/pages/board/BoardMemberEdit.vue"

const route = vi.hoisted(() => ({params: {} as Record<string, string>}))
const router = vi.hoisted(() => ({replace: vi.fn(), options: {history: {state: {back: null}}}}))
vi.mock("vue-router", async importOriginal => ({...(await importOriginal<typeof import("vue-router")>()), useRoute: () => route, useRouter: () => router}))
const roos = {id: 5, name: "Roos"}
const tenth = {id: 10, number: 10, members: [roos]}
const state = vi.hoisted(() => ({may: true, loading: false, refresh: vi.fn()}))
vi.mock("@/domains/boards", async importOriginal => ({
  ...(await importOriginal<typeof import("@/domains/boards")>()),
  useBoards: () => ({boards: ref([tenth]), loading: ref(state.loading), refresh: state.refresh}),
  useMayEditBoards: () => ref(state.may),
}))

const BoardEditor = {name: "BoardEditor", props: ["board", "boards", "nextNumber", "back"], emits: ["saved", "removed", "cancel"], template: "<div />"}
const BoardMemberEditor = {name: "BoardMemberEditor", props: ["board", "member", "back"], emits: ["saved", "removed", "cancel"], template: "<div />"}
const stubs = {BoardEditor, BoardMemberEditor, NotFound: {template: "<div data-testid=missing />"}}

const mountAt = async (page: typeof BoardEdit, params: Record<string, string>) => {
  route.params = params
  const wrapper = mount(page, {global: {stubs}})
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  router.replace.mockReset()
  state.refresh.mockReset().mockResolvedValue([])
  Object.assign(state, {may: true, loading: false})
})

describe("the board edit page", () => {
  it("corrects a board, reads the boards again and shows it on the board page", async () => {
    const wrapper = await mountAt(BoardEdit, {number: "10"})
    const editor = wrapper.getComponent(BoardEditor)

    expect(editor.props()).toMatchObject({board: tenth, nextNumber: 11, back: "/board?board=10"})
    editor.vm.$emit("saved", {...tenth, number: 12})
    await flushPromises()
    editor.vm.$emit("removed")
    await flushPromises()
    editor.vm.$emit("cancel")

    expect(state.refresh).toHaveBeenCalledTimes(2)
    expect(router.replace.mock.calls).toEqual([["/board?board=12"], ["/board"], ["/board?board=10"]])
  })

  it("adds a board, and is not found for a board nobody recorded or for somebody off the board", async () => {
    expect((await mountAt(BoardEdit, {})).getComponent(BoardEditor).props()).toMatchObject({board: null, back: "/board"})
    expect((await mountAt(BoardEdit, {number: "44"})).find("[data-testid=missing]").exists()).toBe(true)
    state.may = false
    expect((await mountAt(BoardEdit, {number: "10"})).find("[data-testid=missing]").exists()).toBe(true)
  })
})

describe("the board member edit page", () => {
  it("corrects or adds a member, reads the boards again and goes back to that board", async () => {
    const wrapper = await mountAt(BoardMemberEdit, {number: "10", member: "5"})
    const editor = wrapper.getComponent(BoardMemberEditor)

    expect(editor.props()).toMatchObject({board: tenth, member: roos, back: "/board?board=10"})
    editor.vm.$emit("saved")
    await flushPromises()
    editor.vm.$emit("removed")
    await flushPromises()
    editor.vm.$emit("cancel")

    expect(state.refresh).toHaveBeenCalledTimes(2)
    expect(router.replace.mock.calls).toEqual([["/board?board=10"], ["/board?board=10"], ["/board?board=10"]])
    expect((await mountAt(BoardMemberEdit, {number: "10"})).getComponent(BoardMemberEditor).props("member")).toBeNull()
  })

  it("is not found for a member or a board nobody recorded", async () => {
    expect((await mountAt(BoardMemberEdit, {number: "10", member: "99"})).find("[data-testid=missing]").exists()).toBe(true)
    expect((await mountAt(BoardMemberEdit, {number: "44"})).find("[data-testid=missing]").exists()).toBe(true)
  })
})
