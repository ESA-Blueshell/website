import {beforeEach, describe, expect, it, vi} from "vitest"
import CohortSubjectDetail from "@/pages/management/CohortSubjectDetail.vue"
import {
  CohortKind,
  CohortSubjectCategory,
  CohortSubjectType,
  DriftResolutionAction,
  JobTrigger,
  TargetSystem,
  fetchCohortSubject,
  linkDriftPeople,
  proposeDriftLinks,
  pushDriftPeople,
  removeDriftPeople,
  type CohortMember,
  type CohortSubject,
} from "@/domains/cohorts/adapters/cohorts"
import type {StoredLogin} from "@/plugins/store"
import {mountPage} from "../../helpers/mountPage"
import {settle} from "../../helpers/testUtils"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  fetchCohortSubject: vi.fn(),
  pushDriftPeople: vi.fn(),
  removeDriftPeople: vi.fn(),
  proposeDriftLinks: vi.fn(),
  linkDriftPeople: vi.fn(),
}))

const adminLogin: StoredLogin = {
  userId: 1,
  username: "admin",
  roles: ["ADMIN"] as StoredLogin["roles"],
  twoFactor: {backupCodesLeft: 0, mayTurnOff: false, offered: false, on: true, required: false},
}

const member = (over: Partial<CohortMember>): CohortMember => ({
  cohortMemberId: 1,
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

const subject = (): CohortSubject => ({
  id: 7,
  label: "Paid 2026",
  description: null,
  category: CohortSubjectCategory.PERIODS,
  type: CohortSubjectType.PERIOD_PAYERS,
  definitionKey: "PERIOD_PAYERS:1",
  orphaned: false,
  mappings: [{
    cohortId: 40,
    system: TargetSystem.BREVO,
    kind: CohortKind.LIST,
    label: "Paid 2026",
    externalId: "7",
    lastReconciledAt: null,
    path: ["Brevo"],
    folderKnown: true,
    runs: [],
  }],
  members: [
    member({cohortMemberId: 1, userId: 5, userFullName: "Ada Lovelace", sync: "ONLY_HERE"}),
    member({cohortMemberId: 2, externalUserId: "ext-2", externalLabel: "grace@example.com", sync: "ONLY_EXTERNAL"}),
  ],
  resolutions: [
    {system: TargetSystem.BREVO, action: DriftResolutionAction.REMOVE, personName: "old@example.com", resolvedByName: null, resolvedAt: "2026-09-29T20:00:00Z"},
  ],
})

const open = async () => {
  vi.mocked(fetchCohortSubject).mockResolvedValue(subject())
  return mountPage(CohortSubjectDetail, {path: "/management/cohorts/subjects/7", login: adminLogin})
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

describe("CohortSubjectDetail drift", () => {
  beforeEach(() => vi.resetAllMocks())

  it("lists the recent resolutions, naming the site where nobody made one", async () => {
    const wrapper = await open()

    const box = wrapper.get("[data-testid=cohort-subject-resolutions]")
    await box.get("[data-testid=info-box-toggle]").trigger("click")
    await settle()

    expect(box.get("[data-testid=cohort-subject-resolution-0]").text()).toContain("old@example.com removed from Brevo")
    expect(box.get("[data-testid=cohort-subject-resolution-0]").text()).toContain("The site")
  })

  it("pushes a selection after showing who it concerns", async () => {
    vi.mocked(pushDriftPeople).mockResolvedValue({ok: true, saved: 1})
    const wrapper = await open()

    await wrapper.get("[data-testid=cohort-subject-member-select-1] input").setValue(true)
    await wrapper.get("[data-testid=cohort-subject-member-select-2] input").setValue(true)
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

    await wrapper.get("[data-testid=cohort-subject-member-select-2] input").setValue(true)
    await settle()
    await press(wrapper, "cohort-drift-bulk-link")

    expect(part(wrapper, "VCard", "cohort-drift-plan").text()).toContain("No account with this address")
    await press(wrapper, "cohort-drift-bulk-clear")
    expect(wrapper.find("[data-testid=cohort-drift-bulk]").exists()).toBe(false)
  })

  it("removes one row from its menu, and reports a refusal", async () => {
    vi.mocked(removeDriftPeople).mockResolvedValue({ok: false, reason: "The target has not been created yet."})
    const wrapper = await open()

    await press(wrapper, "cohort-subject-member-menu-2")
    await click(wrapper, "VListItem", "cohort-subject-member-remove-2")
    await click(wrapper, "VBtn", "cohort-drift-plan-confirm")

    expect(removeDriftPeople).toHaveBeenCalledWith(7, 40, ["ext-2"])
    expect(wrapper.get("[data-testid=cohort-drift-error]").text()).toBe("The target has not been created yet.")
  })

  it("links a contact by hand and says which account already holds it", async () => {
    vi.mocked(linkDriftPeople).mockResolvedValue({ok: true, saved: {linked: 0, conflicts: [{externalUserId: "ext-2", existingUserId: 9}]}})
    const wrapper = await open()

    await press(wrapper, "cohort-subject-member-menu-2")
    await click(wrapper, "VListItem", "cohort-subject-member-link-2")
    const picker = wrapper.findAllComponents({name: "UserPicker"})[0]!
    picker.vm.$emit("update:modelValue", 6)
    await settle()
    await click(wrapper, "VBtn", "cohort-subject-link-confirm")

    expect(linkDriftPeople).toHaveBeenCalledWith(7, 40, [{externalUserId: "ext-2", userId: 6}])
    expect(wrapper.findAllComponents({name: "VAlert"}).some((alert) => alert.text().includes("User #9"))).toBe(true)
  })

  it("pushes one row from its menu, and a cancelled plan sends nothing", async () => {
    const wrapper = await open()

    await press(wrapper, "cohort-subject-member-menu-1")
    await click(wrapper, "VListItem", "cohort-subject-member-push-1")
    const cancel = part(wrapper, "VCard", "cohort-drift-plan").findAllComponents({name: "VBtn"}).find((b) => b.text() === "Cancel")!
    await cancel.trigger("click")
    await settle()
    expect(wrapper.findAllComponents({name: "VCard"}).some((c) => c.attributes("data-testid") === "cohort-drift-plan")).toBe(false)
    expect(pushDriftPeople).not.toHaveBeenCalled()
  })

  it("a plan dismissed by its backdrop sends nothing", async () => {
    const wrapper = await open()

    await press(wrapper, "cohort-subject-member-menu-1")
    await click(wrapper, "VListItem", "cohort-subject-member-push-1")
    const dialog = wrapper.findAllComponents({name: "VDialog"}).find((d) => d.props("modelValue") === true)!
    dialog.vm.$emit("update:modelValue", false)
    await settle()
    expect(pushDriftPeople).not.toHaveBeenCalled()
  })

  it("says an adopt was queued", async () => {
    const wrapper = await open()
    await wrapper.get("[data-testid=cohort-subject-targets] [data-testid=info-box-toggle]").trigger("click")
    await settle()
    await press(wrapper, "cohort-subject-target-menu-brevo")
    await click(wrapper, "VListItem", "cohort-subject-inbound-reconcile-brevo")

    wrapper.findComponent({name: "InboundReconcileModal"}).vm.$emit("applied")
    await settle()

    expect(wrapper.get("[data-testid=cohort-subject-success]").text()).toBe("Adopt queued.")
  })

  it("shows each target's drift and the runs before it", async () => {
    const run = (startedAt: string, missing: number) => ({startedAt, trigger: JobTrigger.SCHEDULED_RUN, inStep: 40, missing, extra: 2})
    const withRuns = subject()
    withRuns.mappings[0]!.runs = [run("2026-09-29T03:00:00Z", 1), run("2026-09-28T03:00:00Z", 3)]
    vi.mocked(fetchCohortSubject).mockResolvedValue(withRuns)
    const wrapper = await mountPage(CohortSubjectDetail, {path: "/management/cohorts/subjects/7", login: adminLogin})
    await wrapper.get("[data-testid=cohort-subject-targets] [data-testid=info-box-toggle]").trigger("click")
    await settle()

    expect(wrapper.get("[data-testid=cohort-subject-target-drift-brevo]").text()).toBe("40 in step · 1 missing · 2 extra")
    expect(wrapper.get("[data-testid=cohort-subject-target-drift-history-brevo]").text()).toContain("nightly: 3 missing, 2 extra")
  })
})
