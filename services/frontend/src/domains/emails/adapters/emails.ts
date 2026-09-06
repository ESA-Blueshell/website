/**
 * Emails domain adapter: the only file in this domain that imports from @/services/api
 * (frontend ADR-002). Everything else imports from here.
 */
import {
  getStats1,
  list1,
  previewSentEmail,
  retry1,
  type Email,
  type EmailStats as EmailStatsDto,
  EmailDeliveryStatus,
} from "@/services/api"
import type {PageOf, PageQuery} from "@/composables/usePagedTable"
import type {RenderedEmailPreview} from "@/composables/useEmailPreview"
import type {Refused} from "@/types/api"
import {refusalReader} from "@/utils/refusals"

// Re-exported so this adapter still answers for its own surface, while the type has one definition.
export type {Refused}

export type SentEmail = Email
export type EmailStats = EmailStatsDto
export {EmailDeliveryStatus}

/**
 * The api declares no refusal codes for this module, so a refused email write reads as whatever
 * detail it carried; the sentence map stays empty rather than inventing codes it does not send.
 */
const {reasonFor} = refusalReader({})

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
  const res = await list1({
    query: {
      page: query.page,
      size: query.size,
      sort: EMAIL_SORT,
      ...(filter.deliveryStatus ? {deliveryStatus: filter.deliveryStatus} : {}),
      ...(query.search ? {search: query.search} : {}),
    },
  })
  if (res.error || !res.data) return {rows: [], totalElements: 0, totalPages: 1}

  const rows = res.data.content ?? []
  return {
    rows,
    totalElements: res.data.page?.totalElements ?? 0,
    totalPages: Math.max(1, res.data.page?.totalPages ?? 1),
  }
}

/** The counts behind the stats panel, or nothing where they could not be read — it is supplementary. */
export async function loadEmailStats(): Promise<EmailStats | null> {
  const res = await getStats1()
  return res.error ? null : res.data ?? null
}

/**
 * Sends a failed email again.
 *
 * Answers with the api's own words when it says no. Retrying used to fall into an empty catch,
 * so a refused retry looked exactly like a successful one that changed nothing.
 */
export async function retrySend(id: number): Promise<{ok: true} | Refused> {
  const res = await retry1({path: {id}})
  if (res.error || !res.data) {
    return {ok: false, reason: reasonFor(res.error, "That email could not be sent again.")}
  }
  return {ok: true}
}

/**
 * A sent email read back. The api renders it and strips its urls before answering, so what
 * arrives here has no link in it to follow. Nothing where it could not be rendered, which is what
 * the preview dialog turns into its own sentence.
 */
export async function readSentEmail(id: number): Promise<RenderedEmailPreview | null> {
  const res = await previewSentEmail({path: {id}})
  return res.error ? null : res.data ?? null
}
