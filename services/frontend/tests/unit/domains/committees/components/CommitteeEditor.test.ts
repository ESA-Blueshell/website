import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {h, type VNode} from "vue"
import CommitteeEditor from "@/domains/committees/components/CommitteeEditor.vue"
import type {Committee} from "@/domains/committees/adapters/committees"
import {aCommittee, anImage} from "../../../helpers/apiFixtures"

const adapter = vi.hoisted(() => ({
  addCommittee: vi.fn(),
  saveCommitteeAsBoard: vi.fn(),
  saveOwnCommitteePage: vi.fn(),
  storeCommitteeBanner: vi.fn(),
  storeCommitteeIcon: vi.fn(),
  listCommittees: vi.fn(),
  removeCommittee: vi.fn(),
  saveCommitteeDiscord: vi.fn(),
  readCommitteeDiscord: vi.fn(),
}))
const {mockStore} = vi.hoisted(() => ({mockStore: {commit: vi.fn()}}))
vi.mock("@/plugins/store", () => ({default: mockStore}))
const lists = vi.hoisted(() => ({refreshSharedLists: vi.fn()}))
vi.mock("@/utils/sharedLists", async importOriginal => ({
  ...(await importOriginal<typeof import("@/utils/sharedLists")>()),
  refreshSharedLists: lists.refreshSharedLists,
}))
vi.mock("@/domains/committees/adapters/committees", () => adapter)
vi.mock("@/domains/games", async importOriginal => {
  const {ref} = await import("vue")
  return {
    ...(await importOriginal<typeof import("@/domains/games")>()),
    useCasualGames: () => ({games: ref([{code: "CS2", name: "Counter-Strike 2"}, {code: "CHESS", name: "Chess"}])}),
  }
})

const passThrough = (name: string) => ({name, setup: (_: unknown, {slots}: {slots: Record<string, (() => VNode[]) | undefined>}) =>
  () => h("div", [slots["actions"]?.(), slots["default"]?.(), slots["footer"]?.(), slots["preview"]?.()])})
const ImagePicker = {name: "ImagePicker", props: ["label", "picture", "store", "testid"], emits: ["update:picture"], template: "<div />"}
const EventGamesPicker = {name: "EventGamesPicker", props: ["modelValue", "testid"], emits: ["update:modelValue"], template: "<div />"}
const CommitteeSeats = {name: "CommitteeSeats", props: ["modelValue"], emits: ["update:modelValue"], template: "<div data-testid=committee-edit-member />"}
const ConfirmDialog = {name: "ConfirmDialog", props: ["open", "question", "title", "failure", "working"], emits: ["confirm", "update:open"], template: "<div />"}
const ArtCells = {name: "ArtCells", props: ["cells", "testidPrefix"], template: "<div />"}
const RecordHead = {name: "RecordHead", props: ["title", "archived"], template: "<div data-testid=head><slot /><slot name=\"facts\" /></div>"}
const MarkdownEditor = {name: "MarkdownEditor", props: ["modelValue"], emits: ["update:modelValue"], template: "<div />"}
const DiscordPlaceFields = {name: "DiscordPlaceFields", props: ["modelValue", "read", "name", "slug"], emits: ["update:modelValue"], template: "<div />"}
const stubs = {
  EditPage: {...passThrough("EditPage"), props: ["title", "eyebrow", "back", "testid", "accent"]},
  PreviewFrame: passThrough("PreviewFrame"),
  ImagePicker, EventGamesPicker, CommitteeSeats, ConfirmDialog, ArtCells, RecordHead, MarkdownEditor, DiscordPlaceFields,
  CutButton: {props: ["href", "testid"], template: "<a :href='href' :data-testid='testid'><slot /></a>"},
}

const lan = aCommittee({
  version: 3,
  banner: anImage({url: "/b.webp", path: "b.webp", width: null, height: null, renditions: []}),
  icon: anImage({url: "/i.webp", path: "i.webp", width: null, height: null, renditions: []}),
  gameCodes: ["CS2"],
})

const mountEditor = (committee: Committee | null, asBoard: boolean) =>
  mount(CommitteeEditor, {props: {committee, asBoard, back: "/committees"}, global: {stubs}})

const input = (wrapper: ReturnType<typeof mountEditor>, id: string) => wrapper.get(`[data-testid=committee-edit-${id}] input`)
const describe_ = (wrapper: ReturnType<typeof mountEditor>, text: string) =>
  wrapper.getComponent(MarkdownEditor).vm.$emit("update:modelValue", text)

beforeEach(() => {
  Object.values(adapter).forEach(one => one.mockReset())
})

const seats = (wrapper: ReturnType<typeof mountEditor>) => wrapper.getComponent(CommitteeSeats)

describe("the committee edit page, for the board", () => {
  it("adds a committee with its address following its name, its members, and previews its head and cell", async () => {
    adapter.addCommittee.mockResolvedValue({ok: true, saved: lan})
    const wrapper = mountEditor(null, true)
    await flushPromises()

    expect(wrapper.getComponent(stubs.EditPage).props("title")).toBe("Add a committee")
    await input(wrapper, "name").setValue("Pub Quiz Cie!")
    expect((input(wrapper, "slug").element as HTMLInputElement).value).toBe("pub-quiz-cie")
    await input(wrapper, "slug").setValue("quiz")
    await input(wrapper, "name").setValue("Pub Quiz Cie")
    describe_(wrapper, "Questions.")
    expect(seats(wrapper).props("modelValue")).toEqual([])
    seats(wrapper).vm.$emit("update:modelValue", [{userId: 4, role: " Chair "}])
    await flushPromises()
    expect(wrapper.getComponent(RecordHead).props("title")).toBe("Pub Quiz Cie")
    expect(wrapper.getComponent(ArtCells).props("cells")[0]).toMatchObject({title: "Pub Quiz Cie", sub: "Questions."})
    wrapper.getComponent(EventGamesPicker).vm.$emit("update:modelValue", ["CHESS"])
    expect(wrapper.get("[data-testid=committee-edit-save]").text()).toBe("Add the committee")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.addCommittee).toHaveBeenCalledWith({
      name: "Pub Quiz Cie", slug: "quiz", description: "Questions.", banner: null, icon: null, members: [{userId: 4, role: "Chair"}], gameCodes: ["CHESS"],
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
    expect(seats(wrapper).props("modelValue")).toEqual([{userId: 5, role: ""}])
    seats(wrapper).vm.$emit("update:modelValue", [{userId: 9, role: ""}])
    await flushPromises()
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.saveCommitteeAsBoard).toHaveBeenCalledWith(1, expect.objectContaining({version: 3, slug: "lancie", banner: "b.webp", members: [{userId: 9, role: null}]}))
    expect(wrapper.get("[data-testid=committee-edit-failure]").text()).toBe("The address 'lancie' is already used by LanCie.")
    expect(wrapper.emitted("saved")).toBeUndefined()
  })

  it("saves a committee with nobody on it, and stores its pictures against the committee they are for", async () => {
    adapter.saveCommitteeAsBoard.mockResolvedValue({ok: true, saved: lan})
    const wrapper = mountEditor({...lan, members: []}, true)
    await flushPromises()
    const file = new File(["x"], "b.png")

    const [bannerPicker, iconPicker] = wrapper.findAllComponents(ImagePicker)
    await bannerPicker!.props("store")(file)
    await iconPicker!.props("store")(file)
    bannerPicker!.vm.$emit("update:picture", null)
    iconPicker!.vm.$emit("update:picture", null)
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.storeCommitteeBanner).toHaveBeenCalledWith(file, 1)
    expect(adapter.storeCommitteeIcon).toHaveBeenCalledWith(file, 1)
    expect(adapter.listCommittees).not.toHaveBeenCalled()
    expect(adapter.saveCommitteeAsBoard).toHaveBeenCalledWith(1, expect.objectContaining({version: 3, banner: null, icon: null, members: []}))
  })

  it("deletes a committee once asked, and says why where the api would not", async () => {
    adapter.removeCommittee.mockResolvedValueOnce({ok: false, reason: "The committee could not be deleted."}).mockResolvedValueOnce({ok: true})
    const wrapper = mountEditor({...lan, members: []}, true)
    await flushPromises()
    const dialog = wrapper.getComponent(ConfirmDialog)

    await wrapper.get("[data-testid=committee-edit-remove]").trigger("click")
    expect(dialog.props("open")).toBe(true)
    expect(dialog.props("question")).toContain("Archiving keeps it")
    dialog.vm.$emit("confirm")
    await flushPromises()
    expect(dialog.props("failure")).toBe("The committee could not be deleted.")
    dialog.vm.$emit("confirm")
    dialog.vm.$emit("confirm")
    await flushPromises()

    expect(adapter.removeCommittee).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted("removed")).toHaveLength(1)
    dialog.vm.$emit("update:open", false)
  })
})

describe("the committee edit page, for its own members", () => {
  it("locks the board's fields, and saves the description, banner and games", async () => {
    adapter.saveOwnCommitteePage.mockResolvedValue({ok: true, saved: lan})
    const wrapper = mountEditor(lan, false)
    await flushPromises()

    expect(input(wrapper, "name").attributes("disabled")).toBeDefined()
    expect(input(wrapper, "slug").attributes("disabled")).toBeDefined()
    expect(wrapper.find("[data-testid=committee-edit-member]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=committee-edit-remove]").exists()).toBe(false)
    expect(wrapper.get("[data-testid=committee-edit-fixed]").text()).toBe("The board changes the name, address and members.")
    describe_(wrapper, "LANs, monthly.")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.saveOwnCommitteePage).toHaveBeenCalledWith(1, {description: "LANs, monthly.", banner: "b.webp", icon: "i.webp", gameCodes: ["CS2"], version: 3})
    expect(wrapper.emitted("saved")).toEqual([[lan]])
  })

  it("sets the committee's Discord once it is saved, and says where Discord refused without holding the committee back", async () => {
    adapter.addCommittee.mockResolvedValue({ok: true, saved: lan})
    adapter.saveCommitteeDiscord.mockResolvedValueOnce({ok: true, saved: {available: true, channels: []}})
    const wrapper = mountEditor(null, true)
    await flushPromises()
    await input(wrapper, "name").setValue("Pub Quiz Cie")
    describe_(wrapper, "Questions.")
    const fields = wrapper.getComponent(DiscordPlaceFields)
    expect(fields.props("read")).toBeNull()
    const choice = {createRole: true, channelIds: [], createChannel: "pub-quiz-cie"}
    fields.vm.$emit("update:modelValue", choice)
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(adapter.saveCommitteeDiscord).toHaveBeenCalledWith(lan.id, choice)
    expect(wrapper.emitted("saved")).toHaveLength(1)

    const editing = mountEditor(lan, true)
    await flushPromises()
    await (editing.getComponent(DiscordPlaceFields).props("read") as () => Promise<unknown>)()
    expect(adapter.readCommitteeDiscord).toHaveBeenCalledWith(lan.id)

    adapter.saveCommitteeDiscord.mockResolvedValueOnce({ok: false, reason: "Discord cannot be reached now."})
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Discord cannot be reached now.")
    expect(wrapper.emitted("saved")).toHaveLength(2)
  })

  it("leaves on Cancel", async () => {
    const wrapper = mountEditor(lan, false)

    await wrapper.get("[data-testid=committee-edit-cancel]").trigger("click")

    expect(wrapper.emitted("cancel")).toHaveLength(1)
  })
})
