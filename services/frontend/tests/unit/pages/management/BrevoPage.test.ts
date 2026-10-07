import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {DOMWrapper, type VueWrapper} from "@vue/test-utils"
import BrevoPage from "@/pages/management/BrevoPage.vue"
import {mountInApp, settle, sortByEveryHead, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findTargetOverview: vi.fn(),
  createMissingTargets: vi.fn(),
  archiveExternalTarget: vi.fn(),
  listCohortTargetFolders: vi.fn(),
  createExternalTarget: vi.fn(),
  createTargetFolder: vi.fn(),
  previewFolderTidy: vi.fn(),
  applyFolderTidy: vi.fn(),
  listTargetFolderStates: vi.fn(),
  reconcileTarget: vi.fn(),
}))
const {mockStore} = vi.hoisted(() => ({mockStore: {commit: vi.fn(), getters: {isAdmin: false} as Record<string, unknown>}}))

vi.mock("@/plugins/store", () => ({default: mockStore}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const overview = (missing = [{targetId: 3, cohortId: 103, cohortLabel: "Paid 2026-2027", cohortType: "PERIOD_PAYERS", folder: "Contribution paid",
  memberCount: 142, creating: false}]) => ({
  lists: [
    {externalId: "7", label: "Members 2025-2026", folderLabel: "Members", memberCount: 211, targetId: 1, cohortId: 101,
      cohortLabel: "Members 2025-2026", cohortType: "PERIOD_MEMBERS", missing: 0, extra: 0, lastReconciledAt: "2026-10-01T03:00:00Z", enforced: false},
    {externalId: "9", label: "Old newsletter test", memberCount: 4, enforced: false},
    {externalId: "10", label: "LAN party 2024", folderLabel: "Archive", memberCount: 57, enforced: false},
  ],
  missing,
  lastReconciledAt: "2026-10-01T03:00:00Z",
})
const made = {system: "BREVO", externalId: "11", kind: "LIST", label: "Pub quiz", folderLabel: "Projects", path: ["Brevo", "Projects"]}

const form = () => new DOMWrapper(document.body.querySelector("form")!)
const inBody = (testid: string) => document.body.querySelector(`[data-testid="${testid}"]`)
const press = async (testid: string) => {
  await new DOMWrapper(inBody(testid)!).trigger("click")
  await settle()
}
/** Sends the form to its preview, then goes on to the confirmation and confirms. */
const createTheList = async () => {
  await form().trigger("submit")
  await settle()
  await press("brevo-new-continue")
  await press("brevo-new-confirm")
}

describe("the Brevo page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(BrevoPage)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.findTargetOverview.mockResolvedValue({status: 200, data: overview()})
    api.createMissingTargets.mockResolvedValue({status: 200, data: {queued: 1}})
    api.archiveExternalTarget.mockResolvedValue({status: 200, data: {...made, externalId: "9", label: "Old newsletter test", folderLabel: "Archive"}})
    api.listCohortTargetFolders.mockResolvedValue({status: 200, data: ["Members", "Projects"]})
    api.createExternalTarget.mockResolvedValue({status: 200, data: made})
    api.createTargetFolder.mockResolvedValue({status: 200, data: ["Members", "Projects", "New"]})
    api.previewFolderTidy.mockResolvedValue({status: 200, data: {
      moves: [
        {externalId: "7", label: "Members 2025-2026", to: "Members", byName: false},
        {externalId: "8", label: "Sitecie", from: "Old", to: "Committees", byName: false},
        {externalId: "9", label: "Contribution Paid 2024-2025", from: "Old", to: "Contributions", byName: true},
      ],
      foldersToCreate: ["Committees"],
      lastApplied: {appliedAt: "2026-09-01T10:00:00Z", appliedByName: "Alice Board", moved: 2, failed: 0},
    }})
    api.applyFolderTidy.mockResolvedValue({status: 200, data: {moved: [made], failed: []}})
    api.listTargetFolderStates.mockResolvedValue({status: 200, data: []})
  })

  it("compares the ticked lists with Brevo together, a list still to be created carrying no tick", async () => {
    api.reconcileTarget.mockResolvedValue({status: 202, data: undefined})
    const wrapper = await mount()
    const bulk = () => wrapper.findComponent({name: "BulkAdd"})

    expect(wrapper.find('[data-testid="brevo-check-missing-3"]').exists()).toBe(false)
    wrapper.findComponent({name: "ManagementTable"}).vm.$emit("selectAll")
    await settle()
    expect(wrapper.get('[data-testid="brevo-selection"]').text()).toContain("3 selected")
    wrapper.findComponent({name: "ManagementTable"}).vm.$emit("clearSelection")
    wrapper.findComponent({name: "ManagementTable"}).vm.$emit("toggleShown")
    await settle()
    await wrapper.get('[data-testid="brevo-check-list-9"]').setValue(false)
    await wrapper.get('[data-testid="brevo-bulk-push"]').trigger("click")
    await settle()
    expect(bulk().props("title")).toBe("Add missing people")
    expect(bulk().props("items")).toEqual([])
    expect(bulk().props("pickable")).toBe(false)
    bulk().vm.$emit("update:open", false)
    await settle()

    await wrapper.get('[data-testid="brevo-bulk-remove"]').trigger("click")
    await settle()
    expect(bulk().props("title")).toBe("Remove additional people")
    expect(bulk().props("pickable")).toBe(true)
    bulk().vm.$emit("update:open", false)
    await settle()

    await wrapper.get('[data-testid="brevo-bulk-compare"]').trigger("click")
    await settle()
    expect(bulk().props("open")).toBe(true)
    expect(bulk().props("items").map((one: {name: string}) => one.name)).toEqual(["Members 2025-2026"])
    expect(bulk().props("skipped")).toEqual([{name: "LAN party 2024", why: "Follows nothing on the site"}])
    expect(await bulk().props("run")(bulk().props("items")[0])).toEqual({ok: true})
    expect(api.reconcileTarget).toHaveBeenCalledWith({path: {id: 101, targetId: 1}})

    api.findTargetOverview.mockClear()
    bulk().vm.$emit("done")
    await settle()
    expect(api.findTargetOverview).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="brevo-selection"]').text()).toContain("0 selected")
  })

  it("says which folders want a hand, and reads the lists again once they are seen to", async () => {
    api.listTargetFolderStates.mockResolvedValue({status: 200, data: [{id: "3", name: "Members", targets: 2}, {id: "4", name: "Old", targets: 0}]})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="brevo-folders-empty"]').text()).toContain("1 folder holds no list: Old.")
    api.findTargetOverview.mockClear()
    wrapper.findComponent({name: "FolderCare"}).vm.$emit("changed")
    await settle()
    expect(api.findTargetOverview).toHaveBeenCalledTimes(1)
  })

  afterEach(() => unmountAll(wrappers, "BrevoPage"))

  it("shows the missing list first with a notice, and every list in one table with its folder", async () => {
    const wrapper = await mount()

    expect(api.findTargetOverview).toHaveBeenCalledWith({path: {system: "BREVO"}})
    expect(wrapper.get('[data-testid="brevo-missing"]').text()).toContain("1 list the site expects is missing")
    expect(wrapper.get('[data-testid="brevo-create-missing"]').text()).toBe("Review and create the list")
    expect(wrapper.get('[data-testid="brevo-state-missing-3"]').text()).toBe("Not created yet")
    expect(wrapper.get('[data-testid="brevo-state-list-7"]').text()).toBe("In sync")
    expect(wrapper.get('[data-testid="brevo-row-list-7"] a').attributes("to")).toBe("/management/platforms/brevo/lists/7")
    expect(wrapper.get('[data-testid="brevo-folder-list-7"]').text()).toBe("Members")
    expect(wrapper.get('[data-testid="brevo-folder-list-9"]').text()).toContain("made by hand in Brevo")
    expect(wrapper.get('[data-testid="brevo-row-list-10"]').text()).toContain("LAN party 2024")
    expect(wrapper.get('[data-testid="brevo-state-list-10"]').text()).toBe("Archived")
    expect(wrapper.findAll('[data-testid^="brevo-row-"]')[0]!.attributes("data-testid")).toBe("brevo-row-missing-3")
    expect(wrapper.find('[data-testid="brevo-archive-10"]').exists()).toBe(false)

    expect(await sortByEveryHead(wrapper)).toBe(5)
    wrapper.findComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "nothing like it")
    await settle()
    expect(wrapper.get('[data-testid="brevo-empty"]').exists()).toBe(true)
  })

  it("creates one missing list or all of them, archives a list made by hand, and says a refusal", async () => {
    const wrapper = await mount()

    // Nothing is created by the first press: the list is shown, then asked about once more.
    await wrapper.get('[data-testid="brevo-create-3"]').trigger("click")
    await settle()
    expect(inBody("brevo-create-preview")?.textContent).toContain("Nothing is made in Brevo yet.")
    expect(api.createMissingTargets).not.toHaveBeenCalled()
    await press("brevo-create-continue")
    expect(inBody("brevo-create-confirm")?.textContent).toContain("cannot be taken back from here")
    await press("brevo-create-back")
    expect(inBody("brevo-create-preview")).not.toBeNull()
    await press("brevo-create-continue")
    expect(api.createMissingTargets).not.toHaveBeenCalled()
    await press("brevo-create-go")
    expect(api.createMissingTargets).toHaveBeenCalledWith({path: {system: "BREVO"}, body: {targetIds: [3]}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "The list is being created.")

    api.createMissingTargets.mockResolvedValueOnce({status: 200, data: {queued: 2}})
    await wrapper.get('[data-testid="brevo-create-missing"]').trigger("click")
    await settle()
    await press("brevo-create-continue")
    await press("brevo-create-go")
    expect(api.createMissingTargets).toHaveBeenLastCalledWith({path: {system: "BREVO"}, body: {targetIds: [3]}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "2 lists are being created.")

    await wrapper.get('[data-testid="brevo-archive-9"]').trigger("click")
    await settle()
    expect(api.archiveExternalTarget).toHaveBeenCalledWith({path: {system: "BREVO", externalId: "9"}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Old newsletter test is in the archive.")

    api.archiveExternalTarget.mockResolvedValueOnce({status: 502, error: {code: "TargetSystemRefused", system: "Brevo", reason: "down"}})
    await wrapper.get('[data-testid="brevo-archive-9"]').trigger("click")
    await settle()
    api.createMissingTargets.mockResolvedValueOnce({status: 500, error: {}})
    await wrapper.get('[data-testid="brevo-create-3"]').trigger("click")
    await settle()
    await press("brevo-create-continue")
    await press("brevo-create-go")
    expect(inBody("brevo-create-refusal")?.textContent).toContain("The lists could not be created.")
    wrapper.findAllComponents({name: "ModalDialog"})[2]!.vm.$emit("update:open", false)
    await settle()
    expect(wrapper.findAllComponents({name: "ModalDialog"})[2]!.props("open")).toBe(false)
  })

  it("makes a new list in a new folder, and says why Brevo refused one", async () => {
    api.findTargetOverview.mockResolvedValue({status: 200, data: overview([{targetId: 3, cohortId: 103, cohortLabel: "A", cohortType: "PERIOD_PAYERS",
      folder: "Contribution paid", memberCount: 1, creating: true}, {targetId: 4, cohortId: 104, cohortLabel: "B", cohortType: "PERIOD_PAYERS",
      folder: null, memberCount: 1, creating: false}])})
    const wrapper = await mount()
    expect(wrapper.get('[data-testid="brevo-state-missing-3"]').text()).toBe("Being created")
    expect(wrapper.get('[data-testid="brevo-create-missing"]').text()).toBe("Review and create 2 lists")

    await wrapper.get('[data-testid="brevo-new-list"]').trigger("click")
    await settle()
    const inputs = () => wrapper.findAllComponents({name: "TextInput"})
    inputs()[0].vm.$emit("update:modelValue", "Pub quiz")
    wrapper.findComponent({name: "SearchPicker"}).vm.$emit("pick", "__new__")
    await settle()
    inputs()[1].vm.$emit("update:modelValue", "New")
    await settle()
    await form().trigger("submit")
    await settle()
    expect(inBody("brevo-new-step-preview")?.textContent).toContain("New, a new folder created first")
    expect(api.createExternalTarget).not.toHaveBeenCalled()
    await press("brevo-new-continue")
    expect(inBody("brevo-new-step-confirm")).not.toBeNull()
    await press("brevo-new-back")
    await press("brevo-new-continue")
    await press("brevo-new-confirm")
    expect(api.createTargetFolder).toHaveBeenCalledWith({path: {system: "BREVO"}, body: {name: "New"}})
    expect(api.createExternalTarget).toHaveBeenCalledWith({path: {system: "BREVO"}, body: {name: "Pub quiz", folder: "New"}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Pub quiz is made.")

    await wrapper.get('[data-testid="brevo-new-list"]').trigger("click")
    await settle()
    inputs()[0].vm.$emit("update:modelValue", "Pub quiz")
    wrapper.findComponent({name: "SearchPicker"}).vm.$emit("pick", "Members")
    api.createExternalTarget.mockResolvedValueOnce({status: 502, error: {code: "TargetSystemRefused", system: "Brevo", reason: "Name taken"}})
    await settle()
    await createTheList()
    expect(api.createExternalTarget).toHaveBeenLastCalledWith({path: {system: "BREVO"}, body: {name: "Pub quiz", folder: "Members"}})
    expect(document.body.querySelector('[data-testid="brevo-new-refusal"]')?.textContent).toContain("Name taken")

    api.createTargetFolder.mockResolvedValueOnce({status: 502, error: {code: "TargetSystemRefused", system: "Brevo", reason: "No"}})
    await press("brevo-new-back")
    await press("brevo-new-back")
    wrapper.findComponent({name: "SearchPicker"}).vm.$emit("pick", "__new__")
    await settle()
    inputs()[1].vm.$emit("update:modelValue", "Other")
    await settle()
    await createTheList()
    expect(api.createTargetFolder).toHaveBeenLastCalledWith({path: {system: "BREVO"}, body: {name: "Other"}})
  })

  it("draws each list as a row on a phone, a missing one saying so", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = await mount()
    vi.unstubAllGlobals()

    expect(wrapper.get('[data-testid="brevo-row-missing-3"]').text()).toContain("Not created yet")
    expect(wrapper.get('[data-testid="brevo-row-list-7-open"]').attributes("to")).toBe("/management/platforms/brevo/lists/7")
  })

  it("says Brevo could not be read", async () => {
    api.findTargetOverview.mockResolvedValue({status: 502, error: {}})
    api.listCohortTargetFolders.mockRejectedValue(new Error("down"))
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="brevo-unreadable"]').exists()).toBe(true)
    await wrapper.get('[data-testid="brevo-new-list"]').trigger("click")
    await settle()
    expect(wrapper.findComponent({name: "SearchPicker"}).props("options")).toEqual([{key: "__new__", label: "New folder…"}])
    wrapper.findAllComponents({name: "ModalDialog"})[1].vm.$emit("update:open", false)
    await settle()
    expect(wrapper.findAllComponents({name: "ModalDialog"})[1].props("open")).toBe(false)
  })

  it("previews the folder tidy with every move ticked, applies the ones left ticked, and keeps a refusal in view", async () => {
    const wrapper = await mount()
    await wrapper.get('[data-testid="brevo-tidy"]').trigger("click")
    await settle()

    expect(api.previewFolderTidy).toHaveBeenCalledWith({path: {system: "BREVO"}, throwOnError: true})
    expect(document.body.querySelector('[data-testid="brevo-tidy-last"]')?.textContent).toContain("by Alice Board: 2 lists moved")
    expect(document.body.textContent).toContain("Members 2025-2026: no folder to Members")
    expect(document.body.textContent).toContain("Creates Committees first.")
    // A list matched only by its name is offered apart, and left for the reader to tick.
    expect(document.body.querySelector('[data-testid="brevo-tidy-named"]')?.textContent).toContain("Contribution Paid 2024-2025: Old to Contributions")
    expect(document.body.querySelector('[data-testid="brevo-tidy-moves"]')?.textContent).not.toContain("Contribution Paid")
    const boxes = () => wrapper.findAllComponents({name: "CheckBox"})
    expect(boxes()[2].props("modelValue")).toBe(false)
    boxes()[2].vm.$emit("update:modelValue", true)
    await settle()
    expect(boxes()[2].props("modelValue")).toBe(true)
    boxes()[2].vm.$emit("update:modelValue", false)
    boxes()[1].vm.$emit("update:modelValue", false)
    boxes()[1].vm.$emit("update:modelValue", true)
    boxes()[1].vm.$emit("update:modelValue", false)
    await settle()
    expect(document.body.querySelector('[data-testid="brevo-tidy-apply"]')?.textContent?.trim()).toBe("Move 1 list")
    await new DOMWrapper(document.body.querySelector('[data-testid="brevo-tidy-apply"]')!).trigger("click")
    await settle()
    expect(api.applyFolderTidy).toHaveBeenCalledWith({path: {system: "BREVO"}, body: {externalIds: ["7"]}, throwOnError: true})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "1 list moved.")
    expect(wrapper.findAllComponents({name: "ModalDialog"})[0].props("open")).toBe(false)

    api.applyFolderTidy.mockResolvedValueOnce({status: 200, data: {moved: [], failed: [{externalId: "7", label: "Members 2025-2026", message: "Brevo said no"}]}})
    await wrapper.get('[data-testid="brevo-tidy"]').trigger("click")
    await settle()
    await new DOMWrapper(document.body.querySelector('[data-testid="brevo-tidy-apply"]')!).trigger("click")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "0 lists moved.")
    expect(document.body.querySelector('[data-testid="brevo-tidy-failure"]')?.textContent).toContain("Members 2025-2026: Brevo said no")

    api.applyFolderTidy.mockRejectedValueOnce(new Error("down"))
    await new DOMWrapper(document.body.querySelector('[data-testid="brevo-tidy-apply"]')!).trigger("click")
    await settle()
    expect(document.body.querySelector('[data-testid="brevo-tidy-refusal"]')?.textContent).toContain("could not be applied")

    wrapper.findAllComponents({name: "ModalDialog"})[0].vm.$emit("update:open", false)
    api.previewFolderTidy.mockResolvedValueOnce({status: 200, data: {moves: [], foldersToCreate: []}})
    await wrapper.get('[data-testid="brevo-tidy"]').trigger("click")
    await settle()
    expect(document.body.querySelector('[data-testid="brevo-tidy-none"]')).not.toBeNull()
    expect(document.body.querySelector('[data-testid="brevo-tidy-last"]')?.textContent).toContain("No tidy has been applied yet.")

    api.previewFolderTidy.mockRejectedValueOnce(new Error("down"))
    await wrapper.get('[data-testid="brevo-tidy"]').trigger("click")
    await settle()
    expect(document.body.querySelector('[data-testid="brevo-tidy-refusal"]')?.textContent).toContain("Brevo could not be read")
  })
})
