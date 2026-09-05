import {readdirSync, readFileSync} from "node:fs"
import {fileURLToPath} from "node:url"
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

/**
 * The api's job types and the catalogue's keys, compared against each other.
 *
 * The types are not published: `jobType` is a bare `string` in the generated sdk, so there is no
 * enum to assert against, and a second hand-written list here would be one more copy to drift.
 * So the api's own `JobDefinition` objects are read as text, the way
 * `tests/unit/architecture/uncheckedSdkWrites.test.ts` reads `sdk.gen.ts`. Both suites run from a
 * full checkout, api tree included.
 */
describe("JOB_CATALOG against the api's registered job types", () => {
  // Held in a variable, not written inline: Vite rewrites a literal `new URL("…", import.meta.url)`
  // into an asset reference, and the path never reaches node:url.
  const API_SOURCES = "../../../../api/src/main/kotlin/"
  const apiRoot = fileURLToPath(new URL(API_SOURCES, import.meta.url))

  /** Every `.kt` file under the api's main sources. */
  function kotlinSourcesUnder(directory: string): string[] {
    return readdirSync(directory, {withFileTypes: true}).flatMap(entry => {
      const path = `${directory}/${entry.name}`
      return entry.isDirectory() ? kotlinSourcesUnder(path) : entry.name.endsWith(".kt") ? [path] : []
    })
  }

  /**
   * The type string of every registered job.
   *
   * Read out of the files that declare a `JobDefinition`, rather than by parsing each object: the
   * literal is what the queue stores and what a row arrives carrying, and matching the literal
   * survives a reformat of the declaration around it.
   */
  let swept: string[] | null = null
  const registeredJobTypes = (): string[] => {
    if (swept) return swept
    const declaration = /override val type: String = "([^"]+)"/g
    swept = kotlinSourcesUnder(apiRoot)
      .map(path => readFileSync(path, "utf8"))
      .filter(source => source.includes("JobDefinition<"))
      .flatMap(source => [...source.matchAll(declaration)].map(([, type]) => type))
    return swept
  }

  /** Reading the api's sources takes well under a second, and far longer on a contended runner. */
  const SWEEP_TIMEOUT_MS = 30_000

  it("reads the api's job definitions at all, so an empty sweep cannot read as agreement", () => {
    expect(registeredJobTypes().length).toBeGreaterThan(10)
  }, SWEEP_TIMEOUT_MS)

  it("names every job type the api registers, so no trigger dialog explains a job by its type", () => {
    const unnamed = registeredJobTypes().filter(type => !(type in JOB_CATALOG))

    expect(unnamed).toEqual([])
  }, SWEEP_TIMEOUT_MS)

  it("lists no job type the api has stopped registering, so a stale entry is not left to rot", () => {
    const registered = new Set(registeredJobTypes())
    const stale = Object.keys(JOB_CATALOG).filter(type => !registered.has(type))

    expect(stale).toEqual([])
  }, SWEEP_TIMEOUT_MS)
})
