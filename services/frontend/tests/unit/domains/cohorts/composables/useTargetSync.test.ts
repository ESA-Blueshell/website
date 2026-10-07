import {beforeEach, describe, expect, it, vi} from "vitest"
import {ref} from "vue"
import {useTargetSync} from "@/domains/cohorts/composables/useTargetSync"
import {
  TargetSystem,
  fetchCohort,
  pushDriftPeople,
  removeDriftPeople,
  triggerReconcile,
  type Cohort,
  type ListedTarget,
} from "@/domains/cohorts/adapters/cohorts"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/domains/cohorts/adapters/cohorts")>()),
  fetchCohort: vi.fn(),
  pushDriftPeople: vi.fn(),
  removeDriftPeople: vi.fn(),
  triggerReconcile: vi.fn(),
}))

const list = (externalId: string, label: string, fields: Partial<ListedTarget> = {}): ListedTarget => ({externalId, label, enforced: false, ...fields})
const members = list("7", "Members", {cohortId: 101, targetId: 1, cohortLabel: "Members 2025-2026", missing: 2, extra: 0})
const board = list("8", "Board", {cohortId: 102, targetId: 2, cohortLabel: "Board", missing: 0, extra: 3})
const loose = list("9", "Old newsletter")
const person = (userId: number | null, sync: string, fields: Record<string, unknown> = {}) =>
  ({system: TargetSystem.BREVO, sync, userId, userFullName: `Person ${userId}`, ...fields})

describe("comparing or filling several lists at once", () => {
  const ticked = ref<ListedTarget[]>([])
  const sync = useTargetSync(TargetSystem.BREVO, ticked, ["list", "lists"])

  beforeEach(() => {
    vi.clearAllMocks()
    ticked.value = [members, board, loose]
    sync.task.value = null
  })

  it("compares every ticked list that follows something, and leaves out one that follows nothing", async () => {
    vi.mocked(triggerReconcile).mockResolvedValue({ok: true})
    sync.task.value = "compare"

    expect(sync.title.value).toBe("Compare with Brevo")
    expect(sync.items.value.map((one) => [one.name, one.note])).toEqual([["Members", "Members 2025-2026"], ["Board", "Board"]])
    expect(sync.skipped.value).toEqual([{name: "Old newsletter", why: "Follows nothing on the site"}])
    expect(sync.words.value).toMatchObject({go: "Compare 2 lists", ask: "Compare 2 lists with Brevo now?", done: "queued"})
    expect(await sync.run(sync.items.value[0]!)).toEqual({ok: true})
    expect(triggerReconcile).toHaveBeenCalledWith(101, 1)
  })

  it("fills only a list somebody is missing from, with the people who can be added", async () => {
    vi.mocked(fetchCohort).mockResolvedValue({members: [
      person(11, "ONLY_HERE"), person(12, "ONLY_HERE", {unreachable: true}), person(null, "ONLY_EXTERNAL"), person(13, "IN_SYNC"),
      {...person(14, "ONLY_HERE"), system: TargetSystem.DISCORD},
    ]} as unknown as Cohort)
    vi.mocked(pushDriftPeople).mockResolvedValue({ok: true, saved: 1})
    sync.task.value = "push"

    expect(sync.title.value).toBe("Add missing people")
    expect(sync.items.value.map((one) => [one.name, one.note])).toEqual([["Members", "2 missing"]])
    expect(sync.skipped.value).toEqual([{name: "Board", why: "Nobody is missing"}, {name: "Old newsletter", why: "Follows nothing on the site"}])
    expect(sync.words.value).toMatchObject({go: "Add to 1 list", doing: "Adding", done: "filled"})
    expect(await sync.run(sync.items.value[0]!)).toEqual({ok: true})
    expect(pushDriftPeople).toHaveBeenCalledWith(101, 1, [11])
  })

  it("says why a list could not be filled, and does nothing where nobody can be added", async () => {
    sync.task.value = "push"
    const item = sync.items.value[0]!

    vi.mocked(fetchCohort).mockResolvedValue(null)
    expect(await sync.run(item)).toEqual({ok: false, reason: "What it follows could not be read."})

    vi.mocked(fetchCohort).mockResolvedValue({members: [person(12, "ONLY_HERE", {unreachable: true})]} as unknown as Cohort)
    expect(await sync.run(item)).toEqual({ok: true})
    expect(pushDriftPeople).not.toHaveBeenCalled()

    vi.mocked(fetchCohort).mockResolvedValue({members: [person(11, "ONLY_HERE")]} as unknown as Cohort)
    vi.mocked(pushDriftPeople).mockResolvedValue({ok: false, reason: "Brevo refused."})
    expect(await sync.run(item)).toEqual({ok: false, reason: "Brevo refused."})
  })

  it("clears only a list with somebody on it who should not be, of the people the list has and the site does not", async () => {
    vi.mocked(fetchCohort).mockResolvedValue({members: [
      person(null, "ONLY_EXTERNAL", {externalUserId: "ann@x.nl"}), person(11, "ONLY_HERE"), person(null, "ONLY_EXTERNAL"),
      {...person(null, "ONLY_EXTERNAL", {externalUserId: "bo@x.nl"}), system: TargetSystem.DISCORD},
    ]} as unknown as Cohort)
    vi.mocked(removeDriftPeople).mockResolvedValue({ok: true, saved: 0})
    sync.task.value = "remove"

    expect(sync.title.value).toBe("Remove additional people")
    expect(sync.items.value.map((one) => [one.name, one.note])).toEqual([["Board", "3 leave"]])
    expect(sync.skipped.value).toEqual([
      {name: "Members", why: "Nobody is on it who should not be"},
      {name: "Old newsletter", why: "Follows nothing on the site"},
    ])
    expect(sync.words.value).toMatchObject({go: "Remove them", doing: "Removing", done: "cleared"})
    expect(sync.words.value.ask).toContain("This is not undone from here.")
    expect(await sync.run(sync.items.value[0]!)).toEqual({ok: true})
    expect(removeDriftPeople).toHaveBeenCalledWith(102, 2, ["ann@x.nl"])

    ticked.value = [{...board, extra: 1}]
    expect(sync.items.value[0]!.note).toBe("1 leaves")
    vi.mocked(fetchCohort).mockResolvedValue({members: []} as unknown as Cohort)
    expect(await sync.run(sync.items.value[0]!)).toEqual({ok: true})
    vi.mocked(fetchCohort).mockResolvedValue({members: [person(null, "ONLY_EXTERNAL", {externalUserId: "ann@x.nl"})]} as unknown as Cohort)
    vi.mocked(removeDriftPeople).mockResolvedValue({ok: false, reason: "Brevo refused."})
    expect(await sync.run(sync.items.value[0]!)).toEqual({ok: false, reason: "Brevo refused."})
  })
})
