import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import BoardMemberEditor from "@/domains/boards/components/BoardMemberEditor.vue"
import {loadMemberAccounts} from "@/domains/user"
import {editorStubs, write} from "./editorStubs"

const adapter = vi.hoisted(() => ({
  addMemberOrReason: vi.fn(), dropMemberOrReason: vi.fn(), linkMemberAccountOrReason: vi.fn(), saveMemberOrReason: vi.fn(), storeMemberPortrait: vi.fn(),
}))
vi.mock("@/domains/boards/adapters/boards", async importOriginal => ({
  ...(await importOriginal<typeof import("@/domains/boards/adapters/boards")>()),
  ...adapter,
}))
vi.mock("@/domains/user", () => ({loadMemberAccounts: vi.fn()}))

const roos = {
  id: 5, name: "Roos Kruk", nickname: "Rookie", role: "Chair", description: "Runs *it*.", startDate: "2024-09-01", endDate: null, userId: 2,
  portrait: {path: "r.webp", url: "/r.webp", renditions: [{url: "/r-320.webp", width: 320}]},
}
const piet = {id: 6, name: "Piet", nickname: null, role: "Treasurer", description: null, startDate: "2024-09-01", endDate: null, userId: null, portrait: null}
const board = {id: 10, number: 10, name: "Blue", accent: "#65c6cd", startDate: "2024-09-01", endDate: "2025-08-31", members: [roos, piet], version: 1}

const mountEditor = async (member: typeof roos | typeof piet | null, on: Record<string, unknown> = board) => {
  const wrapper = mount(BoardMemberEditor, {props: {board: on, member, back: "/board?board=10"}, global: {stubs: editorStubs}})
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  Object.values(adapter).forEach(one => one.mockReset())
  vi.mocked(loadMemberAccounts).mockReset().mockResolvedValue([{id: 2, name: "Roos Kruk", email: "roos@esa.test"}, {id: 3, name: "Mo", email: null}] as never)
})

describe("the board member edit page", () => {
  it("adds a member on the board's term, previewed open among the board's faces, with an account picked", async () => {
    adapter.addMemberOrReason.mockResolvedValue({ok: true})
    const wrapper = await mountEditor(null)
    const band = () => wrapper.getComponent(editorStubs.SliceBand)

    expect(wrapper.getComponent(editorStubs.EditPage).props()).toMatchObject({title: "Add a member", accent: "#65c6cd"})
    expect(band().props("items").map((one: {title: string}) => one.title)).toContain("New member")
    write(wrapper, "board-member-edit-name", "Sem de Wit")
    write(wrapper, "board-member-edit-nickname", "Semmie")
    write(wrapper, "board-member-edit-role", "Secretary")
    write(wrapper, "board-member-edit-description", "Writes it **down**.")
    await flushPromises()
    expect(wrapper.getComponent(editorStubs.SearchPicker).props("options")).toEqual([
      {key: "2", label: "Roos Kruk", note: "roos@esa.test"}, {key: "3", label: "Mo", note: undefined},
    ])
    wrapper.getComponent(editorStubs.SearchPicker).vm.$emit("pick", "3")
    await flushPromises()
    expect(wrapper.get("[data-testid=board-member-edit-attached]").text()).toContain("Mo")
    expect(band().props("items").find((one: {meta: string}) => one.meta === "Secretary").title).toBe("Sem \"Semmie\" de Wit")
    expect(wrapper.text()).toContain("Writes it")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.addMemberOrReason).toHaveBeenCalledWith(10, {
      role: "Secretary", startDate: "2024-09-01", endDate: "2025-08-31", displayName: "Sem de Wit", nickname: "Semmie",
      description: "Writes it **down**.", portrait: null, userId: 3,
    })
    expect(wrapper.emitted("saved")).toHaveLength(1)
  })

  it("corrects a member, detaching the account, and keeps what was typed when either write is refused", async () => {
    adapter.saveMemberOrReason.mockResolvedValueOnce({ok: false, reason: "Refused."}).mockResolvedValue({ok: true})
    adapter.linkMemberAccountOrReason.mockResolvedValueOnce({ok: false, reason: "Not linked."}).mockResolvedValue({ok: true})
    const wrapper = await mountEditor(roos)
    const portrait = wrapper.getComponent(editorStubs.ImagePicker)
    const file = new File(["x"], "r.png")

    expect(portrait.props("shape")).toBe("portrait")
    await portrait.props("store")(file)
    expect(adapter.storeMemberPortrait).toHaveBeenCalledWith(file)
    expect(wrapper.get("[data-testid=board-member-edit-attached]").text()).toContain("Roos Kruk")
    await wrapper.get("[data-testid=board-member-edit-detach]").trigger("click")
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(wrapper.get("[data-testid=board-member-edit-failure]").text()).toBe("Refused.")

    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(wrapper.get("[data-testid=board-member-edit-failure]").text()).toBe("Not linked.")

    await wrapper.get("[data-testid=board-member-edit-save]").trigger("click")
    await flushPromises()
    expect(adapter.saveMemberOrReason).toHaveBeenLastCalledWith(10, 5, expect.objectContaining({displayName: "Roos Kruk", portrait: "r.webp"}))
    expect(adapter.linkMemberAccountOrReason).toHaveBeenLastCalledWith(10, 5, null)
    expect(wrapper.emitted("saved")).toHaveLength(1)
  })

  it("does not write the account again where it did not change, and never saves without a name, role and start", async () => {
    adapter.saveMemberOrReason.mockResolvedValue({ok: true})
    const wrapper = await mountEditor(piet)

    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(adapter.linkMemberAccountOrReason).not.toHaveBeenCalled()

    write(wrapper, "board-member-edit-role", " ")
    await flushPromises()
    await wrapper.get("form").trigger("submit")
    expect(adapter.saveMemberOrReason).toHaveBeenCalledTimes(1)
    expect(wrapper.get("[data-testid=board-member-edit-save]").attributes("data-disabled")).toBe("true")
  })

  it("says the accounts could not be read rather than that there are none", async () => {
    vi.mocked(loadMemberAccounts).mockResolvedValue(null)
    const unread = await mountEditor(piet)
    expect(unread.getComponent(editorStubs.SearchPicker).props("emptyNote")).toContain("could not be read")

    vi.mocked(loadMemberAccounts).mockResolvedValue([])
    const none = await mountEditor(piet)
    expect(none.getComponent(editorStubs.SearchPicker).props("emptyNote")).toBe("Nobody has an account here yet.")
  })

  it("names an account it has not read by its number, and draws a board without members or a colour", async () => {
    vi.mocked(loadMemberAccounts).mockResolvedValue([])
    const wrapper = await mountEditor({...roos, userId: 44}, {...board, accent: null, members: undefined})

    expect(wrapper.get("[data-testid=board-member-edit-attached]").text()).toContain("Member 44")
    expect(wrapper.getComponent(editorStubs.EditPage).props("accent")).toBe("var(--color-brand)")
    expect(wrapper.getComponent(editorStubs.SliceBand).props("items")).toHaveLength(1)
  })

  it("says what removing the member takes with it, and leaves once they are gone", async () => {
    adapter.dropMemberOrReason.mockResolvedValueOnce({ok: false, reason: "Refused."}).mockResolvedValueOnce({ok: true})
    const wrapper = await mountEditor(roos)
    const confirm = () => wrapper.getComponent(editorStubs.ConfirmDialog)

    await wrapper.get("[data-testid=board-member-edit-remove]").trigger("click")
    expect(confirm().props("question")).toBe(
      "Roos \"Rookie\" Kruk held Chair on this board. Removing the member takes that place out of the association's history."
      + " What they wrote about themselves goes with it.")
    confirm().vm.$emit("confirm")
    await flushPromises()
    expect(confirm().props("failure")).toBe("Refused.")
    confirm().vm.$emit("confirm")
    await flushPromises()
    expect(adapter.dropMemberOrReason).toHaveBeenLastCalledWith(10, 5)
    expect(wrapper.emitted("removed")).toHaveLength(1)
    confirm().vm.$emit("update:open", false)

    const quiet = await mountEditor(piet)
    await quiet.get("[data-testid=board-member-edit-remove]").trigger("click")
    expect(quiet.getComponent(editorStubs.ConfirmDialog).props("question")).not.toContain("wrote about themselves")
    expect((await mountEditor(null)).find("[data-testid=board-member-edit-remove]").exists()).toBe(false)
  })

  it("leaves on Cancel", async () => {
    const wrapper = await mountEditor(roos)
    await wrapper.get("[data-testid=board-member-edit-cancel]").trigger("click")

    expect(wrapper.emitted("cancel")).toHaveLength(1)
  })
})
