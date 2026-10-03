import {describe, expect, it} from "vitest"
import type {Job, JobFoldedTrigger, JobRelatedEntity} from "@/domains/jobs"
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
  relatedEntityLink,
  retryLabel,
  rowStatusClass,
  stackTrace,
  statusColor,
  statusCounts,
  statusOptions,
  statusTitle,
  successRate,
  titleCase,
  effectLabel,
  foldedTriggerLabel,
  triggerLabel,
} from "@/domains/jobs"
import {ActionActorType, JobEffect, JobExecutionCategory, JobExecutionStatus, JobTrigger} from "@/services/api"

const job = (fields: Partial<Job>): Job => fields as Job

describe("job reading", () => {
  it("says what queued a job, and that it was run again by hand", () => {
    expect(triggerLabel(job({trigger: JobTrigger.EVENT_APPROVED}))).toBe("Approving the event")
    expect(triggerLabel(job({trigger: JobTrigger.MORNING_RUN}))).toBe("The 08:00 run")
    expect(triggerLabel(job({trigger: JobTrigger.EVENT_SENT_BACK}))).toBe("Sending the event back to the board")
    expect(triggerLabel(job({trigger: JobTrigger.EVENT_UPDATED, forced: true}))).toBe("Editing the event, run again by hand")
    expect(triggerLabel(job({trigger: JobTrigger.BY_HAND, forced: true}))).toBe("Run a job")
    expect(triggerLabel(job({}))).toBe("")
    expect(Object.values(JobTrigger).every(trigger => triggerLabel(job({trigger})) !== "")).toBe(true)
  })

  it("names a trigger folded into a job in the words its own trigger would have", () => {
    const folded: JobFoldedTrigger = {
      trigger: JobTrigger.SIGN_UPS_CHANGED,
      at: "2026-10-01T10:00:00Z",
      initiatedByType: ActionActorType.USER,
      initiatedByDisplay: "Jane Doe (@jdoe)",
    }
    expect(foldedTriggerLabel(folded)).toBe("A change in sign-ups")
  })

  it("says what a run did to the thing its job keeps", () => {
    const announcement = (effect: JobEffect) => effectLabel(job({jobType: "discord.announcement", effect}))
    expect(announcement(JobEffect.MADE)).toBe("Put up the #events-info announcement")
    expect(announcement(JobEffect.EDITED)).toBe("Edited the #events-info announcement")
    expect(announcement(JobEffect.UNCHANGED)).toBe("Found the #events-info announcement up to date")
    expect(announcement(JobEffect.REMOVED)).toBe("Took down the #events-info announcement")
    expect(effectLabel(job({jobType: "discord.event", effect: JobEffect.MADE}))).toBe("Put up the Discord event")
    expect(effectLabel(job({jobType: "contact.sync", effect: JobEffect.EDITED}))).toBe("Edited what it keeps")
    expect(effectLabel(job({jobType: "discord.post"}))).toBe("")
  })

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
    expect(statusColor("SKIPPED")).toBe("grey")
    expect(statusColor(undefined)).toBe("secondary")
    expect(rowStatusClass("FAILED")).toBe("job-row--failed")
    expect(rowStatusClass("DEAD")).toBe("")
    expect(rowStatusClass("SKIPPED")).toBe("job-row--skipped")
  })

  it("offers a run again only for a job that stopped without doing its work", () => {
    expect(canRetry(job({id: 1, status: JobExecutionStatus.FAILED}))).toBe(true)
    expect(canRetry(job({id: 1, status: JobExecutionStatus.DEAD}))).toBe(true)
    expect(canRetry(job({id: 1, status: JobExecutionStatus.SKIPPED}))).toBe(true)
    expect(retryLabel(job({id: 1, status: JobExecutionStatus.SKIPPED}))).toBe("Run anyway")
    expect(retryLabel(job({id: 1, status: JobExecutionStatus.FAILED}))).toBe("Retry")
    expect(canRetry(job({id: 1, status: JobExecutionStatus.SUCCESS}))).toBe(false)
    // Nothing to point a retry at.
    expect(canRetry(job({status: JobExecutionStatus.FAILED}))).toBe(false)
  })

  it("names whoever asked for the job, at two lengths", () => {
    expect(actorDisplay(job({initiatedByDisplay: "Admin User"}))).toBe("Admin User")
    expect(actorDisplay(job({initiatedByFullName: "John Doe", initiatedByUsername: "jdoe"})))
      .toBe("John Doe (@jdoe)")
    expect(actorDisplay(job({initiatedByType: ActionActorType.SYSTEM}))).toBe("System")
    expect(actorDisplay(job({initiatedByUserId: 42}))).toBe("User #42")
    expect(actorDisplay(job({}))).toBe("System")

    expect(previewActorDisplay(job({initiatedByFullName: "Jane Doe"}))).toBe("Jane Doe")
    expect(previewActorDisplay(job({initiatedByDisplay: "Bob (@bob)"}))).toBe("Bob")
    expect(previewActorDisplay(job({initiatedByUserId: 7}))).toBe("User #7")
    expect(previewActorDisplay(job({}))).toBe("System")
  })

  it("falls back to the category where the catalog knows nothing about the type", () => {
    expect(previewTitle(job({jobType: "not.a.known.job", category: JobExecutionCategory.CONTACT})))
      .toContain("Not A Known Job")
    expect(previewTitle(job({jobType: "", category: JobExecutionCategory.COHORT}))).toBe("Cohort job")
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
    // The api always names the entity now; the fallback is for a row that does not.
    expect(relatedEntityLabel({type: "event_sign_up", id: 3} as JobRelatedEntity))
      .toBe("Event Sign Up #3")
  })

  it("reads the counts off the stats rather than off the loaded page", () => {
    const stats = {
      avgSuccessDurationSeconds: 1, deadCount: 2, deadSinceStartup: 0, failedCount: 3,
      failedSinceStartup: 0, queuedCount: 4, recoveriesSinceStartup: 0, runningCount: 5,
      skippedCount: 1, successCount: 6, totalCount: 21,
    }

    expect(statusCounts(stats)).toEqual({
      QUEUED: 4, RUNNING: 5, SUCCESS: 6, SKIPPED: 1, FAILED: 3, DEAD: 2,
    })
    expect(successRate({...stats, totalCount: 20})).toBe(30)
    // While the stats are still loading the chip row stays mounted, reading zeroes.
    expect(statusCounts(null)).toEqual({QUEUED: 0, RUNNING: 0, SUCCESS: 0, SKIPPED: 0, FAILED: 0, DEAD: 0})
    expect(successRate(null)).toBe(0)
    expect(successRate({...stats, totalCount: 0})).toBe(0)
  })

  it("links what a job concerns to its own page, where it has one", () => {
    expect(relatedEntityLink({type: "EVENT", id: 4, label: "LAN"})).toBe("/events/4")
    expect(relatedEntityLink({type: "COHORT", id: 2, label: "Sitecie (BREVO LIST)"})).toBe("/management/platforms/brevo")
    expect(relatedEntityLink({type: "USER", id: 7, label: "Ada Lovelace (@ada.l)"})).toBe("/management/users/7")
    expect(relatedEntityLink({type: "CONTRIBUTION_PERIOD", id: 3, label: "2026-2027"})).toBeNull()
    expect(relatedEntityLink({type: "EVENT", id: null, label: "Event"})).toBeNull()
  })

  it("offers every filter the api declares, not only the ones on screen", () => {
    expect(categoryOptions()).toEqual([
      {key: "calendar", label: "Calendar"},
      {key: "contact", label: "Contact"},
      {key: "cohort", label: "Cohort"},
      {key: "discord", label: "Discord"},
      {key: "email", label: "Email"},
      {key: "other", label: "Other"},
    ])
    expect(statusOptions().map(one => one.key))
      .toEqual(["QUEUED", "RUNNING", "SUCCESS", "SKIPPED", "FAILED", "DEAD"])
  })
})
