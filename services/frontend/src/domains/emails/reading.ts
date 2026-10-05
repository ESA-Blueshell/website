/**
 * How a sent email reads: what it is called, where it stands, what happened to it, and what it
 * may do next.
 *
 * Knowledge about emails rather than about a page, so it sits in the domain and can be checked
 * without mounting anything.
 */
import type {EmailStats, SentEmail} from "./adapters/emails"
import {EmailDeliveryStatus} from "./adapters/emails"
import type {StateKind} from "@/components/island/StateMark.vue"

type Status = EmailDeliveryStatus | string | null | undefined

/**
 * An email that can be sent again: one that failed and has the job behind it to run. A failure
 * with no job recorded has nothing to retry, so no button is offered for it.
 */
export function canRetry(email: SentEmail): boolean {
  return email.id != null && email.deliveryStatus === "FAILED" && email.jobExecutionId != null
}

/** An email a job wrote can be made again for the person's current address, once it has left the queue. */
export function canResend(email: SentEmail): boolean {
  return email.id != null && email.jobExecutionId != null && email.deliveryStatus !== EmailDeliveryStatus.QUEUED
}

const titleCase = (value: string): string =>
  value.charAt(0).toUpperCase() + value.slice(1).toLowerCase()

/** What a status is called, such as Queued or Bounced. */
export const statusWord = (status?: Status): string => (status ? titleCase(status) : "Unknown")

/** How a status stands against arriving: in sync once it arrived, unreachable when it did not. */
export function stateKindOf(status?: Status): StateKind {
  if (status === "DELIVERED" || status === "OPENED") return "in-sync"
  if (status === "BOUNCED" || status === "FAILED") return "unreachable"
  if (status === "QUEUED") return "not-created"
  return "not-compared"
}

/** What kind of email it is, from its type: email.contribution-reminder reads as Contribution reminder. */
export function emailTypeLabel(type?: string | null): string {
  const last = (type ?? "").split(".").pop() ?? ""
  return last ? titleCase(last.replace(/-/g, " ")) : "Email"
}

/** One moment in an email's life, oldest first. */
export interface EmailMoment {
  what: string
  at: string
  wrong: boolean
}

/** What happened to an email: queued, sent, delivered, opened, or where it went wrong. */
export function timelineOf(email: SentEmail): EmailMoment[] {
  const moments: EmailMoment[] = []
  if (email.createdAt) moments.push({what: "Queued", at: email.createdAt, wrong: false})
  if (email.sentAt) moments.push({what: "Sent", at: email.sentAt, wrong: false})
  if (email.deliveredAt) moments.push({what: "Delivered", at: email.deliveredAt, wrong: false})
  if (email.openedAt) moments.push({what: "Opened", at: email.openedAt, wrong: false})
  if (email.deliveryStatus === "BOUNCED" || email.deliveryStatus === "FAILED") {
    moments.push({what: statusWord(email.deliveryStatus), at: email.updatedAt ?? email.createdAt ?? "", wrong: true})
  }
  return moments
}

/** The three facts over every email: waiting, arrived, and needing a look. */
export function sentFacts(stats: EmailStats | null) {
  const queued = stats?.queuedCount ?? 0
  const sent = Math.max(0, (stats?.totalCount ?? 0) - queued)
  const bounced = stats?.bouncedCount ?? 0
  const failed = stats?.failedCount ?? 0
  const arrived = (stats?.deliveredCount ?? 0) + (stats?.openedCount ?? 0)
  return {
    queued,
    sent,
    delivered: sent === 0 ? 0 : Math.round(arrived / sent * 100),
    opened: sent === 0 ? 0 : Math.round((stats?.openedCount ?? 0) / sent * 100),
    needsLook: bounced + failed,
    bounced,
    failed,
  }
}
