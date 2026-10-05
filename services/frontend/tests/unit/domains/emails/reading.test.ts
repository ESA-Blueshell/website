import {describe, expect, it} from "vitest"
import type {EmailStats, SentEmail} from "@/domains/emails"
import {canResend, canRetry, emailTypeLabel, sentFacts, stateKindOf, statusWord, timelineOf} from "@/domains/emails"

const email = (fields: Partial<SentEmail>): SentEmail => fields as SentEmail

const stats = (fields: Partial<EmailStats>): EmailStats => ({
  bouncedCount: 0,
  deliveredCount: 0,
  failedCount: 0,
  openedCount: 0,
  queuedCount: 0,
  sentCount: 0,
  totalCount: 0,
  ...fields,
})

describe("email reading", () => {
  it("retries a failed send its job can run again, and resends anything that left the queue", () => {
    expect(canRetry(email({id: 1, deliveryStatus: "FAILED", jobExecutionId: 9}))).toBe(true)
    expect(canRetry(email({id: 1, deliveryStatus: "FAILED"}))).toBe(false)
    expect(canRetry(email({id: 1, deliveryStatus: "BOUNCED", jobExecutionId: 9}))).toBe(false)
    expect(canResend(email({id: 1, deliveryStatus: "BOUNCED", jobExecutionId: 9}))).toBe(true)
    expect(canResend(email({id: 1, deliveryStatus: "SENT", jobExecutionId: 9}))).toBe(true)
    expect(canResend(email({id: 1, deliveryStatus: "QUEUED", jobExecutionId: 9}))).toBe(false)
    expect(canResend(email({id: 1, deliveryStatus: "BOUNCED"}))).toBe(false)
  })

  it("names a status and its standing, and an email's kind from its type", () => {
    expect(statusWord("QUEUED")).toBe("Queued")
    expect(statusWord(null)).toBe("Unknown")
    expect(stateKindOf("OPENED")).toBe("in-sync")
    expect(stateKindOf("DELIVERED")).toBe("in-sync")
    expect(stateKindOf("BOUNCED")).toBe("unreachable")
    expect(stateKindOf("QUEUED")).toBe("not-created")
    expect(stateKindOf("SENT")).toBe("not-compared")
    expect(emailTypeLabel("email.contribution-reminder")).toBe("Contribution reminder")
    expect(emailTypeLabel("auth.security-notification")).toBe("Security notification")
    expect(emailTypeLabel(null)).toBe("Email")
  })

  it("tells what happened, oldest first, with where it went wrong", () => {
    expect(timelineOf(email({
      createdAt: "2026-09-29T09:39:00Z", sentAt: "2026-09-29T09:40:00Z", deliveryStatus: "BOUNCED", updatedAt: "2026-09-29T09:41:00Z",
    }))).toEqual([
      {what: "Queued", at: "2026-09-29T09:39:00Z", wrong: false},
      {what: "Sent", at: "2026-09-29T09:40:00Z", wrong: false},
      {what: "Bounced", at: "2026-09-29T09:41:00Z", wrong: true},
    ])
    expect(timelineOf(email({createdAt: "a", sentAt: "b", deliveredAt: "c", openedAt: "d", deliveryStatus: "OPENED"})).map((one) => one.what))
      .toEqual(["Queued", "Sent", "Delivered", "Opened"])
    expect(timelineOf(email({deliveryStatus: "FAILED", createdAt: "a"})).at(-1)).toEqual({what: "Failed", at: "a", wrong: true})
    expect(timelineOf(email({deliveryStatus: "FAILED"})).at(-1)?.at).toBe("")
  })

  it("counts what waits, what arrived of what was sent, and what needs a look", () => {
    expect(sentFacts(stats({totalCount: 110, queuedCount: 10, deliveredCount: 60, openedCount: 30, bouncedCount: 4, failedCount: 2})))
      .toEqual({queued: 10, sent: 100, delivered: 90, opened: 30, needsLook: 6, bounced: 4, failed: 2})
    expect(sentFacts(null)).toEqual({queued: 0, sent: 0, delivered: 0, opened: 0, needsLook: 0, bounced: 0, failed: 0})
  })
})
