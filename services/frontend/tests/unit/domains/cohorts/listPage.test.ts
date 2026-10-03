import {describe, expect, it} from "vitest"
import {
  CohortType,
  DriftResolutionAction,
  RESOLUTION_WORDS,
  TargetSystem,
  adoptWord,
  driftRowsOf,
  inStepOn,
  isDrift,
  runBars,
  whyOf,
  type CohortMember,
  type ReconcileRun,
} from "@/domains/cohorts"

const member = (over: Partial<CohortMember> = {}): CohortMember => ({
  targetMemberId: 1, userId: 5, userFullName: "Ada Lovelace", userEmail: "ada@example.com", isUserDeleted: false,
  joinedAt: "2026-01-05T10:00:00Z", externalLabel: null, externalUserId: null, system: TargetSystem.BREVO, sync: "IN_SYNC", ...over,
})
const run = (missing: number, extra: number): ReconcileRun => ({startedAt: "2026-02-10T09:00:00Z", trigger: null, inStep: 40, missing, extra})

describe("one list's drift", () => {
  it("keeps the people on one side only of that system's list, by name, and searches them", () => {
    const rows = [
      member({targetMemberId: 1, userFullName: "Zoe", sync: "ONLY_HERE"}),
      member({targetMemberId: 2, userFullName: "Ann", sync: "ONLY_EXTERNAL", userEmail: null, externalLabel: "ann@x.nl"}),
      member({targetMemberId: 3, sync: "IN_SYNC"}),
      member({targetMemberId: 4, sync: "ONLY_HERE", system: null}),
    ]
    expect(driftRowsOf(rows, TargetSystem.BREVO).map((row) => row.targetMemberId)).toEqual([2, 1])
    expect(driftRowsOf(rows, TargetSystem.BREVO, " ANN@x ").map((row) => row.targetMemberId)).toEqual([2])
    expect(inStepOn(rows, TargetSystem.BREVO)).toBe(1)
    expect(isDrift(member({sync: "BROKEN"}))).toBe(false)
  })

  it("says why a person drifts, in the cohort's name", () => {
    expect(whyOf(member({sync: "ONLY_HERE"}), "Paid 2025-2026")).toMatch(/^In Paid 2025-2026 since .+, and not on the list$/)
    expect(whyOf(member({sync: "ONLY_EXTERNAL"}), "Paid 2025-2026")).toBe("On the list, and not in Paid 2025-2026")
    expect(whyOf(member({sync: "ONLY_EXTERNAL", userId: null}), "Paid 2025-2026")).toBe("On the list; no account here has this address")
  })

  it("takes people in only where the cohort's data can, and names every resolution", () => {
    expect(adoptWord(CohortType.PERIOD_PAYERS)).toBe("Record as paid")
    expect(adoptWord(CohortType.COMMITTEE_MEMBERS)).toBeNull()
    expect(adoptWord(null)).toBeNull()
    for (const action of Object.values(DriftResolutionAction)) expect(RESOLUTION_WORDS[action]).toBeTruthy()
  })

  it("draws the last runs oldest first, as tall as their drift", () => {
    expect(runBars([run(0, 2), run(1, 0), run(0, 0)])).toEqual([
      {height: 3, drift: false},
      {height: 11, drift: true},
      {height: 21, drift: true},
    ])
    expect(runBars(Array.from({length: 10}, () => run(0, 0)))).toHaveLength(8)
    expect(runBars([])).toEqual([])
  })
})
