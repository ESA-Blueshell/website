import {describe, expect, it} from "vitest"
import {countLabel, nounFor} from "@/domains/cohorts/cohortSubjectSummaries"

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
