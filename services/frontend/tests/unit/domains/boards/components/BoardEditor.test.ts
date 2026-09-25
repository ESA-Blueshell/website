import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import BoardEditor from "@/domains/boards/components/BoardEditor.vue"
import {editorStubs, write} from "./editorStubs"

const adapter = vi.hoisted(() => ({dropBoard: vi.fn(), saveBoardOrReason: vi.fn(), storeBoardPhoto: vi.fn()}))
vi.mock("@/domains/boards/adapters/boards", async importOriginal => ({
  ...(await importOriginal<typeof import("@/domains/boards/adapters/boards")>()),
  ...adapter,
}))

const tenth = {
  id: 10, number: 10, name: "Blue", cheer: "Go", accent: "#65c6cd", description: "The tenth.", startDate: "2024-09-01", endDate: "2025-08-31",
  photo: {path: "p.webp", url: "/p.webp", renditions: []}, members: [{id: 1}, {id: 2}], version: 2,
}
const ninth = {...tenth, id: 9, number: 9, name: null, members: [], photo: null}

const mountEditor = (board: typeof tenth | null) =>
  mount(BoardEditor, {props: {board, boards: [ninth, tenth], nextNumber: 11, back: "/board"}, global: {stubs: editorStubs}})

beforeEach(() => Object.values(adapter).forEach(one => one.mockReset()))

describe("the board edit page", () => {
  it("adds a board on the suggested number, previewed on the timeline and in its band as it is typed", async () => {
    adapter.saveBoardOrReason.mockResolvedValue({ok: true, board: {...tenth, id: 11, number: 11}})
    const wrapper = mountEditor(null)
    const stops = () => wrapper.getComponent(editorStubs.Timeline).props("stops") as Array<{id: number}>

    expect(wrapper.getComponent(editorStubs.EditPage).props("title")).toBe("Add a board")
    expect(wrapper.get("[data-testid=board-edit-save]").attributes("data-disabled")).toBe("true")
    write(wrapper, "board-edit-name", "Orange")
    write(wrapper, "board-edit-cheer", "Forward")
    wrapper.getComponent({name: "ColourControl"}).vm.$emit("update:modelValue", "#ff7a1a")
    write(wrapper, "board-edit-description", "The eleventh.")
    write(wrapper, "board-edit-start", "2025-09-01")
    write(wrapper, "board-edit-end", "2026-08-31")
    await flushPromises()

    expect(stops().map(one => one.id)).toContain(11)
    expect(wrapper.getComponent(editorStubs.BoardBand).props()).toMatchObject({name: "Orange", cheer: "Forward", description: "The eleventh."})
    expect(wrapper.getComponent(editorStubs.BoardBand).props("label")).toContain("2025")
    expect(wrapper.getComponent(editorStubs.EditPage).props("accent")).toBe("#ff7a1a")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.saveBoardOrReason).toHaveBeenCalledWith({
      id: undefined, number: 11, name: "Orange", cheer: "Forward", accent: "#ff7a1a", description: "The eleventh.",
      startDate: "2025-09-01", endDate: "2026-08-31", photo: null, version: undefined,
    })
    expect(wrapper.emitted("saved")).toEqual([[{...tenth, id: 11, number: 11}]])
  })

  it("corrects a board and keeps what was typed when refused, its photo stored when chosen", async () => {
    adapter.saveBoardOrReason.mockResolvedValue({ok: false, reason: "Board 9 already exists."})
    const wrapper = mountEditor(tenth)
    const photo = wrapper.getComponent(editorStubs.ImagePicker)
    const file = new File(["x"], "p.png")

    expect(wrapper.getComponent(editorStubs.EditPage).props("title")).toBe("Edit board")
    await photo.props("store")(file)
    photo.vm.$emit("update:picture", null)
    write(wrapper, "board-edit-number", "9")
    write(wrapper, "board-edit-name", "")
    await flushPromises()
    await wrapper.get("[data-testid=board-edit-save]").trigger("click")
    await flushPromises()

    expect(adapter.storeBoardPhoto).toHaveBeenCalledWith(file)
    expect(adapter.saveBoardOrReason).toHaveBeenCalledWith(expect.objectContaining({id: 10, number: 9, name: null, photo: null, version: 2}))
    expect(wrapper.get("[data-testid=board-edit-failure]").text()).toBe("Board 9 already exists.")
    expect(wrapper.emitted("saved")).toBeUndefined()
    expect(wrapper.getComponent(editorStubs.BoardBand).props("label")).toBe("Board IX, 2024-2025")
  })

  it("never saves without a number and a start", async () => {
    const wrapper = mountEditor(tenth)

    write(wrapper, "board-edit-number", "0")
    await flushPromises()
    await wrapper.get("form").trigger("submit")
    write(wrapper, "board-edit-number", "10")
    write(wrapper, "board-edit-start", "")
    await flushPromises()
    await wrapper.get("form").trigger("submit")

    expect(adapter.saveBoardOrReason).not.toHaveBeenCalled()
    expect(wrapper.getComponent(editorStubs.BoardBand).props("label")).toBe("Blue, 2024-2025")
  })

  it("names the photo by the board alone where its year cannot be read", () => {
    const wrapper = mountEditor({...tenth, startDate: "unknown"} as never)

    expect(wrapper.getComponent(editorStubs.BoardBand).props("label")).toBe("Blue")
  })

  it("says what removing the board takes with it, and leaves once it is gone", async () => {
    adapter.dropBoard.mockResolvedValueOnce({ok: false, reason: "Remove the members first."}).mockResolvedValueOnce({ok: true})
    const wrapper = mountEditor(tenth)
    const confirm = () => wrapper.getComponent(editorStubs.ConfirmDialog)

    await wrapper.get("[data-testid=board-edit-remove]").trigger("click")
    expect(confirm().props("open")).toBe(true)
    expect(confirm().props("question")).toContain("holds 2 members")
    confirm().vm.$emit("confirm")
    await flushPromises()
    expect(confirm().props("failure")).toBe("Remove the members first.")
    confirm().vm.$emit("confirm")
    await flushPromises()

    expect(adapter.dropBoard).toHaveBeenLastCalledWith(10)
    expect(wrapper.emitted("removed")).toHaveLength(1)
    expect(confirm().props("open")).toBe(false)
    confirm().vm.$emit("update:open", true)
    await flushPromises()
    expect(confirm().props("open")).toBe(true)
  })

  it("says a board with nobody on it only leaves the timeline, and offers no removal while adding", async () => {
    const empty = mountEditor({...tenth, members: []} as never)
    await empty.get("[data-testid=board-edit-remove]").trigger("click")

    expect(empty.getComponent(editorStubs.ConfirmDialog).props("question")).toBe("Blue holds no members. Removing it takes it off the timeline.")
    expect(mountEditor(null).find("[data-testid=board-edit-remove]").exists()).toBe(false)
  })

  it("refuses to save a colour that is not one, and draws the association's blue meanwhile", async () => {
    const wrapper = mountEditor(tenth)
    wrapper.getComponent({name: "ColourControl"}).vm.$emit("update:modelValue", "pink")
    await flushPromises()

    expect(wrapper.getComponent(editorStubs.EditPage).props("accent")).toBe("var(--color-brand)")
    expect(wrapper.get("[data-testid=board-edit-save]").attributes("data-disabled")).toBe("true")
    await wrapper.get("form").trigger("submit")
    expect(adapter.saveBoardOrReason).not.toHaveBeenCalled()
  })

  it("leaves on Cancel", async () => {
    const wrapper = mountEditor(tenth)
    await wrapper.get("[data-testid=board-edit-cancel]").trigger("click")

    expect(wrapper.emitted("cancel")).toHaveLength(1)
  })
})
