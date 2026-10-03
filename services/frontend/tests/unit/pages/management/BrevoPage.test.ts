import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {DOMWrapper, type VueWrapper} from "@vue/test-utils"
import BrevoPage from "@/pages/management/BrevoPage.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findTargetOverview: vi.fn(),
  createMissingTargets: vi.fn(),
  archiveExternalTarget: vi.fn(),
  listCohortTargetFolders: vi.fn(),
  createExternalTarget: vi.fn(),
  createTargetFolder: vi.fn(),
  previewFolderTidy: vi.fn(),
  applyFolderTidy: vi.fn(),
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
      moves: [{externalId: "7", label: "Members 2025-2026", to: "Members"}, {externalId: "8", label: "Sitecie", from: "Old", to: "Committees"}],
      foldersToCreate: ["Committees"],
      lastApplied: {appliedAt: "2026-09-01T10:00:00Z", appliedByName: "Alice Board", moved: 2, failed: 0},
    }})
    api.applyFolderTidy.mockResolvedValue({status: 200, data: {moved: [made], failed: []}})
  })

  afterEach(() => unmountAll(wrappers, "BrevoPage"))

  it("shows the missing list first with a notice, every list by folder, and the archive folded", async () => {
    const wrapper = await mount()

    expect(api.findTargetOverview).toHaveBeenCalledWith({path: {system: "BREVO"}})
    expect(wrapper.get('[data-testid="brevo-missing"]').text()).toContain("1 list the site expects is missing")
    expect(wrapper.get('[data-testid="brevo-create-missing"]').text()).toBe("Create the list")
    expect(wrapper.get('[data-testid="brevo-state-missing-3"]').text()).toBe("Not created yet")
    expect(wrapper.get('[data-testid="brevo-state-list-7"]').text()).toBe("In step")
    expect(wrapper.get('[data-testid="brevo-row-list-7"] .brevo__name > *').attributes("to")).toBe("/management/platforms/brevo/lists/7")
    expect(wrapper.get('[data-testid="brevo-group-Follows nothing"]').text()).toContain("Old newsletter test")
    expect(wrapper.find('[data-testid="brevo-row-list-10"]').exists()).toBe(false)

    await wrapper.get('[data-testid="brevo-group-Archive-toggle"]').trigger("click")
    expect(wrapper.get('[data-testid="brevo-row-list-10"]').text()).toContain("LAN party 2024")
    expect(wrapper.find('[data-testid="brevo-archive-10"]').exists()).toBe(false)

    wrapper.findComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "nothing like it")
    await settle()
    expect(wrapper.get('[data-testid="brevo-empty"]').exists()).toBe(true)
  })

  it("creates one missing list or all of them, archives a list made by hand, and says a refusal", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="brevo-create-3"]').trigger("click")
    await settle()
    expect(api.createMissingTargets).toHaveBeenCalledWith({path: {system: "BREVO"}, body: {targetIds: [3]}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "The list is being created.")

    api.createMissingTargets.mockResolvedValueOnce({status: 200, data: {queued: 2}})
    await wrapper.get('[data-testid="brevo-create-missing"]').trigger("click")
    await settle()
    expect(api.createMissingTargets).toHaveBeenLastCalledWith({path: {system: "BREVO"}, body: {targetIds: []}})
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
    expect(mockStore.commit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", "The lists could not be created.")
  })

  it("makes a new list in a new folder, and says why Brevo refused one", async () => {
    api.findTargetOverview.mockResolvedValue({status: 200, data: overview([{targetId: 3, cohortId: 103, cohortLabel: "A", cohortType: "PERIOD_PAYERS",
      folder: "Contribution paid", memberCount: 1, creating: true}, {targetId: 4, cohortId: 104, cohortLabel: "B", cohortType: "PERIOD_PAYERS",
      folder: null, memberCount: 1, creating: false}])})
    const wrapper = await mount()
    expect(wrapper.get('[data-testid="brevo-state-missing-3"]').text()).toBe("Being created")
    expect(wrapper.get('[data-testid="brevo-create-missing"]').text()).toBe("Create 2 lists")

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
    expect(api.createTargetFolder).toHaveBeenCalledWith({path: {system: "BREVO"}, body: {name: "New"}})
    expect(api.createExternalTarget).toHaveBeenCalledWith({path: {system: "BREVO"}, body: {name: "Pub quiz", folder: "New"}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Pub quiz is made.")

    await wrapper.get('[data-testid="brevo-new-list"]').trigger("click")
    await settle()
    inputs()[0].vm.$emit("update:modelValue", "Pub quiz")
    wrapper.findComponent({name: "SearchPicker"}).vm.$emit("pick", "Members")
    api.createExternalTarget.mockResolvedValueOnce({status: 502, error: {code: "TargetSystemRefused", system: "Brevo", reason: "Name taken"}})
    await settle()
    await form().trigger("submit")
    await settle()
    expect(api.createExternalTarget).toHaveBeenLastCalledWith({path: {system: "BREVO"}, body: {name: "Pub quiz", folder: "Members"}})
    expect(document.body.querySelector('[data-testid="brevo-new-refusal"]')?.textContent).toContain("Name taken")

    api.createTargetFolder.mockResolvedValueOnce({status: 502, error: {code: "TargetSystemRefused", system: "Brevo", reason: "No"}})
    wrapper.findComponent({name: "SearchPicker"}).vm.$emit("pick", "__new__")
    await settle()
    inputs()[1].vm.$emit("update:modelValue", "Other")
    await settle()
    await form().trigger("submit")
    await settle()
    expect(api.createTargetFolder).toHaveBeenLastCalledWith({path: {system: "BREVO"}, body: {name: "Other"}})
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
    expect(document.body.textContent).toContain("Makes Committees first.")
    const boxes = () => wrapper.findAllComponents({name: "CheckBox"})
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
