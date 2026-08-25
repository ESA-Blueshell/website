import {describe, expect, it} from "vitest"
import {CohortSubjectType} from "@/services/api"
import {
  COHORT_TYPE_LABELS,
  COHORT_TYPE_ORDER,
  cohortTypeLabel,
} from "@/domains/cohorts/cohortTypeLabels"

describe("cohortTypeLabels", () => {
  it("names every kind the api can send", () => {
    // A kind added upstream should surface here rather than render as its raw name.
    for (const type of Object.values(CohortSubjectType)) {
      expect(COHORT_TYPE_LABELS[type]).toBeTruthy()
    }
  })

  it("reads the committee cohorts first, then a period in the order it progresses", () => {
    expect(COHORT_TYPE_ORDER).toEqual([
      CohortSubjectType.COMMITTEE_MEMBERS,
      CohortSubjectType.PERIOD_MEMBERS,
      CohortSubjectType.PERIOD_ACTIVE_MEMBERS,
      CohortSubjectType.PERIOD_PAYERS,
      CohortSubjectType.NEWSLETTER_SUBSCRIBERS,
      CohortSubjectType.CUSTOM,
    ])
  })

  it("names the separations in the words they are talked about in", () => {
    expect(cohortTypeLabel(CohortSubjectType.COMMITTEE_MEMBERS)).toBe("Committee members")
    expect(cohortTypeLabel(CohortSubjectType.PERIOD_ACTIVE_MEMBERS)).toBe("Active members in period")
    expect(cohortTypeLabel(CohortSubjectType.PERIOD_PAYERS)).toBe("Contribution paid")
  })

  it("falls back to the raw name rather than rendering nothing", () => {
    expect(cohortTypeLabel("SOMETHING_NEW" as CohortSubjectType)).toBe("SOMETHING_NEW")
  })
})
