/** The catch-all mailbox as the board reads it. */
import {
  type AnsweredEmail,
  findInbox,
  findInboxCounts,
  type InboxCounts,
  type InboxEntry,
  InboxSort,
  InboxState,
} from "@/services/api"
import type {PageOf, PageQuery} from "@/composables/usePagedTable"
import {readOr} from "@/utils/answers"

export type {AnsweredEmail, InboxCounts, InboxEntry}
export {InboxState}

/** What each of the table's columns is ordered by on the server. Who handled a message is a name looked up after, so it has none. */
const INBOX_SORTS: Record<string, InboxSort> = {received: InboxSort.RECEIVED, from: InboxSort.FROM, what: InboxSort.SUBJECT, state: InboxSort.STATE}

/** One page, newest first unless the reader ordered it; an empty one where it could not be read. */
export async function loadInboxPage(query: PageQuery): Promise<PageOf<InboxEntry>> {
  const sort = query.sort && INBOX_SORTS[query.sort.key] ? {sort: INBOX_SORTS[query.sort.key], descending: query.sort.descending} : {}
  const page = await readOr(findInbox({query: {page: query.page, ...(query.search ? {search: query.search} : {}), ...sort}}), null)
  if (!page) return {rows: [], totalElements: 0, totalPages: 1}
  return {rows: page.content ?? [], totalElements: page.page?.totalElements ?? 0, totalPages: Math.max(1, page.page?.totalPages ?? 1)}
}

/** What the facts count, or nothing where they could not be read. */
export const readInboxCounts = (): Promise<InboxCounts | null> => readOr(findInboxCounts(), null)
