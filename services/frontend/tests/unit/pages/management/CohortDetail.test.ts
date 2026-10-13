import {beforeEach, describe, expect, it, vi} from "vitest"
import CohortDetail from "@/pages/management/CohortDetail.vue"
import {
  TargetKind,
  CohortCategory,
  CohortType,
  TargetSystem,
  fetchCohort,
  linkUserToExternal,
  removeExternalMember,
  triggerReconcile,
  type CohortMember,
  type Cohort,
} from "@/domains/cohorts/adapters/cohorts"
import router from "@/plugins/router"
import type {StoredLogin} from "@/plugins/store"
import {mountPage} from "../../helpers/mountPage"
import {settle} from "../../helpers/testUtils"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  fetchCohort: vi.fn(),
  linkUserToExternal: vi.fn(),
  removeExternalMember: vi.fn(),
  triggerReconcile: vi.fn(),
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
  }],
  members: [
    member({targetMemberId: 1, userId: 5, userFullName: "Ada Lovelace", sync: "ONLY_HERE"}),
    member({targetMemberId: 2, externalUserId: "ext-2", externalLabel: "grace@example.com", sync: "ONLY_EXTERNAL"}),
  ],
})

const open = async () => {
  vi.mocked(fetchCohort).mockResolvedValue(cohort())
  return mountPage(CohortDetail, {path: "/management/cohort/7", login: adminLogin})
}

type Page = Awaited<ReturnType<typeof open>>

const press = async (wrapper: Page, testid: string) => {
  await wrapper.get(`[data-testid="${testid}"]`).trigger("click")
  await settle()
}

// Dialogs and menus teleport out of the page's element, so they are found as components.
const click = async (wrapper: Page, name: string, testid: string) => {
  const found = wrapper.findAllComponents({name}).find((part) => part.attributes("data-testid") === testid)
  if (!found) throw new Error(`no ${name} ${testid}`)
  await found.trigger("click")
  await settle()
}

// The targets stand in a box that opens on a press.
const withTargets = async () => {
  const wrapper = await open()
  await wrapper.get("[data-testid=cohort-detail-targets] [data-testid=info-box-toggle]").trigger("click")
  await settle()
  return wrapper
}

describe("the cohort page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("reads the cohort its address names and shows what it is", async () => {
    const wrapper = await open()

    expect(fetchCohort).toHaveBeenCalledWith(7)
    expect(wrapper.get("[data-testid=cohort-detail-identity]").text()).toContain("Paid 2026")
    expect(wrapper.get("[data-testid=cohort-detail-back]").text()).toBe("Periods")
  })

  it("reconciles a target from its menu", async () => {
    vi.mocked(triggerReconcile).mockResolvedValue(3)
    const wrapper = await withTargets()

    await press(wrapper, "cohort-detail-target-menu-brevo")
    await click(wrapper, "VListItem", "cohort-detail-reconcile-brevo")

    expect(triggerReconcile).toHaveBeenCalledWith(40)
    expect(wrapper.get("[data-testid=cohort-detail-success]").text()).toBe("Reconcile enqueued.")
  })

  it("opens the picker to switch a target, and to add one", async () => {
    const wrapper = await withTargets()
    const picker = () => wrapper.findComponent({name: "TargetPickerModal"})

    await press(wrapper, "cohort-detail-target-menu-brevo")
    await click(wrapper, "VListItem", "cohort-detail-switch-target-brevo")
    expect(picker().props()).toMatchObject({mode: "switch", targetId: 40, system: TargetSystem.BREVO})

    await press(wrapper, "cohort-detail-targets-menu")
    await click(wrapper, "VListItem", "cohort-detail-add-target")
    expect(picker().props()).toMatchObject({mode: "add", targetId: undefined})
  })

  it("opens the inbound reconcile for the target it was asked from", async () => {
    const wrapper = await withTargets()

    await press(wrapper, "cohort-detail-target-menu-brevo")
    await click(wrapper, "VListItem", "cohort-detail-inbound-reconcile-brevo")

    expect(wrapper.findComponent({name: "InboundReconcileModal"}).props()).toMatchObject({cohortId: 7, targetId: 40})
  })

  it("removes somebody who is only on the target from the target the row belongs to", async () => {
    vi.mocked(removeExternalMember).mockResolvedValue(11)
    const wrapper = await open()

    await press(wrapper, "cohort-detail-member-menu-2")
    await click(wrapper, "VListItem", "cohort-detail-member-remove-2")

    expect(removeExternalMember).toHaveBeenCalledWith(40, "ext-2")
    expect(wrapper.get("[data-testid=cohort-detail-success]").text()).toBe("Removal enqueued.")
  })

  it("links somebody who is only on the target to the user picked for them", async () => {
    vi.mocked(linkUserToExternal).mockResolvedValue({type: "ok"})
    const wrapper = await open()

    await press(wrapper, "cohort-detail-member-menu-2")
    await click(wrapper, "VListItem", "cohort-detail-member-link-2")
    await wrapper.findComponent({name: "UserPicker"}).setValue(9)
    await click(wrapper, "VBtn", "cohort-detail-link-confirm")

    expect(linkUserToExternal).toHaveBeenCalledWith(7, 9, TargetSystem.BREVO, "ext-2")
    expect(wrapper.get("[data-testid=cohort-detail-success]").text()).toBe("External id linked.")
  })

  it("says so where the system cannot say which folder a target is in", async () => {
    const known = await withTargets()
    expect(known.find("[data-testid=cohort-detail-target-folder-unknown-brevo]").exists()).toBe(false)

    const unfiled = cohort()
    unfiled.mappings[0]!.folderKnown = false
    vi.mocked(fetchCohort).mockResolvedValue(unfiled)
    const wrapper = await mountPage(CohortDetail, {path: "/management/cohort/7", login: adminLogin})
    await wrapper.get("[data-testid=cohort-detail-targets] [data-testid=info-box-toggle]").trigger("click")
    await settle()

    expect(wrapper.get("[data-testid=cohort-detail-target-folder-unknown-brevo]").text()).toBe("Folder unknown")
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
})
