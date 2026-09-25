import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {h} from "vue"
import CommitteeEditor from "@/domains/committees/components/CommitteeEditor.vue"

const adapter = vi.hoisted(() => ({
  addCommittee: vi.fn(),
  saveCommitteeAsBoard: vi.fn(),
  saveOwnCommitteePage: vi.fn(),
  storeCommitteeBanner: vi.fn(),
  listCommittees: vi.fn(),
}))
vi.mock("@/domains/committees/adapters/committees", () => adapter)
const users = vi.hoisted(() => ({loadMemberAccounts: vi.fn()}))
vi.mock("@/domains/user", () => users)
vi.mock("@/domains/games", async importOriginal => {
  const {ref} = await import("vue")
  return {
    ...(await importOriginal<typeof import("@/domains/games")>()),
    useCasualGames: () => ({games: ref([{code: "CS2", name: "Counter-Strike 2"}, {code: "CHESS", name: "Chess"}])}),
  }
})

const passThrough = (name: string) => ({name, setup: (_: unknown, {slots}: {slots: Record<string, () => unknown>}) =>
  () => h("div", [slots["actions"]?.(), slots["default"]?.(), slots["footer"]?.(), slots["preview"]?.()])})
const ImagePicker = {name: "ImagePicker", props: ["label", "picture", "store", "testid"], emits: ["update:picture"], template: "<div />"}
const EventGamesPicker = {name: "EventGamesPicker", props: ["modelValue", "testid"], emits: ["update:modelValue"], template: "<div />"}
const SearchPicker = {name: "SearchPicker", props: ["options", "placeholder", "testidPrefix", "emptyNote"], emits: ["pick"], template: "<div />"}
const ArtCells = {name: "ArtCells", props: ["cells", "testidPrefix"], template: "<div />"}
const RecordHead = {name: "RecordHead", props: ["title", "archived"], template: "<div data-testid=head><slot /><slot name=\"facts\" /></div>"}
const MarkdownEditor = {name: "MarkdownEditor", props: ["modelValue"], emits: ["update:modelValue"], template: "<div />"}
const stubs = {
  EditPage: {...passThrough("EditPage"), props: ["title", "eyebrow", "back", "testid", "accent"]},
  PreviewFrame: passThrough("PreviewFrame"),
  ImagePicker, EventGamesPicker, SearchPicker, ArtCells, RecordHead, MarkdownEditor,
  CutButton: {props: ["href", "testid", "disabled"], template: "<a :href='href' :data-testid='testid' :data-disabled='disabled'><slot /></a>"},
}

const lan = {
  id: 1, name: "LanCie", slug: "lancie", description: "LANs", listed: true, archived: false, version: 3,
  banner: {url: "/b.webp", path: "b.webp", renditions: []}, gameCodes: ["CS2"], createdAt: "", updatedAt: "",
}

const mountEditor = (committee: typeof lan | null, asBoard: boolean) =>
  mount(CommitteeEditor, {props: {committee, asBoard, back: "/committees"}, global: {stubs}})

const input = (wrapper: ReturnType<typeof mountEditor>, id: string) => wrapper.get(`[data-testid=committee-edit-${id}] input`)
const describe_ = (wrapper: ReturnType<typeof mountEditor>, text: string) =>
  wrapper.getComponent(MarkdownEditor).vm.$emit("update:modelValue", text)

beforeEach(() => {
  Object.values(adapter).forEach(one => one.mockReset())
  users.loadMemberAccounts.mockReset().mockResolvedValue([{id: 4, name: "Nelly Bee", email: "n@x.nl"}, {id: 5, name: "Mo", email: null}])
})

describe("the committee edit page, for the board", () => {
  it("adds a committee with its address following its name, its members, and previews its head and cell", async () => {
    adapter.addCommittee.mockResolvedValue({ok: true, committee: lan})
    const wrapper = mountEditor(null, true)
    await flushPromises()

    expect(wrapper.getComponent(stubs.EditPage).props("title")).toBe("Add a committee")
    await input(wrapper, "name").setValue("Pub Quiz Cie!")
    expect((input(wrapper, "slug").element as HTMLInputElement).value).toBe("pub-quiz-cie")
    await input(wrapper, "slug").setValue("quiz")
    await input(wrapper, "name").setValue("Pub Quiz Cie")
    describe_(wrapper, "Questions.")
    await wrapper.get("input[data-testid=committee-edit-listed]").setValue(false)
    expect(wrapper.getComponent(SearchPicker).props("options")).toHaveLength(2)
    wrapper.getComponent(SearchPicker).vm.$emit("pick", "4")
    await flushPromises()
    await wrapper.get("[data-testid=committee-edit-role-4] input").setValue("Chair")
    expect(wrapper.get("[data-testid=committee-edit-seat-4]").text()).toContain("Nelly Bee")
    expect(wrapper.getComponent(SearchPicker).props("options")).toEqual([{key: "5", label: "Mo", note: undefined}])
    expect(wrapper.getComponent(RecordHead).props("title")).toBe("Pub Quiz Cie")
    expect(wrapper.getComponent(ArtCells).props("cells")[0]).toMatchObject({title: "Pub Quiz Cie", sub: "Questions."})
    wrapper.getComponent(EventGamesPicker).vm.$emit("update:modelValue", ["CHESS"])
    expect(wrapper.get("[data-testid=committee-edit-save]").text()).toBe("Add the committee")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.addCommittee).toHaveBeenCalledWith({
      name: "Pub Quiz Cie", slug: "quiz", listed: false, description: "Questions.", banner: null, members: [{userId: 4, role: "Chair"}], gameCodes: ["CHESS"],
    })
    expect(wrapper.emitted("saved")).toEqual([[lan]])
  })

  it("corrects a committee from its seats as the board's list holds them, and keeps what was typed when refused", async () => {
    adapter.listCommittees.mockResolvedValue([{...lan, members: [{userId: 5, role: null}]}])
    adapter.saveCommitteeAsBoard.mockResolvedValue({ok: false, reason: "The address 'lancie' is already used by LanCie."})
    const wrapper = mountEditor(lan, true)
    await flushPromises()

    expect(wrapper.get("[data-testid=committee-edit-see]").attributes("href")).toBe("/committees/lancie")
    expect(wrapper.get("[data-testid=head]").text()).toContain("Counter-Strike 2")
    wrapper.getComponent(EventGamesPicker).vm.$emit("update:modelValue", ["CS2", "CHESS"])
    await flushPromises()
    expect(wrapper.get("[data-testid=head]").text()).toContain("Counter-Strike 2 · Chess")
    expect(wrapper.get("[data-testid=committee-edit-seat-5]").text()).toContain("Mo")
    await wrapper.get("[data-testid=committee-edit-unseat-5]").trigger("click")
    wrapper.getComponent(SearchPicker).vm.$emit("pick", "9")
    await flushPromises()
    expect(wrapper.get("[data-testid=committee-edit-seat-9]").text()).toContain("Member 9")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.saveCommitteeAsBoard).toHaveBeenCalledWith(1, 3, expect.objectContaining({slug: "lancie", banner: "b.webp", members: [{userId: 9, role: null}]}))
    expect(wrapper.get("[data-testid=committee-edit-failure]").text()).toBe("The address 'lancie' is already used by LanCie.")
    expect(wrapper.emitted("saved")).toBeUndefined()
  })

  it("never saves a committee without a member, and stores a banner against the committee it is for", async () => {
    adapter.listCommittees.mockResolvedValue([])
    users.loadMemberAccounts.mockResolvedValue(null)
    const wrapper = mountEditor({...lan, members: []} as never, true)
    await flushPromises()
    const file = new File(["x"], "b.png")

    await wrapper.getComponent(ImagePicker).props("store")(file)
    wrapper.getComponent(ImagePicker).vm.$emit("update:picture", null)
    await wrapper.get("form").trigger("submit")

    expect(adapter.storeCommitteeBanner).toHaveBeenCalledWith(file, 1)
    expect(adapter.listCommittees).not.toHaveBeenCalled()
    expect(adapter.saveCommitteeAsBoard).not.toHaveBeenCalled()
    expect(wrapper.get("[data-testid=committee-edit-save]").attributes("data-disabled")).toBe("true")
  })
})

describe("the committee edit page, for its own members", () => {
  it("locks the board's fields, and saves the description, banner and games", async () => {
    adapter.saveOwnCommitteePage.mockResolvedValue({ok: true, committee: lan})
    const wrapper = mountEditor({...lan, listed: false}, false)
    await flushPromises()

    expect(input(wrapper, "name").attributes("disabled")).toBeDefined()
    expect(input(wrapper, "slug").attributes("disabled")).toBeDefined()
    expect(wrapper.get("input[data-testid=committee-edit-listed]").attributes("disabled")).toBeDefined()
    expect(wrapper.find("[data-testid=committee-edit-member]").exists()).toBe(false)
    expect(wrapper.get("[data-testid=committee-edit-fixed]").text()).toBe("The board changes the name, address, listing and members.")
    expect(users.loadMemberAccounts).not.toHaveBeenCalled()
    describe_(wrapper, "LANs, monthly.")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.saveOwnCommitteePage).toHaveBeenCalledWith(1, {description: "LANs, monthly.", banner: "b.webp", gameCodes: ["CS2"]})
    expect(wrapper.emitted("saved")).toEqual([[lan]])
  })

  it("leaves on Cancel", async () => {
    const wrapper = mountEditor(lan, false)

    await wrapper.get("[data-testid=committee-edit-cancel]").trigger("click")

    expect(wrapper.emitted("cancel")).toHaveLength(1)
  })
})
