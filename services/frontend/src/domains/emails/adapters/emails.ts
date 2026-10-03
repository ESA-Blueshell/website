/**
 * Emails domain adapter: the only file in this domain that imports from @/services/api
 * (frontend ADR-002). Everything else imports from here.
 */
import {
  type EmailDetail,
  findEmail,
  getStats1,
  list1,
  previewSentEmail,
  render,
  resend,
  retry1,
  type Email,
  type EmailStats as EmailStatsDto,
  EmailDeliveryStatus,
} from "@/services/api"
import type {PageOf, PageQuery} from "@/composables/usePagedTable"
import type {RenderedEmailPreview} from "@/composables/useEmailPreview"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import {refusalReader} from "@/utils/refusals"

// Re-exported so this adapter still answers for its own surface, while the type has one definition.
export type {Refused}

export type SentEmail = Email
export type {EmailDetail}
export type EmailStats = EmailStatsDto
export {EmailDeliveryStatus}

/**
 * The api declares no refusal codes for this module, so a refused email write reads as whatever
 * detail it carried; the sentence map stays empty rather than inventing codes it does not send.
 */
const {refusable} = refusalReader({})

/** What the manager narrows a page of emails by. None set is the whole outbox. */
export interface EmailFilter {
  deliveryStatus?: EmailDeliveryStatus
}

/** Newest first, which is the order an outbox is read in. */
const EMAIL_SORT = ["createdAt,desc"]

/**
 * One page of the outbox.
 *
 * A page that could not be read answers as an empty one: the manager's job is to show what the
 * api will say, and a table of nothing is the honest reading of an api that said nothing.
 */
export async function loadEmailPage(
  query: PageQuery,
  filter: EmailFilter = {},
): Promise<PageOf<SentEmail>> {
  const page = await readOr(list1({
    query: {
      page: query.page,
      size: query.size,
      sort: EMAIL_SORT,
      ...(filter.deliveryStatus ? {deliveryStatus: filter.deliveryStatus} : {}),
      ...(query.search ? {search: query.search} : {}),
    },
  }), null)
  if (!page) return {rows: [], totalElements: 0, totalPages: 1}

  return {
    rows: page.content ?? [],
    totalElements: page.page?.totalElements ?? 0,
    totalPages: Math.max(1, page.page?.totalPages ?? 1),
  }
}

/** The counts behind the stats panel, or nothing where they could not be read — it is supplementary. */
export const loadEmailStats = (): Promise<EmailStats | null> => readOr(getStats1(), null)

/**
 * Sends a failed email again.
 *
 * Answers with the api's own words when it says no, so a refused retry does not read as one that
 * worked and changed nothing.
 */
export const retrySend = (id: number): Promise<{ok: true} | Refused> =>
  refusable(retry1({path: {id}}), "That email could not be sent again.")

/** Makes the email again for the person's current address, as a new email linked to this one. */
export const resendEmail = (id: number): Promise<{ok: true} | Refused> =>
  refusable(resend({path: {id}}), "That email could not be made again.")

/**
 * A sent email read back. The api renders it and strips its urls before answering, so what
 * arrives here has no link in it to follow. Nothing where it could not be rendered, which is what
 * the preview dialog turns into its own sentence.
 */
export const readSentEmail = (id: number): Promise<RenderedEmailPreview | null> =>
  readOr(previewSentEmail({path: {id}}), null)

/** One email and the emails made again from it, or nothing where it could not be read. */
export const readEmail = (id: number): Promise<EmailDetail | null> => readOr(findEmail({path: {id}}), null)

/** An editor's message as the email it becomes, rendered by the api the way a send renders it. */
export async function renderWritten(subject: string, message: string, recipientName?: string): Promise<RenderedEmailPreview | null> {
  const answered = await readOr(render({body: {subject, message, recipientName: recipientName ?? "Member"}}), null)
  return answered ? {subject: answered.subject, html: answered.html, recipientEmail: "", recipientName: recipientName ?? ""} : null
}
