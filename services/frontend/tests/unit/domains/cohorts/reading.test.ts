import {describe, expect, it} from "vitest"
import {TargetKind, JobTrigger, TargetSystem, type CohortMember, type ReconcileRun, type TargetMapping} from "@/domains/cohorts/adapters/cohorts"
import {
  driftLabel,
  earlierDrift,
  isMember,
  memberName,
  runStartedBy,
  syncChipColour,
  syncLabel,
  systemLabel,
} from "@/domains/cohorts"

const member = (over: Partial<CohortMember> = {}): CohortMember => ({
  targetMemberId: 1,
  userId: 5,
  userFullName: "Ada Lovelace",
  userEmail: "ada@example.com",
  isUserDeleted: false,
  joinedAt: "2026-01-05T10:00:00Z",
  externalLabel: null,
  externalUserId: null,
  system: null,
  sync: "IN_SYNC",
  ...over,
})

describe("what a ledger row is called", () => {
  it("is the name we hold, where we hold one", () => {
    expect(memberName(member())).toBe("Ada Lovelace")
  })

  it("says a deleted account is deleted rather than dropping the row", () => {
    expect(memberName(member({userFullName: null, isUserDeleted: true}))).toBe("Deleted user #5")
  })

  it("falls back to the id of an account we have no name for", () => {
    expect(memberName(member({userFullName: null}))).toBe("User #5")
  })

  it("names a stranger by what the target calls it, then by its id, then not at all", () => {
    const stranger = {userId: null, userFullName: null, sync: "ONLY_EXTERNAL"} as Partial<CohortMember>
    expect(memberName(member({...stranger, externalLabel: "ada@brevo"}))).toBe("ada@brevo")
    expect(memberName(member({...stranger, externalUserId: "sub-99"}))).toBe("sub-99")
    expect(memberName(member(stranger))).toBe("Unknown")
  })
})

describe("what the sync column says", () => {
  it("names the system a row is waiting on", () => {
    expect(syncLabel(member({sync: "ONLY_HERE", system: "BREVO"}))).toBe("Not in Brevo yet")
    expect(syncLabel(member({sync: "ONLY_EXTERNAL", system: "BREVO"}))).toBe("Only in Brevo")
  })

  it("speaks of the target in the abstract where the row names no system", () => {
    expect(syncLabel(member({sync: "ONLY_HERE"}))).toBe("Not in the target yet")
  })

  it("chips only the exceptions", () => {
    expect(syncChipColour(member({sync: "IN_SYNC"}))).toBeUndefined()
    expect(syncChipColour(member({sync: "ONLY_HERE"}))).toBe("info")
    expect(syncChipColour(member({sync: "ONLY_EXTERNAL"}))).toBe("warning")
    expect(syncChipColour(member({sync: "BROKEN"}))).toBe("error")
  })

  it("counts everything but a row only the target knows as one of ours", () => {
    expect(isMember(member({sync: "BROKEN"}))).toBe(true)
    expect(isMember(member({sync: "ONLY_EXTERNAL"}))).toBe(false)
  })
})

describe("what a system is called", () => {
  it("names the systems we speak to, and leaves an unknown one as its own id", () => {
    expect(systemLabel("GOOGLE_WORKSPACE")).toBe("Google Workspace")
    expect(systemLabel("MASTODON")).toBe("MASTODON")
  })
})

const run = (over: Partial<ReconcileRun> = {}): ReconcileRun => ({
  startedAt: "2026-02-10T09:00:00Z",
  trigger: JobTrigger.SCHEDULED_RUN,
  inStep: 40,
  missing: 1,
  extra: 2,
  ...over,
})

const mapping = (runs: ReconcileRun[]): TargetMapping => ({
  targetId: 3,
  system: TargetSystem.BREVO,
  kind: TargetKind.LIST,
  label: "Newsletter",
  externalId: "7",
  lastReconciledAt: null,
  path: [],
  folderKnown: true,
  runs,
  enforced: false,
})

describe("drift", () => {
  it("names what started a run", () => {
    expect(runStartedBy(run())).toBe("Nightly")
    expect(runStartedBy(run({trigger: JobTrigger.BY_HAND}))).toBe("By hand")
    expect(runStartedBy(run({trigger: JobTrigger.SITE_ACTION}))).toBe("After a change")
    expect(runStartedBy(run({trigger: null}))).toBe("Unknown")
  })

  it("reads the latest run as the target's drift, and nothing before the first", () => {
    expect(driftLabel(mapping([run(), run({missing: 9})]))).toBe("40 in step · 1 missing · 2 extra")
    expect(driftLabel(mapping([]))).toBe("")
  })

  it("lists the runs before the latest, at most four", () => {
    const runs = [run(), ...Array.from({length: 6}, () => run({trigger: JobTrigger.BY_HAND, missing: 3, extra: 0}))]

    const lines = earlierDrift(mapping(runs))

    expect(lines).toHaveLength(4)
    expect(lines[0]).toMatch(/by hand: 3 missing, 0 extra$/)
  })
})
