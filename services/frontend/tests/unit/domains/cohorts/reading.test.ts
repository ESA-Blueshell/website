import {describe, expect, it} from "vitest"
import {CohortSubjectCategory, type CohortMember} from "@/domains/cohorts/adapters/cohorts"
import {
  categoryLabel,
  isMember,
  memberName,
  syncChipColour,
  syncLabel,
  systemLabel,
} from "@/domains/cohorts"

const member = (over: Partial<CohortMember> = {}): CohortMember => ({
  cohortMemberId: 1,
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

describe("what a system and a category are called", () => {
  it("names the systems we speak to, and leaves an unknown one as its own id", () => {
    expect(systemLabel("GOOGLE_WORKSPACE")).toBe("Google Workspace")
    expect(systemLabel("MASTODON")).toBe("MASTODON")
  })

  it("titles each category the same way on every page", () => {
    expect(categoryLabel(CohortSubjectCategory.COMMITTEES)).toBe("Committees")
    expect(categoryLabel(CohortSubjectCategory.PERIODS)).toBe("Periods")
    expect(categoryLabel(CohortSubjectCategory.MEMBERS)).toBe("Members")
  })
})
