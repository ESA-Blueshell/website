import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import CommitteeDialog from "@/domains/committees/island/CommitteeDialog.vue"

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

const ModalDialog = {name: "ModalDialog", props: ["open", "title", "testid"], emits: ["update:open"], template: "<div><slot /><slot name=\"footer\" /></div>"}
const ImagePicker = {name: "ImagePicker", props: ["label", "picture", "store", "testid"], emits: ["update:picture"], template: "<div />"}
const EventGamesPicker = {name: "EventGamesPicker", props: ["modelValue", "testid"], emits: ["update:modelValue"], template: "<div />"}
const SearchPicker = {name: "SearchPicker", props: ["options", "placeholder", "testidPrefix", "emptyNote"], emits: ["pick"], template: "<div />"}
const ArtCells = {name: "ArtCells", props: ["cells", "testidPrefix"], template: "<div />"}
const stubs = {ModalDialog, ImagePicker, EventGamesPicker, SearchPicker, ArtCells}

const lan = {
  id: 1, name: "LanCie", slug: "lancie", description: "LANs", listed: true, archived: false, version: 3,
  banner: {url: "/b.webp", path: "b.webp", renditions: []}, gameCodes: ["CS2"], createdAt: "", updatedAt: "",
}

const mountDialog = (committee: typeof lan | null, asBoard: boolean) =>
  mount(CommitteeDialog, {props: {open: true, committee, asBoard}, global: {stubs}})

beforeEach(() => {
  Object.values(adapter).forEach(one => one.mockReset())
  users.loadMemberAccounts.mockReset().mockResolvedValue([{id: 4, name: "Nelly Bee", email: "n@x.nl"}, {id: 5, name: "Mo", email: null}])
})

describe("the committee dialog, for the board", () => {
  it("adds a committee with its address following its name, its members and a preview of its cell", async () => {
    adapter.addCommittee.mockResolvedValue({ok: true, committee: lan})
    const wrapper = mountDialog(null, true)
    await flushPromises()

    await wrapper.get("[data-testid=committee-dialog-name]").setValue("Pub Quiz Cie!")
    expect((wrapper.get("[data-testid=committee-dialog-slug]").element as HTMLInputElement).value).toBe("pub-quiz-cie")
    await wrapper.get("[data-testid=committee-dialog-slug]").setValue("quiz")
    await wrapper.get("[data-testid=committee-dialog-name]").setValue("Pub Quiz Cie")
    await wrapper.get("[data-testid=committee-dialog-description]").setValue("Questions.")
    await wrapper.get("[data-testid=committee-dialog-listed]").setValue(false)
    expect(wrapper.getComponent(SearchPicker).props("options")).toHaveLength(2)
    wrapper.getComponent(SearchPicker).vm.$emit("pick", "4")
    await flushPromises()
    await wrapper.get("[data-testid=committee-dialog-seat-4] input").setValue("Chair")
    expect(wrapper.get("[data-testid=committee-dialog-seat-4]").text()).toContain("Nelly Bee")
    expect(wrapper.getComponent(SearchPicker).props("options")).toEqual([{key: "5", label: "Mo", note: undefined}])
    expect(wrapper.getComponent(ArtCells).props("cells")[0]).toMatchObject({title: "Pub Quiz Cie", sub: "Questions."})
    wrapper.getComponent(EventGamesPicker).vm.$emit("update:modelValue", ["CHESS"])
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.addCommittee).toHaveBeenCalledWith({
      name: "Pub Quiz Cie", slug: "quiz", listed: false, description: "Questions.", banner: null, members: [{userId: 4, role: "Chair"}], gameCodes: ["CHESS"],
    })
    expect(wrapper.emitted("saved")).toEqual([[lan]])
    expect(wrapper.emitted("update:open")).toEqual([[false]])
    expect(wrapper.get("[data-testid=committee-dialog-save]").text()).toBe("Add the committee")
  })

  it("corrects a committee from its seats as the board's list holds them, and keeps what was typed when refused", async () => {
    adapter.listCommittees.mockResolvedValue([{...lan, members: [{userId: 5, role: null}]}])
    adapter.saveCommitteeAsBoard.mockResolvedValue({ok: false, reason: "The address 'lancie' is already used by LanCie."})
    const wrapper = mountDialog(lan, true)
    await flushPromises()

    expect(wrapper.get("[data-testid=committee-dialog-seat-5]").text()).toContain("Mo")
    await wrapper.get("[data-testid=committee-dialog-seat-5] button").trigger("click")
    wrapper.getComponent(SearchPicker).vm.$emit("pick", "9")
    await flushPromises()
    expect(wrapper.get("[data-testid=committee-dialog-seat-9]").text()).toContain("Member 9")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.saveCommitteeAsBoard).toHaveBeenCalledWith(1, 3, expect.objectContaining({slug: "lancie", banner: "b.webp", members: [{userId: 9, role: null}]}))
    expect(wrapper.get("[data-testid=committee-dialog-failure]").text()).toBe("The address 'lancie' is already used by LanCie.")
    expect(wrapper.emitted("saved")).toBeUndefined()
  })

  it("never saves a committee without a member, and stores a banner against the committee it is for", async () => {
    adapter.listCommittees.mockResolvedValue([])
    users.loadMemberAccounts.mockResolvedValue(null)
    const wrapper = mountDialog({...lan, members: []} as never, true)
    await flushPromises()
    const file = new File(["x"], "b.png")

    await wrapper.getComponent(ImagePicker).props("store")(file)
    wrapper.getComponent(ImagePicker).vm.$emit("update:picture", null)
    await wrapper.get("form").trigger("submit")

    expect(adapter.storeCommitteeBanner).toHaveBeenCalledWith(file, 1)
    expect(adapter.listCommittees).not.toHaveBeenCalled()
    expect(adapter.saveCommitteeAsBoard).not.toHaveBeenCalled()
    expect(wrapper.get("[data-testid=committee-dialog-save]").attributes("disabled")).toBeDefined()
  })
})

describe("the committee dialog, for its own members", () => {
  it("shows the board's fields read-only, and saves the description, banner and games", async () => {
    adapter.saveOwnCommitteePage.mockResolvedValue({ok: true, committee: lan})
    const wrapper = mountDialog({...lan, listed: false}, false)
    await flushPromises()

    expect(wrapper.find("[data-testid=committee-dialog-name]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=committee-dialog-member-search]").exists()).toBe(false)
    expect(wrapper.get("[data-testid=committee-dialog-fixed]").text()).toContain("LanCie at /committees/lancie, not listed.")
    expect(wrapper.get("[data-testid=committee-dialog-fixed]").text()).toContain("The board changes these.")
    expect(users.loadMemberAccounts).not.toHaveBeenCalled()
    await wrapper.get("[data-testid=committee-dialog-description]").setValue("LANs, monthly.")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.saveOwnCommitteePage).toHaveBeenCalledWith(1, {description: "LANs, monthly.", banner: "b.webp", gameCodes: ["CS2"]})
    expect(wrapper.emitted("saved")).toEqual([[lan]])
    expect(mountDialog(lan, false).get("[data-testid=committee-dialog-fixed]").text()).toContain(", listed.")
  })

  it("closes on Cancel", async () => {
    const wrapper = mountDialog(lan, false)

    await wrapper.get("[data-testid=committee-dialog-cancel]").trigger("click")
    wrapper.getComponent(ModalDialog).vm.$emit("update:open", false)

    expect(wrapper.emitted("update:open")).toEqual([[false], [false]])
  })
})
