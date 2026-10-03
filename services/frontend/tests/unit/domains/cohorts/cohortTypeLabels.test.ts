import {describe, expect, it} from "vitest"
import {CohortType} from "@/services/api"
import {
  COHORT_TYPE_LABELS,
  cohortTypeLabel,
} from "@/domains/cohorts/cohortTypeLabels"

describe("cohortTypeLabels", () => {
  it("names every kind the api can send", () => {
    // A kind added upstream should surface here rather than render as its raw name.
    for (const type of Object.values(CohortType)) {
      expect(COHORT_TYPE_LABELS[type]).toBeTruthy()
    }
  })

  it("names the separations in the words they are talked about in", () => {
    expect(cohortTypeLabel(CohortType.COMMITTEE_MEMBERS)).toBe("Committee members")
    expect(cohortTypeLabel(CohortType.PERIOD_ACTIVE_MEMBERS)).toBe("Active members in period")
    expect(cohortTypeLabel(CohortType.PERIOD_PAYERS)).toBe("Contribution paid")
  })

  it("falls back to the raw name rather than rendering nothing", () => {
    expect(cohortTypeLabel("SOMETHING_NEW" as CohortType)).toBe("SOMETHING_NEW")
  })
})
