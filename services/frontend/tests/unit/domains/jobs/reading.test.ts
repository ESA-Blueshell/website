import {describe, expect, it} from "vitest"
import type {Job} from "@/domains/jobs"
import {
  actorDisplay,
  canRetry,
  categoryOptions,
  errorSummary,
  hasStackTrace,
  looksLikeStackTrace,
  previewActorDisplay,
  previewTitle,
  relatedEntityLabel,
  rowStatusClass,
  stackTrace,
  statusColor,
  statusCounts,
  statusOptions,
  statusTitle,
  successRate,
  titleCase,
} from "@/domains/jobs"

const job = (fields: Partial<Job>): Job => fields as Job

describe("job reading", () => {
  it("titles a status and a snake-cased type", () => {
    expect(titleCase("contact.sync_user")).toBe("Contact Sync User")
    expect(statusTitle("SUCCESS")).toBe("Success")
    expect(statusTitle(undefined)).toBe("Unknown")
  })

  it("gives each status its colour and its row class", () => {
    expect(statusColor("SUCCESS")).toBe("success")
    expect(statusColor("FAILED")).toBe("error")
    expect(statusColor("RUNNING")).toBe("info")
    expect(statusColor("QUEUED")).toBe("warning")
    expect(statusColor("DEAD")).toBe("secondary")
    expect(statusColor(undefined)).toBe("secondary")
    expect(rowStatusClass("FAILED")).toBe("job-row--failed")
    expect(rowStatusClass("DEAD")).toBe("")
  })

  it("offers a retry only for a job that stopped without succeeding", () => {
    expect(canRetry(job({id: 1, status: "FAILED"}))).toBe(true)
    expect(canRetry(job({id: 1, status: "DEAD"}))).toBe(true)
    expect(canRetry(job({id: 1, status: "SUCCESS"}))).toBe(false)
    // Nothing to point a retry at.
    expect(canRetry(job({status: "FAILED"}))).toBe(false)
  })

  it("names whoever asked for the job, at two lengths", () => {
    expect(actorDisplay(job({initiatedByDisplay: "Admin User"}))).toBe("Admin User")
    expect(actorDisplay(job({initiatedByFullName: "John Doe", initiatedByUsername: "jdoe"})))
      .toBe("John Doe (@jdoe)")
    expect(actorDisplay(job({initiatedByType: "SYSTEM"}))).toBe("System")
    expect(actorDisplay(job({initiatedByUserId: 42}))).toBe("User #42")
    expect(actorDisplay(job({}))).toBe("System")

    expect(previewActorDisplay(job({initiatedByFullName: "Jane Doe"}))).toBe("Jane Doe")
    expect(previewActorDisplay(job({initiatedByDisplay: "Bob (@bob)"}))).toBe("Bob")
    expect(previewActorDisplay(job({initiatedByUserId: 7}))).toBe("User #7")
    expect(previewActorDisplay(job({}))).toBe("System")
  })

  it("falls back to the category where the catalog knows nothing about the type", () => {
    expect(previewTitle(job({jobType: "not.a.known.job", category: "contact"})))
      .toContain("Not A Known Job")
    expect(previewTitle(job({jobType: "", category: "cohort"}))).toBe("Cohort job")
  })

  it("reads a stack trace out of whichever field carried it", () => {
    const trace = "Error\n\tat com.example.Main.run(Main.java:42)"

    expect(looksLikeStackTrace(trace)).toBe(true)
    expect(looksLikeStackTrace("Error\n at something")).toBe(true)
    expect(looksLikeStackTrace("Caused by: java.lang.NullPointerException")).toBe(true)
    expect(looksLikeStackTrace("just a normal message")).toBe(false)
    expect(looksLikeStackTrace(null)).toBe(false)

    expect(hasStackTrace(job({stackTrace: trace}))).toBe(true)
    expect(hasStackTrace(job({errorReason: trace}))).toBe(true)
    expect(hasStackTrace(job({errorReason: "plain reason"}))).toBe(false)
    expect(stackTrace(job({errorReason: trace}))).toBe(trace)
    expect(stackTrace(job({errorReason: "plain reason"}))).toBe("")
  })

  it("prefers the message over the reason, and says so when there is neither", () => {
    expect(errorSummary(job({errorMessage: "boom", errorReason: "trace"}))).toBe("boom")
    expect(errorSummary(job({errorReason: "trace"}))).toBe("trace")
    expect(errorSummary(job({}))).toBe("-")
  })

  it("names a related entity, falling back to its type and id", () => {
    expect(relatedEntityLabel({type: "user", id: 3, label: "Jo Jonkers"})).toBe("Jo Jonkers")
    expect(relatedEntityLabel({type: "event_sign_up", id: 3} as never))
      .toBe("Event Sign Up #3")
  })

  it("reads the counts off the stats rather than off the loaded page", () => {
    const stats = {
      avgSuccessDurationSeconds: 1, deadCount: 2, deadSinceStartup: 0, failedCount: 3,
      failedSinceStartup: 0, queuedCount: 4, recoveriesSinceStartup: 0, runningCount: 5,
      successCount: 6, totalCount: 20,
    }

    expect(statusCounts(stats)).toEqual({
      QUEUED: 4, RUNNING: 5, SUCCESS: 6, FAILED: 3, DEAD: 2,
    })
    expect(successRate(stats)).toBe(30)
    // While the stats are still loading the chip row stays mounted, reading zeroes.
    expect(statusCounts(null)).toEqual({QUEUED: 0, RUNNING: 0, SUCCESS: 0, FAILED: 0, DEAD: 0})
    expect(successRate(null)).toBe(0)
    expect(successRate({...stats, totalCount: 0})).toBe(0)
  })

  it("offers every filter the api declares, not only the ones on screen", () => {
    expect(categoryOptions()).toEqual([
      {title: "All categories", value: "all"},
      {title: "Calendar", value: "calendar"},
      {title: "Contact", value: "contact"},
      {title: "Cohort", value: "cohort"},
      {title: "Email", value: "email"},
      {title: "Other", value: "other"},
    ])
    expect(statusOptions().map(one => one.value))
      .toEqual(["all", "QUEUED", "RUNNING", "SUCCESS", "FAILED", "DEAD"])
  })
})
