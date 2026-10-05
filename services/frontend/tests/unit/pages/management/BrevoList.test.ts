import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {DOMWrapper, type VueWrapper} from "@vue/test-utils"
import BrevoList from "@/pages/management/BrevoList.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findListedTarget: vi.fn(),
  findCohortById: vi.fn(),
  findTargetOverview: vi.fn(),
  listCohortTargetFolders: vi.fn(),
  reconcileTarget: vi.fn(),
  enforceTarget: vi.fn(),
  archiveExternalTarget: vi.fn(),
  renameExternalTarget: vi.fn(),
  moveCohortTarget: vi.fn(),
  deleteExternalTarget: vi.fn(),
  linkExistingTarget: vi.fn(),
  pushDrift: vi.fn(),
  removeDrift: vi.fn(),
  proposeLinks: vi.fn(),
  linkDrift: vi.fn(),
  previewInboundReconcile: vi.fn(),
  applyInboundReconcile: vi.fn(),
}))
const {mockStore, route, push} = vi.hoisted(() => ({
  mockStore: {commit: vi.fn(), getters: {isAdmin: true} as Record<string, unknown>},
  route: {params: {externalId: "7"}},
  push: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => route,
  useRouter: () => ({push}),
}))
vi.mock("@/plugins/store", () => ({default: mockStore}))
vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const listed = (fields: Record<string, unknown> = {}) => ({
  externalId: "7", label: "Contribution paid 2025-2026", folderLabel: "Contribution paid", memberCount: 188, targetId: 40, cohortId: 3,
  cohortLabel: "Paid 2025-2026", cohortType: "PERIOD_PAYERS", missing: 1, extra: 2, lastReconciledAt: "2026-10-01T03:00:00Z", enforced: false, ...fields,
})
const ledger = (targetMemberId: number, state: string, fields: Record<string, unknown> = {}) => ({
  targetMemberId, state, isUserDeleted: false, joinedAt: "2025-09-22T10:00:00Z", system: "BREVO", ...fields,
})
const cohort = (fields: Record<string, unknown> = {}) => ({
  id: 3, label: "Paid 2025-2026", category: "PERIODS", type: "PERIOD_PAYERS", orphaned: false,
  mappings: [{targetId: 40, system: "BREVO", kind: "LIST", externalId: "7", label: "Contribution paid 2025-2026", path: [], folderKnown: true,
    enforced: false, runs: [{startedAt: "2026-10-01T03:00:00Z", inSync: 186, oursOnly: 1, theirsOnly: 2}, {startedAt: "2026-09-30T03:00:00Z", inSync: 189, oursOnly: 0, theirsOnly: 0}]}],
  members: [
    ledger(1, "DESIRED", {userId: 11, userFullName: "Sanne Jansen", userEmail: "sanne@example.com"}),
    ledger(2, "STRANGER", {userId: 12, userFullName: "Eva de Boer", userEmail: "eva@example.com", externalUserId: "e2"}),
    ledger(3, "STRANGER", {externalUserId: "e3", externalLabel: "jan@example.com"}),
    ledger(4, "SYNCED", {userId: 14, userFullName: "In Step"}),
  ],
  resolutions: [{system: "BREVO", action: "PUSH", userId: 31, personName: "Kim Vos", resolvedById: 4, resolvedByName: "Treasurer", resolvedAt: "2026-09-23T10:15:00Z"},
    {system: "GOOGLE_WORKSPACE", action: "REMOVE", resolvedAt: "2026-09-23T10:15:00Z"}],
  ...fields,
})
const target = {system: "BREVO", externalId: "7", kind: "LIST", label: "Paid", path: []}
const inDialog = (testid: string) => new DOMWrapper(document.body.querySelector(`[data-testid="${testid}"]`)!)

describe("one Brevo list", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(BrevoList)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    route.params.externalId = "7"
    mockStore.getters.isAdmin = true
    api.findListedTarget.mockResolvedValue({status: 200, data: listed()})
    api.findCohortById.mockResolvedValue({status: 200, data: cohort()})
    api.listCohortTargetFolders.mockResolvedValue({status: 200, data: ["Contribution paid", "Members"]})
    api.reconcileTarget.mockResolvedValue({status: 200, data: undefined})
    api.enforceTarget.mockResolvedValue({status: 200, data: undefined})
    api.archiveExternalTarget.mockResolvedValue({status: 200, data: {...target, folderLabel: "Archive"}})
    api.renameExternalTarget.mockResolvedValue({status: 200, data: target})
    api.moveCohortTarget.mockResolvedValue({status: 200, data: target})
    api.pushDrift.mockResolvedValue({status: 200, data: {resolved: 1}})
    api.removeDrift.mockResolvedValue({status: 200, data: {resolved: 2}})
    api.proposeLinks.mockResolvedValue({status: 200, data: [{externalUserId: "e3", userId: 15, userFullName: "Jan Pieters"}]})
    api.linkDrift.mockResolvedValue({status: 200, data: {linked: 1, conflicts: []}})
    api.previewInboundReconcile.mockResolvedValue({status: 200, data: {cohortLabel: "Paid", definitionKey: "k", previewToken: "tok", remoteCount: 1,
      skipped: [], writerSupported: true, matched: [{externalUserId: "e2", writable: true, alreadyMember: false}]}})
    api.applyInboundReconcile.mockResolvedValue({status: 200, data: {acceptedCount: 1, skippedCount: 0}})
  })

  afterEach(() => unmountAll(wrappers, "BrevoListPage"))

  it("shows what the list follows, its people and runs, each drifting person with why, and the resolutions", async () => {
    const wrapper = await mount()

    expect(api.findListedTarget).toHaveBeenCalledWith({path: {system: "BREVO", externalId: "7"}})
    expect(wrapper.text()).toContain("Brevo list · Contribution paid folder")
    expect(wrapper.text()).toContain("Mail sent to this list reaches Paid 2025-2026")
    expect(wrapper.text()).toContain("1 in step")
    expect(wrapper.text()).toContain("1 missing · 2 extra")
    expect(wrapper.findAll('[data-testid="brevo-list-runs"] span')).toHaveLength(2)
    expect(wrapper.get('[data-testid="brevo-list-row-1"]').text()).toContain("not on the list")
    expect(wrapper.get('[data-testid="brevo-list-row-3"]').text()).toContain("Unknown contact")
    expect(wrapper.get('[data-testid="brevo-list-adopt-2"]').text()).toBe("Record as paid")
    expect(wrapper.find('[data-testid="brevo-list-row-4"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="brevo-list-resolved"]').text()).toContain("Pushed to the list")
    expect(wrapper.findAll('[data-testid="brevo-list-resolved"] tbody tr')).toHaveLength(1)
    // Both names in a resolution lead to that person's page.
    expect(wrapper.findAll('[data-testid="brevo-list-resolved"] tbody a').map((one) => [one.text(), one.attributes("href") ?? one.attributes("to")]))
      .toEqual([["Treasurer", "/management/users/4"], ["Kim Vos", "/management/users/31"]])
    expect(wrapper.find('[data-testid="brevo-list-link"]').exists()).toBe(false)

    wrapper.findComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "nobody")
    await settle()
    expect(wrapper.get('[data-testid="brevo-list-in-step"]').text()).toBe("Nobody who differs matches.")
  })

  it("resolves one person and a selection, and reconciles by hand", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="brevo-list-push-1"]').trigger("click")
    await settle()
    await inDialog("brevo-list-plan-confirm").trigger("click")
    await settle()
    expect(api.pushDrift).toHaveBeenCalledWith({path: {id: 3, targetId: 40}, body: {userIds: [11]}})

    await wrapper.get('[data-testid="brevo-list-link-3"]').trigger("click")
    await settle()
    expect(document.body.textContent).toContain("Jan Pieters")
    await inDialog("brevo-list-plan-confirm").trigger("click")
    await settle()
    expect(api.linkDrift).toHaveBeenCalledWith({path: {id: 3, targetId: 40}, body: {links: [{externalUserId: "e3", userId: 15}]}})

    // The head's tick takes everybody who can be selected.
    await wrapper.get('[data-testid="brevo-list-drift-select-shown"]').trigger("change")
    await settle()
    expect(wrapper.get('[data-testid="brevo-list-bulk-remove"]').text()).toBe("Remove: 2")
    expect(wrapper.get('[data-testid="brevo-list-bulk-adopt"]').text()).toBe("Record as paid: 1")
    await wrapper.get('[data-testid="brevo-list-bulk-adopt"]').trigger("click")
    await settle()
    await inDialog("brevo-list-plan-confirm").trigger("click")
    await settle()
    expect(api.applyInboundReconcile).toHaveBeenCalled()

    await wrapper.get('[data-testid="brevo-list-row-2"]').findAll("button").at(-1)!.trigger("click")
    await settle()
    wrapper.findComponent({name: "ModalDialog"}).vm.$emit("update:open", false)
    await settle()
    expect(api.removeDrift).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="brevo-list-reconcile"]').trigger("click")
    await settle()
    expect(api.reconcileTarget).toHaveBeenCalledWith({path: {id: 3, targetId: 40}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "A reconcile is queued.")
  })

  it("renames and moves the list, enforces and archives it", async () => {
    const wrapper = await mount()
    expect(wrapper.get('[data-testid="brevo-list-save"]').attributes("disabled")).toBeDefined()

    wrapper.findComponent({name: "TextInput"}).vm.$emit("update:modelValue", "Paid 25-26")
    wrapper.findAllComponents({name: "SearchPicker"}).at(-1)!.vm.$emit("pick", "Members")
    await settle()
    await wrapper.get("form.list__form").trigger("submit")
    await settle()
    expect(api.renameExternalTarget).toHaveBeenCalledWith({path: {system: "BREVO", externalId: "7"}, body: {name: "Paid 25-26"}})
    expect(api.moveCohortTarget).toHaveBeenCalledWith({path: {system: "BREVO", externalId: "7"}, body: {folder: "Members"}, throwOnError: true})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "The list is saved.")

    await wrapper.get('[data-testid="brevo-list-enforce"]').trigger("click")
    await settle()
    expect(api.enforceTarget).toHaveBeenCalledWith({path: {id: 3, targetId: 40}, body: {enforced: true}})

    await wrapper.get('[data-testid="brevo-list-archive"]').trigger("click")
    await settle()
    expect(api.archiveExternalTarget).toHaveBeenCalledWith({path: {system: "BREVO", externalId: "7"}})
    expect(wrapper.get('[data-testid="brevo-list-delete"]').attributes("disabled")).toBeDefined()
  })

  it("says why a write was refused", async () => {
    api.reconcileTarget.mockResolvedValue({status: 409, error: {code: "TargetNotCreated", cohortId: 3}})
    api.enforceTarget.mockResolvedValue({status: 403, error: {}})
    api.archiveExternalTarget.mockResolvedValue({status: 502, error: {code: "TargetSystemRefused", system: "Brevo", reason: "down"}})
    api.renameExternalTarget.mockResolvedValue({status: 502, error: {code: "TargetSystemRefused", system: "Brevo", reason: "taken"}})
    const wrapper = await mount()

    await wrapper.get('[data-testid="brevo-list-reconcile"]').trigger("click")
    await wrapper.get('[data-testid="brevo-list-enforce"]').trigger("click")
    await wrapper.get('[data-testid="brevo-list-archive"]').trigger("click")
    await settle()
    wrapper.findComponent({name: "TextInput"}).vm.$emit("update:modelValue", "Other")
    await settle()
    await wrapper.get("form.list__form").trigger("submit")
    await settle()
    expect(api.moveCohortTarget).not.toHaveBeenCalled()

    api.renameExternalTarget.mockResolvedValue({status: 200, data: target})
    api.moveCohortTarget.mockRejectedValue(new Error("down"))
    wrapper.findAllComponents({name: "SearchPicker"}).at(-1)!.vm.$emit("pick", "Members")
    await settle()
    await wrapper.get("form.list__form").trigger("submit")
    await settle()
    expect(mockStore.commit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", "The list could not be moved.")
  })

  it("draws each person who differs as a row on a phone, ticked from the row, one out of reach saying so", async () => {
    api.findCohortById.mockResolvedValue({status: 200, data: cohort({members: [
      ledger(1, "DESIRED", {userId: 11, userFullName: "Sanne Jansen", userEmail: "sanne@example.com"}),
      ledger(5, "DESIRED", {userId: 15, userFullName: "No Brevo", unreachable: true}),
    ]})})
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = await mount()
    vi.unstubAllGlobals()

    expect(wrapper.findAllComponents({name: "ManagementRow"})).toHaveLength(2)
    expect(wrapper.find('[data-testid="brevo-list-select-5"]').exists()).toBe(false)
    await wrapper.get('[data-testid="brevo-list-select-1"]').trigger("change")
    await settle()
    expect(wrapper.get('[data-testid="brevo-list-selection"]').text()).toContain("1 selected")
  })

  it("links a list that follows nothing to a cohort whose list is missing, and an admin deletes it by its name", async () => {
    mockStore.getters.isAdmin = true
    api.findListedTarget.mockResolvedValue({status: 200, data: {externalId: "9", label: "Old test", memberCount: 4, enforced: false}})
    api.findTargetOverview.mockResolvedValue({status: 200, data: {lists: [], missing: [{targetId: 5, cohortId: 6, cohortLabel: "Paid 2026-2027",
      cohortType: "PERIOD_PAYERS", memberCount: 1, creating: false}]}})
    api.linkExistingTarget.mockResolvedValue({status: 200, data: {targetId: 5, system: "BREVO", kind: "LIST", label: "Paid 2026-2027", path: [], folderKnown: true, runs: [], enforced: false}})
    api.deleteExternalTarget.mockResolvedValueOnce({status: 409, error: {code: "TargetNameMismatch"}}).mockResolvedValue({status: 204, data: undefined})
    const wrapper = await mount()

    expect(wrapper.text()).toContain("This list is not linked to a cohort")
    expect(wrapper.text()).toContain("Brevo list · No folder")
    expect(wrapper.find('[data-testid="brevo-list-reconcile"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="brevo-list-enforce"]').exists()).toBe(false)
    wrapper.findAllComponents({name: "SearchPicker"})[0].vm.$emit("pick", "6")
    await settle()
    await wrapper.get('[data-testid="brevo-list-link-confirm"]').trigger("click")
    await settle()
    expect(api.linkExistingTarget).toHaveBeenCalledWith({path: {id: 6}, body: {system: "BREVO", externalId: "9"}, throwOnError: true})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Old test now follows Paid 2026-2027.")

    api.linkExistingTarget.mockRejectedValueOnce({response: {status: 409}})
    await wrapper.get('[data-testid="brevo-list-link-confirm"]').trigger("click")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "That cohort already has a list.")
    api.linkExistingTarget.mockRejectedValueOnce(new Error("down"))
    await wrapper.get('[data-testid="brevo-list-link-confirm"]').trigger("click")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "The list could not be linked.")

    await wrapper.get('[data-testid="brevo-list-delete"]').trigger("click")
    await settle()
    const dialogs = () => wrapper.findAllComponents({name: "ModalDialog"})
    dialogs().at(-1)!.vm.$emit("update:open", false)
    await settle()
    expect(dialogs().at(-1)!.props("open")).toBe(false)
    await wrapper.get('[data-testid="brevo-list-delete"]').trigger("click")
    await settle()
    const typed = wrapper.findAllComponents({name: "TextInput"}).at(-1)!
    typed.vm.$emit("update:modelValue", "Old test")
    await settle()
    await new DOMWrapper(document.body.querySelector('[data-testid="brevo-list-delete-form"]')!).trigger("submit")
    await settle()
    expect(document.body.querySelector('[data-testid="brevo-list-delete-failure"]')).not.toBeNull()
    await new DOMWrapper(document.body.querySelector('[data-testid="brevo-list-delete-form"]')!).trigger("submit")
    await settle()
    expect(api.deleteExternalTarget).toHaveBeenLastCalledWith({path: {system: "BREVO", externalId: "9"}, body: {name: "Old test"}})
    expect(push).toHaveBeenCalledWith("/management/platforms/brevo")
  })

  it("says Brevo has no such list", async () => {
    api.findListedTarget.mockResolvedValue({status: 404, error: {code: "TargetNotFound"}})
    api.listCohortTargetFolders.mockRejectedValue(new Error("down"))
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="brevo-list-missing"]').exists()).toBe(true)
    expect(api.findCohortById).not.toHaveBeenCalled()
  })
})
