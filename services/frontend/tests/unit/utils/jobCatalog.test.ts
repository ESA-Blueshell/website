import {describe, expect, it} from "vitest"
import {JOB_CATALOG, humanizeJobType, jobCatalogEntry} from "@/utils/jobCatalog"

describe("humanizeJobType", () => {
  it("reads a dotted job type as words", () => {
    expect(humanizeJobType("contact.sync")).toBe("Contact Sync")
  })

  it("treats a dot, an underscore and a hyphen alike, since the backend uses all three", () => {
    expect(humanizeJobType("cohort.reconcile-all-users")).toBe("Cohort Reconcile All Users")
    expect(humanizeJobType("cohort_evaluate_user")).toBe("Cohort Evaluate User")
  })

  it("evens out a run of separators rather than leaving a gap between words", () => {
    expect(humanizeJobType("contact..sync--all")).toBe("Contact Sync All")
    expect(humanizeJobType(".contact.sync.")).toBe("Contact Sync")
  })

  it("gives every word one capital, whatever case the type arrived in", () => {
    expect(humanizeJobType("EMAIL.RECOVERY")).toBe("Email Recovery")
    expect(humanizeJobType("eMaIl.reCovery")).toBe("Email Recovery")
  })

  it("says nothing for a type that is nothing, which is what an unsaved row carries", () => {
    expect(humanizeJobType("")).toBe("")
    expect(humanizeJobType("...")).toBe("")
  })
})

describe("jobCatalogEntry", () => {
  it("answers with the catalogued title and description for a job type it knows", () => {
    const entry = jobCatalogEntry("contact.sync")

    expect(entry.title).toBe("Sync contact")
    expect(entry.description).toContain("Idempotent")
  })

  it("names a job type it does not know from the type itself, so no row reads as blank", () => {
    // The catalogue is written by hand against the backend's JobDefinition objects, so a type
    // registered there and not here still has to arrive at the operator as words.
    expect(jobCatalogEntry("bookkeeping.close-year").title).toBe("Bookkeeping Close Year")
  })

  it("leaves a job type it does not know without a description, rather than inventing one", () => {
    // The trigger dialog and the row caption both read this, and an empty string is what they
    // treat as nothing to say.
    expect(jobCatalogEntry("bookkeeping.close-year").description).toBe("")
  })

  it("names an execution carrying no type at all, which is how JobManager asks", () => {
    expect(jobCatalogEntry("")).toEqual({title: "", description: ""})
  })
})

describe("JOB_CATALOG", () => {
  it("says something about every job it lists, since a listed job with no description reads as a gap", () => {
    const silent = Object.entries(JOB_CATALOG)
      .filter(([, entry]) => entry.title.trim() === "" || entry.description.trim() === "")
      .map(([type]) => type)

    expect(silent).toEqual([])
  })

  it("keys every entry by the backend's own type string", () => {
    // A key that is not a dotted lowercase type can never be looked up: the row carries the
    // type the backend registered, and nothing normalises it on the way in.
    const misKeyed = Object.keys(JOB_CATALOG).filter(type => !/^[a-z]+(?:\.[a-z-]+)+$/.test(type))

    expect(misKeyed).toEqual([])
  })

  it("gives each job a title of its own, so two rows never read as the same job", () => {
    const titles = Object.values(JOB_CATALOG).map(entry => entry.title)

    expect(new Set(titles).size).toBe(titles.length)
  })
})
