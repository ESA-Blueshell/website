/**
 * The email manager's reading rules. Every one of these was previously reachable only by driving
 * a browser, which is why `email-manager.spec.ts` asserted 63 things and none of them was a rate,
 * a colour or the retry predicate.
 */
import {describe, expect, it} from "vitest"
import type {EmailStats, SentEmail} from "@/domains/emails"
import {
  canRetry,
  deliveryRate,
  openRate,
  rowStatusClass,
  statusColor,
  statusCounts,
  statusOptions,
} from "@/domains/emails"

const email = (fields: Partial<SentEmail>): SentEmail => fields as SentEmail

const stats = (fields: Partial<EmailStats>): EmailStats => ({
  bouncedCount: 0,
  deliveredCount: 0,
  failedCount: 0,
  openedCount: 0,
  pendingCount: 0,
  sentCount: 0,
  totalCount: 0,
  ...fields,
})

describe("email reading", () => {
  it("gives each delivery status its colour and its row class", () => {
    expect(statusColor("DELIVERED")).toBe("success")
    expect(statusColor("OPENED")).toBe("success")
    expect(statusColor("BOUNCED")).toBe("error")
    expect(statusColor("FAILED")).toBe("error")
    expect(statusColor("SENT")).toBe("info")
    expect(statusColor("PENDING")).toBe("warning")
    expect(statusColor(undefined)).toBe("secondary")

    expect(rowStatusClass("OPENED")).toBe("email-row--success")
    expect(rowStatusClass("BOUNCED")).toBe("email-row--failed")
    expect(rowStatusClass("SENT")).toBe("email-row--sent")
    expect(rowStatusClass("PENDING")).toBe("email-row--pending")
    expect(rowStatusClass(undefined)).toBe("")
  })

  it("offers a retry only where there is a failed send with a job behind it", () => {
    expect(canRetry(email({id: 1, deliveryStatus: "FAILED", jobExecutionId: 9}))).toBe(true)
    // A failure with no job recorded has nothing to run again.
    expect(canRetry(email({id: 1, deliveryStatus: "FAILED"}))).toBe(false)
    expect(canRetry(email({id: 1, deliveryStatus: "BOUNCED", jobExecutionId: 9}))).toBe(false)
    expect(canRetry(email({deliveryStatus: "FAILED", jobExecutionId: 9}))).toBe(false)
  })

  it("counts an opened email as delivered as well as opened", () => {
    const counted = stats({totalCount: 10, deliveredCount: 4, openedCount: 3})

    expect(deliveryRate(counted)).toBe(70)
    expect(openRate(counted)).toBe(30)
  })

  it("reports no rate rather than dividing by nothing", () => {
    expect(deliveryRate(null)).toBe(0)
    expect(openRate(null)).toBe(0)
    expect(deliveryRate(stats({totalCount: 0, deliveredCount: 5}))).toBe(0)
    expect(openRate(stats({totalCount: 0, openedCount: 5}))).toBe(0)
  })

  it("counts the statuses on the page that is loaded", () => {
    const counts = statusCounts([
      email({deliveryStatus: "SENT"}),
      email({deliveryStatus: "SENT"}),
      email({deliveryStatus: "OPENED"}),
      email({}),
    ])

    expect(counts).toEqual({
      PENDING: 0, SENT: 2, DELIVERED: 0, OPENED: 1, BOUNCED: 0, FAILED: 0,
    })
  })

  it("offers every status the api declares", () => {
    expect(statusOptions()).toEqual([
      {title: "All statuses", value: "all"},
      {title: "Pending", value: "PENDING"},
      {title: "Sent", value: "SENT"},
      {title: "Delivered", value: "DELIVERED"},
      {title: "Opened", value: "OPENED"},
      {title: "Bounced", value: "BOUNCED"},
      {title: "Failed", value: "FAILED"},
    ])
  })
})
