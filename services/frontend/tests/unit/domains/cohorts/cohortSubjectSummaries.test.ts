import {describe, expect, it} from "vitest"
import type {CohortSubjectDetail} from "@/services/api"
import {
  countLabel,
  nounFor,
  membersSummary,
  rulesSummary,
  subjectCounts,
  targetsSummary,
} from "@/domains/cohorts/cohortSubjectSummaries"

type Rule = CohortSubjectDetail["rules"][number]
type Member = CohortSubjectDetail["members"][number]
type Mapping = CohortSubjectDetail["mappings"][number]

const rule = (enabled: boolean): Rule =>
  ({id: enabled ? 1 : 2, factKind: "COMMITTEE", factKey: "board", enabled}) as Rule

const member = (userId: number, isUserDeleted = false): Member =>
  ({cohortMemberId: userId, userId, isUserDeleted, joinedAt: "2026-01-01T00:00:00Z"}) as Member

const mapping = (system: string): Mapping =>
  ({system, cohortId: 1, kind: "LIST", label: system}) as Mapping

describe("countLabel", () => {
  it("keeps the noun singular for exactly one", () => {
    expect(countLabel(1, "member")).toBe("1 member")
  })

  it("pluralises everything else, zero included", () => {
    expect(countLabel(0, "member")).toBe("0 members")
    expect(countLabel(2, "member")).toBe("2 members")
  })

  it("carries an irregular plural through", () => {
    expect(countLabel(4, "category", "categories")).toBe("4 categories")
  })
})

describe("nounFor", () => {
  it("gives the bare noun, for the places that bold the number themselves", () => {
    expect(nounFor(1, "cohort")).toBe("cohort")
    expect(nounFor(3, "cohort")).toBe("cohorts")
  })

  it("takes an explicit plural, because not every noun takes an s", () => {
    expect(nounFor(1, "category", "categories")).toBe("category")
    expect(nounFor(4, "category", "categories")).toBe("categories")
  })
})

describe("subjectCounts", () => {
  it("names the people and the places in one line", () => {
    const subject = {
      members: [member(1), member(2)],
      mappings: [mapping("BREVO")],
    } as CohortSubjectDetail

    expect(subjectCounts(subject)).toBe("2 members · 1 sync target")
  })
})

describe("rulesSummary", () => {
  it("counts every rule but says how many actually apply", () => {
    expect(rulesSummary([rule(true), rule(false)])).toBe("2 rules · 1 enabled")
  })

  it("reports none rather than reading as though one applies", () => {
    expect(rulesSummary([])).toBe("0 rules · 0 enabled")
  })
})

describe("targetsSummary", () => {
  it("counts the mappings", () => {
    expect(targetsSummary([mapping("BREVO"), mapping("DISCORD")])).toBe("2 sync targets")
  })
})

describe("membersSummary", () => {
  it("stays silent about deleted members when there are none", () => {
    expect(membersSummary([member(1), member(2)])).toBe("2 members")
  })

  it("names them once there are, and still counts them in the total", () => {
    expect(membersSummary([member(1), member(2, true)])).toBe("2 members · 1 deleted")
  })

  it("handles a cohort nobody is in", () => {
    expect(membersSummary([])).toBe("0 members")
  })
})
