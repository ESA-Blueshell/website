import {beforeEach, describe, expect, it, vi} from "vitest"
import CohortDetail from "@/pages/management/CohortDetail.vue"
import {
  TargetKind,
  CohortCategory,
  CohortType,
  DriftResolutionAction,
  JobTrigger,
  TargetSystem,
  fetchCohort,
  linkDriftPeople,
  proposeDriftLinks,
  pushDriftPeople,
  removeDriftPeople,
  setTargetEnforced,
  evaluateMember,
  triggerReconcile,
  type CohortMember,
  type Cohort,
} from "@/domains/cohorts/adapters/cohorts"
import router from "@/plugins/router"
import type {StoredLogin} from "@/plugins/store"
import {boardLogin, mountPage} from "../../helpers/mountPage"
import {settle} from "../../helpers/testUtils"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  fetchCohort: vi.fn(),
  pushDriftPeople: vi.fn(),
  removeDriftPeople: vi.fn(),
  proposeDriftLinks: vi.fn(),
  linkDriftPeople: vi.fn(),
  setTargetEnforced: vi.fn(),
  triggerReconcile: vi.fn(),
  evaluateMember: vi.fn(),
}))

const adminLogin: StoredLogin = {
  userId: 1,
  username: "admin",
  roles: ["ADMIN"] as StoredLogin["roles"],
  twoFactor: {backupCodesLeft: 0, mayTurnOff: false, offered: false, on: true, required: false},
}

const member = (over: Partial<CohortMember>): CohortMember => ({
  targetMemberId: 1,
  userId: null,
  userFullName: null,
  userEmail: null,
  isUserDeleted: false,
  joinedAt: "2026-09-01T00:00:00Z",
  externalLabel: null,
  externalUserId: null,
  system: TargetSystem.BREVO,
  sync: "IN_SYNC",
  ...over,
})

const cohort = (): Cohort => ({
  id: 7,
  label: "Paid 2026",
  description: null,
  category: CohortCategory.PERIODS,
  type: CohortType.PERIOD_PAYERS,
  definitionKey: "PERIOD_PAYERS:1",
  orphaned: false,
  mappings: [{
    targetId: 40,
    system: TargetSystem.BREVO,
    kind: TargetKind.LIST,
    label: "Paid 2026",
    externalId: "7",
    lastReconciledAt: null,
    path: ["Brevo"],
    folderKnown: true,
    runs: [],
    enforced: false,
  }],
  members: [
    member({targetMemberId: 1, userId: 5, userFullName: "Ada Lovelace", sync: "ONLY_HERE"}),
    member({targetMemberId: 2, externalUserId: "ext-2", externalLabel: "grace@example.com", sync: "ONLY_EXTERNAL"}),
  ],
  resolutions: [
    {system: TargetSystem.BREVO, action: DriftResolutionAction.REMOVE, personName: "old@example.com", resolvedByName: null, resolvedAt: "2026-09-29T20:00:00Z"},
  ],
})

const open = async () => {
  vi.mocked(fetchCohort).mockResolvedValue(cohort())
  return mountPage(CohortDetail, {path: "/management/cohort/7", login: adminLogin})
}

const press = async (wrapper: Awaited<ReturnType<typeof open>>, testid: string) => {
  await wrapper.get(`[data-testid="${testid}"]`).trigger("click")
  await settle()
}

// Dialogs and menus teleport out of the page's element, so they are found as components.
const part = (wrapper: Awaited<ReturnType<typeof open>>, name: string, testid: string) => {
  const found = wrapper.findAllComponents({name}).find((c) => c.attributes("data-testid") === testid)
  if (!found) throw new Error(`no ${name} ${testid}`)
  return found
}

const click = async (wrapper: Awaited<ReturnType<typeof open>>, name: string, testid: string) => {
  await part(wrapper, name, testid).trigger("click")
  await settle()
}

describe("CohortDetail drift", () => {
  beforeEach(() => vi.resetAllMocks())

  it("lists the recent resolutions, naming the site where nobody made one", async () => {
    const wrapper = await open()

    const box = wrapper.get("[data-testid=cohort-detail-resolutions]")
    await box.get("[data-testid=info-box-toggle]").trigger("click")
    await settle()

    expect(box.get("[data-testid=cohort-detail-resolution-0]").text()).toContain("old@example.com removed from Brevo")
    expect(box.get("[data-testid=cohort-detail-resolution-0]").text()).toContain("The site")
  })

  it("pushes a selection after showing who it concerns", async () => {
    vi.mocked(pushDriftPeople).mockResolvedValue({ok: true, saved: 1})
    const wrapper = await open()

    await wrapper.get("[data-testid=cohort-detail-member-select-1] input").setValue(true)
    await wrapper.get("[data-testid=cohort-detail-member-select-2] input").setValue(true)
    await settle()
    expect(wrapper.get("[data-testid=cohort-drift-bulk-push]").text()).toBe("Push 1 to the target")
    await press(wrapper, "cohort-drift-bulk-push")
    expect(part(wrapper, "VCard", "cohort-drift-plan").text()).toContain("Ada Lovelace")
    await click(wrapper, "VBtn", "cohort-drift-plan-confirm")

    expect(pushDriftPeople).toHaveBeenCalledWith(7, 40, [5])
    expect(wrapper.get("[data-testid=cohort-drift-message]").text()).toBe("1 pushed.")
  })

  it("shows the account a link would give each contact", async () => {
    vi.mocked(proposeDriftLinks).mockResolvedValue({ok: true, saved: [{externalUserId: "ext-2", label: "grace@example.com", userId: null, userFullName: null}]})
    const wrapper = await open()

    await wrapper.get("[data-testid=cohort-detail-member-select-2] input").setValue(true)
    await settle()
    await press(wrapper, "cohort-drift-bulk-link")

    expect(part(wrapper, "VCard", "cohort-drift-plan").text()).toContain("No account with this address")
    await press(wrapper, "cohort-drift-bulk-clear")
    expect(wrapper.find("[data-testid=cohort-drift-bulk]").exists()).toBe(false)
  })

  it("removes one row from its menu, and reports a refusal", async () => {
    vi.mocked(removeDriftPeople).mockResolvedValue({ok: false, reason: "The target has not been created yet."})
    const wrapper = await open()

    await press(wrapper, "cohort-detail-member-menu-2")
    await click(wrapper, "VListItem", "cohort-detail-member-remove-2")
    await click(wrapper, "VBtn", "cohort-drift-plan-confirm")

    expect(removeDriftPeople).toHaveBeenCalledWith(7, 40, ["ext-2"])
    expect(wrapper.get("[data-testid=cohort-drift-error]").text()).toBe("The target has not been created yet.")
  })

  it("links a contact by hand and says which account already holds it", async () => {
    vi.mocked(linkDriftPeople).mockResolvedValue({ok: true, saved: {linked: 0, conflicts: [{externalUserId: "ext-2", existingUserId: 9}]}})
    const wrapper = await open()

    await press(wrapper, "cohort-detail-member-menu-2")
    await click(wrapper, "VListItem", "cohort-detail-member-link-2")
    const picker = wrapper.findAllComponents({name: "UserPicker"})[0]!
    picker.vm.$emit("update:modelValue", 6)
    await settle()
    await click(wrapper, "VBtn", "cohort-detail-link-confirm")

    expect(linkDriftPeople).toHaveBeenCalledWith(7, 40, [{externalUserId: "ext-2", userId: 6}])
    expect(wrapper.findAllComponents({name: "VAlert"}).some((alert) => alert.text().includes("User #9"))).toBe(true)
  })

  it("pushes one row from its menu, and a cancelled plan sends nothing", async () => {
    const wrapper = await open()

    await press(wrapper, "cohort-detail-member-menu-1")
    await click(wrapper, "VListItem", "cohort-detail-member-push-1")
    const cancel = part(wrapper, "VCard", "cohort-drift-plan").findAllComponents({name: "VBtn"}).find((b) => b.text() === "Cancel")!
    await cancel.trigger("click")
    await settle()
    expect(wrapper.findAllComponents({name: "VCard"}).some((c) => c.attributes("data-testid") === "cohort-drift-plan")).toBe(false)
    expect(pushDriftPeople).not.toHaveBeenCalled()
  })

  it("a plan dismissed by its backdrop sends nothing", async () => {
    const wrapper = await open()

    await press(wrapper, "cohort-detail-member-menu-1")
    await click(wrapper, "VListItem", "cohort-detail-member-push-1")
    const dialog = wrapper.findAllComponents({name: "VDialog"}).find((d) => d.props("modelValue") === true)!
    dialog.vm.$emit("update:modelValue", false)
    await settle()
    expect(pushDriftPeople).not.toHaveBeenCalled()
  })

  it("says an adopt was queued", async () => {
    const wrapper = await open()
    await wrapper.get("[data-testid=cohort-detail-targets] [data-testid=info-box-toggle]").trigger("click")
    await settle()
    await press(wrapper, "cohort-detail-target-menu-brevo")
    await click(wrapper, "VListItem", "cohort-detail-inbound-reconcile-brevo")

    wrapper.findComponent({name: "InboundReconcileModal"}).vm.$emit("applied")
    await settle()

    expect(wrapper.get("[data-testid=cohort-detail-success]").text()).toBe("Adopt queued.")
  })

  const enforceFromMenu = async () => {
    const wrapper = await open()
    await wrapper.get("[data-testid=cohort-detail-targets] [data-testid=info-box-toggle]").trigger("click")
    await settle()
    await press(wrapper, "cohort-detail-target-menu-brevo")
    await click(wrapper, "VListItem", "cohort-detail-enforce-brevo")
    return wrapper
  }

  it("enforces a target from its menu", async () => {
    vi.mocked(setTargetEnforced).mockResolvedValue({ok: true})

    const wrapper = await enforceFromMenu()

    expect(setTargetEnforced).toHaveBeenCalledWith(7, 40, true)
    expect(wrapper.get("[data-testid=cohort-detail-success]").text()).toBe("Enforced: each reconcile removes the extra people.")
  })

  it("says why a switch was refused", async () => {
    vi.mocked(setTargetEnforced).mockResolvedValue({ok: false, reason: "Only an admin may."})

    const wrapper = await enforceFromMenu()

    expect(wrapper.get("[data-testid=cohort-detail-error]").text()).toBe("Only an admin may.")
  })

  it("marks an enforced target and offers to stop enforcing it", async () => {
    vi.mocked(setTargetEnforced).mockResolvedValue({ok: true})
    const enforced = cohort()
    enforced.mappings[0]!.enforced = true
    vi.mocked(fetchCohort).mockResolvedValue(enforced)
    const wrapper = await mountPage(CohortDetail, {path: "/management/cohort/7", login: adminLogin})
    await wrapper.get("[data-testid=cohort-detail-targets] [data-testid=info-box-toggle]").trigger("click")
    await settle()

    expect(wrapper.find("[data-testid=cohort-detail-target-enforced-brevo]").exists()).toBe(true)
    await press(wrapper, "cohort-detail-target-menu-brevo")
    await click(wrapper, "VListItem", "cohort-detail-enforce-brevo")
    expect(setTargetEnforced).toHaveBeenCalledWith(7, 40, false)
    expect(wrapper.get("[data-testid=cohort-detail-success]").text()).toBe("No longer enforced.")
  })

  const withTargets = async () => {
    const wrapper = await open()
    await wrapper.get("[data-testid=cohort-detail-targets] [data-testid=info-box-toggle]").trigger("click")
    await settle()
    return wrapper
  }

  it("reconciles a target from its menu", async () => {
    vi.mocked(triggerReconcile).mockResolvedValue({ok: true})
    const wrapper = await withTargets()

    await press(wrapper, "cohort-detail-target-menu-brevo")
    await click(wrapper, "VListItem", "cohort-detail-reconcile-brevo")

    expect(triggerReconcile).toHaveBeenCalledWith(7, 40)
    expect(wrapper.get("[data-testid=cohort-detail-success]").text()).toBe("Reconcile enqueued.")
  })

  it("opens the picker to switch a target", async () => {
    const wrapper = await withTargets()

    await press(wrapper, "cohort-detail-target-menu-brevo")
    await click(wrapper, "VListItem", "cohort-detail-switch-target-brevo")

    const picker = wrapper.findComponent({name: "TargetPickerModal"})
    expect(picker.props("mode")).toBe("switch")
    expect(picker.props("targetId")).toBe(40)
  })

  it("opens the picker to add a target", async () => {
    const wrapper = await withTargets()

    await press(wrapper, "cohort-detail-targets-menu")
    await click(wrapper, "VListItem", "cohort-detail-add-target")

    const picker = wrapper.findComponent({name: "TargetPickerModal"})
    expect(picker.props("mode")).toBe("add")
    expect(picker.props("targetId")).toBeUndefined()
  })

  it("goes back to the cohort's category", async () => {
    const wrapper = await open()
    const push = vi.spyOn(router, "push").mockResolvedValue(undefined)

    await press(wrapper, "cohort-detail-back")

    expect(push).toHaveBeenCalledWith({name: "cohortCategory", params: {category: "periods"}})
    push.mockRestore()
  })

  it("shows nothing for a cohort that could not be read", async () => {
    vi.mocked(fetchCohort).mockRejectedValue(new Error("down"))
    const wrapper = await mountPage(CohortDetail, {path: "/management/cohort/7", login: adminLogin})

    expect(wrapper.find("[data-testid=cohort-detail-identity]").exists()).toBe(false)
  })

  it("shows each target's drift and the runs before it", async () => {
    const run = (startedAt: string, missing: number) => ({startedAt, trigger: JobTrigger.SCHEDULED_RUN, inStep: 40, missing, extra: 2})
    const withRuns = cohort()
    withRuns.mappings[0]!.runs = [run("2026-09-29T03:00:00Z", 1), run("2026-09-28T03:00:00Z", 3)]
    vi.mocked(fetchCohort).mockResolvedValue(withRuns)
    const wrapper = await mountPage(CohortDetail, {path: "/management/cohort/7", login: adminLogin})
    await wrapper.get("[data-testid=cohort-detail-targets] [data-testid=info-box-toggle]").trigger("click")
    await settle()

    expect(wrapper.get("[data-testid=cohort-detail-target-drift-brevo]").text()).toBe("40 in step · 1 missing · 2 extra")
    expect(wrapper.get("[data-testid=cohort-detail-target-drift-history-brevo]").text()).toContain("nightly: 3 missing, 2 extra")
  })

  it("says why a reconcile was refused", async () => {
    vi.mocked(triggerReconcile).mockResolvedValue({ok: false, reason: "The target has not been created yet."})
    const wrapper = await withTargets()

    await press(wrapper, "cohort-detail-target-menu-brevo")
    await click(wrapper, "VListItem", "cohort-detail-reconcile-brevo")

    expect(wrapper.get("[data-testid=cohort-detail-error]").text()).toBe("The target has not been created yet.")
  })

  it("has a member looked at again, and says when that was refused", async () => {
    vi.mocked(evaluateMember).mockResolvedValueOnce({ok: true}).mockResolvedValueOnce({ok: false, reason: "No."})
    const wrapper = await open()

    await press(wrapper, "cohort-detail-member-menu-1")
    await click(wrapper, "VListItem", "cohort-detail-member-reeval-5")
    expect(evaluateMember).toHaveBeenCalledWith(5)
    expect(wrapper.get("[data-testid=cohort-detail-success]").text()).toBe("Queued a fresh look at their cohorts.")

    const again = await open()
    await press(again, "cohort-detail-member-menu-1")
    await click(again, "VListItem", "cohort-detail-member-reeval-5")
    expect(again.get("[data-testid=cohort-detail-error]").text()).toBe("No.")
  })

  it("leaves switching and enforcing out of the board's target menu", async () => {
    vi.mocked(fetchCohort).mockResolvedValue(cohort())
    const wrapper = await mountPage(CohortDetail, {path: "/management/cohort/7", login: boardLogin})
    await wrapper.get("[data-testid=cohort-detail-targets] [data-testid=info-box-toggle]").trigger("click")
    await settle()

    await press(wrapper, "cohort-detail-target-menu-brevo")

    const items = wrapper.findAllComponents({name: "VListItem"}).map((c) => c.attributes("data-testid"))
    expect(items).toContain("cohort-detail-reconcile-brevo")
    expect(items).not.toContain("cohort-detail-switch-target-brevo")
    expect(items).not.toContain("cohort-detail-enforce-brevo")
  })
})
