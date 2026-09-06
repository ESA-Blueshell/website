/**
 * How a sent email reads: the words on a row, the colour behind it, and what the rates mean.
 *
 * Knowledge about emails rather than about a page, so it sits in the domain and can be checked
 * without mounting anything.
 */
import type {EmailStats, SentEmail} from "./adapters/emails"
import {EmailDeliveryStatus} from "./adapters/emails"

type Status = EmailDeliveryStatus | string | null | undefined

export function statusColor(status?: Status): string {
  if (status === "DELIVERED" || status === "OPENED") return "success"
  if (status === "FAILED" || status === "BOUNCED") return "error"
  if (status === "SENT") return "info"
  if (status === "PENDING") return "warning"
  return "secondary"
}

export function rowStatusClass(status?: Status): string {
  if (status === "DELIVERED" || status === "OPENED") return "email-row--success"
  if (status === "FAILED" || status === "BOUNCED") return "email-row--failed"
  if (status === "SENT") return "email-row--sent"
  if (status === "PENDING") return "email-row--pending"
  return ""
}

/**
 * An email that can be sent again: one that failed and has the job behind it to run. A failure
 * with no job recorded has nothing to retry, so no button is offered for it.
 */
export function canRetry(email: SentEmail): boolean {
  return email.id != null && email.deliveryStatus === "FAILED" && email.jobExecutionId != null
}

/** An opened email was delivered, so it counts towards delivery as well as towards opens. */
export function deliveryRate(stats: EmailStats | null): number {
  const total = stats?.totalCount ?? 0
  if (!stats || total === 0) return 0
  return Math.round(((stats.deliveredCount ?? 0) + (stats.openedCount ?? 0)) / total * 100)
}

export function openRate(stats: EmailStats | null): number {
  const total = stats?.totalCount ?? 0
  if (!stats || total === 0) return 0
  return Math.round((stats.openedCount ?? 0) / total * 100)
}

/**
 * The chip counts. These count the page on screen rather than the outbox, unlike the total
 * beside them, which is every email the filter matches.
 */
export function statusCounts(emails: SentEmail[]): Record<EmailDeliveryStatus, number> {
  const counts = {
    [EmailDeliveryStatus.PENDING]: 0,
    [EmailDeliveryStatus.SENT]: 0,
    [EmailDeliveryStatus.DELIVERED]: 0,
    [EmailDeliveryStatus.OPENED]: 0,
    [EmailDeliveryStatus.BOUNCED]: 0,
    [EmailDeliveryStatus.FAILED]: 0,
  }
  for (const email of emails) {
    const status = email.deliveryStatus
    if (status && status in counts) counts[status] += 1
  }
  return counts
}

/** One option in a filter picker: what it says, and the value it filters by. */
export interface FilterOption {
  title: string
  value: string
}

const titleCase = (value: string): string =>
  value.charAt(0).toUpperCase() + value.slice(1).toLowerCase()

/**
 * The statuses offered, built from the generated enum rather than from a list copied into the
 * page, so a status the api adds is selectable without anyone noticing it was added.
 */
export const statusOptions = (): FilterOption[] => [
  {title: "All statuses", value: "all"},
  ...Object.values(EmailDeliveryStatus).map(value => ({title: titleCase(value), value})),
]
