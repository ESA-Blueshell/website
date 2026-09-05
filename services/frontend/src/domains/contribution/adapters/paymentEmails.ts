/**
 * Contribution domain adapter — the only file in this domain that imports from
 * `@/services/api` (frontend ADR-002). Everything else, dialogs included, comes through here.
 *
 * The return types are inferred rather than named: the generated client answers a refusal
 * instead of throwing, and an alias written by hand here loses the error half of that union,
 * which is precisely what the callers read.
 */
import {previewBulkContributionEmail, readContributionEmail, sendPaymentEmails} from "@/services/api"

/** The query the endpoint documents, not a restatement of it that would accept more. */
export type ReadEmailQuery = NonNullable<Parameters<typeof readContributionEmail>[0]>["query"]
export type SendPaymentEmailsBody = NonNullable<Parameters<typeof sendPaymentEmails>[0]>["body"]

/** What the send would do to a selection, before anybody is written to. */
export function readSelection(contributionPeriodId: number, userIds: number[]) {
  return previewBulkContributionEmail({body: {contributionPeriodId, userIds}})
}

/** One member's email as they would receive it. */
export function readOneEmail(query: ReadEmailQuery) {
  return readContributionEmail({query})
}

export function sendTheEmails(body: SendPaymentEmailsBody) {
  return sendPaymentEmails({body})
}
