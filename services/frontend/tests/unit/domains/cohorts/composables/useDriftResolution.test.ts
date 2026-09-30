import {beforeEach, describe, expect, it, vi} from "vitest"
import {ref} from "vue"
import {useDriftResolution} from "@/domains/cohorts/composables/useDriftResolution"
import {
  CohortKind,
  TargetSystem,
  linkDriftPeople,
  proposeDriftLinks,
  pushDriftPeople,
  removeDriftPeople,
  type CohortMember,
  type TargetMapping,
} from "@/domains/cohorts/adapters/cohorts"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/domains/cohorts/adapters/cohorts")>()
  return {
    ...actual,
    pushDriftPeople: vi.fn(),
    removeDriftPeople: vi.fn(),
    proposeDriftLinks: vi.fn(),
    linkDriftPeople: vi.fn(),
  }
})

const mapping = (system: TargetSystem, cohortId: number): TargetMapping => ({
  cohortId,
  system,
  kind: CohortKind.LIST,
  label: "Members",
  externalId: "7",
  lastReconciledAt: null,
  path: [],
  folderKnown: true,
  runs: [],
})

let nextId = 1
const row = (over: Partial<CohortMember>): CohortMember => ({
  cohortMemberId: nextId++,
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

const oursOnly = (userId: number) => row({userId, sync: "ONLY_HERE"})
const theirsOnly = (externalUserId: string) => row({externalUserId, externalLabel: `${externalUserId}@example.com`, sync: "ONLY_EXTERNAL"})

const setup = () => {
  const reload = vi.fn().mockResolvedValue(undefined)
  const drift = useDriftResolution(ref(3), ref([mapping(TargetSystem.BREVO, 40)]), reload)
  return {drift, reload}
}

describe("useDriftResolution", () => {
  beforeEach(() => vi.resetAllMocks())

  it("offers each action only to the rows it fits", () => {
    const {drift} = setup()

    expect(drift.canResolve("push", oursOnly(5))).toBe(true)
    expect(drift.canResolve("push", theirsOnly("x"))).toBe(false)
    expect(drift.canResolve("remove", theirsOnly("x"))).toBe(true)
    expect(drift.canResolve("link", theirsOnly("x"))).toBe(true)
    expect(drift.canResolve("link", row({userId: 5, externalUserId: "x", sync: "ONLY_EXTERNAL"}))).toBe(false)
    expect(drift.canResolve("remove", row({externalUserId: "x", sync: "ONLY_EXTERNAL", system: TargetSystem.GOOGLE_CALENDAR}))).toBe(false)
  })

  it("counts the ticked rows each action fits", () => {
    const {drift} = setup()
    const rows = [oursOnly(5), theirsOnly("x"), oursOnly(6)]
    drift.toggle(rows[0]!)
    drift.toggle(rows[1]!)
    drift.toggle(rows[2]!)
    drift.toggle(rows[2]!)

    expect(drift.selectedFor("push", rows)).toBe(1)
    expect(drift.selectedFor("remove", rows)).toBe(1)
  })

  it("pushes the planned people per target, then reloads and clears the selection", async () => {
    vi.mocked(pushDriftPeople).mockResolvedValue({ok: true, saved: 2})
    const {drift, reload} = setup()
    const rows = [oursOnly(5), oursOnly(6), theirsOnly("x")]
    drift.toggle(rows[0]!)

    await drift.prepare("push", rows)
    expect(drift.plan.value?.groups).toEqual([{cohortId: 40, people: [rows[0], rows[1]]}])
    expect(drift.planCount.value).toBe(2)
    await drift.confirm()

    expect(pushDriftPeople).toHaveBeenCalledWith(3, 40, [5, 6])
    expect(drift.message.value).toBe("2 pushed.")
    expect(drift.plan.value).toBeNull()
    expect(drift.selection.value.size).toBe(0)
    expect(reload).toHaveBeenCalled()
  })

  it("reports a refused removal", async () => {
    vi.mocked(removeDriftPeople).mockResolvedValue({ok: false, reason: "The target has not been created yet."})
    const {drift} = setup()

    await drift.prepare("remove", [theirsOnly("x")])
    await drift.confirm()

    expect(removeDriftPeople).toHaveBeenCalledWith(3, 40, ["x"])
    expect(drift.message.value).toBe("0 removed.")
    expect(drift.error.value).toBe("The target has not been created yet.")
  })

  it("links only the contacts an account was found for, and says how many another account holds", async () => {
    vi.mocked(proposeDriftLinks).mockResolvedValue({
      ok: true,
      saved: [
        {externalUserId: "a", label: "a@example.com", userId: 5, userFullName: "Ada"},
        {externalUserId: "b", label: "b@example.com", userId: null, userFullName: null},
      ],
    })
    vi.mocked(linkDriftPeople).mockResolvedValue({ok: true, saved: {linked: 0, conflicts: [{externalUserId: "a", existingUserId: 9}]}})
    const {drift} = setup()

    await drift.prepare("link", [theirsOnly("a"), theirsOnly("b")])
    expect(drift.plan.value?.proposals).toHaveLength(2)
    await drift.confirm()

    expect(linkDriftPeople).toHaveBeenCalledWith(3, 40, [{externalUserId: "a", userId: 5}])
    expect(drift.error.value).toBe("1 already belong to another account.")
  })

  it("sends no link when no contact has an account", async () => {
    vi.mocked(proposeDriftLinks).mockResolvedValue({ok: true, saved: [{externalUserId: "b", label: null, userId: null, userFullName: null}]})
    const {drift} = setup()

    await drift.prepare("link", [theirsOnly("b")])
    await drift.confirm()

    expect(linkDriftPeople).not.toHaveBeenCalled()
    expect(drift.message.value).toBe("0 linked.")
  })

  it("stops at a link lookup that is refused", async () => {
    vi.mocked(proposeDriftLinks).mockResolvedValue({ok: false, reason: "No."})
    const {drift} = setup()

    await drift.prepare("link", [theirsOnly("b")])

    expect(drift.plan.value).toBeNull()
    expect(drift.error.value).toBe("No.")
  })

  it("plans nothing when no row fits, and cancels a plan", async () => {
    const {drift} = setup()

    await drift.prepare("push", [theirsOnly("x")])
    expect(drift.plan.value).toBeNull()

    await drift.prepare("remove", [theirsOnly("x")])
    drift.cancel()
    expect(drift.plan.value).toBeNull()
    await drift.confirm()
    expect(removeDriftPeople).not.toHaveBeenCalled()
  })

  it("links one contact by hand, and answers the account already holding one", async () => {
    vi.mocked(linkDriftPeople)
      .mockResolvedValueOnce({ok: true, saved: {linked: 1, conflicts: []}})
      .mockResolvedValueOnce({ok: true, saved: {linked: 0, conflicts: [{externalUserId: "b", existingUserId: 9}]}})
      .mockResolvedValueOnce({ok: false, reason: "Refused."})
    const {drift, reload} = setup()

    expect(await drift.linkOne(theirsOnly("a"), 5)).toBeNull()
    expect(reload).toHaveBeenCalledOnce()
    expect(await drift.linkOne(theirsOnly("b"), 6)).toBe(9)
    expect(await drift.linkOne(theirsOnly("c"), 7)).toBeNull()
    expect(drift.error.value).toBe("Refused.")
    expect(await drift.linkOne(row({externalUserId: "d", system: TargetSystem.GOOGLE_CALENDAR}), 7)).toBeNull()
  })
})
