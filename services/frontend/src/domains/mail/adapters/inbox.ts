/** The catch-all mailbox as the board reads it. */
import {
  type AnsweredEmail,
  findInbox,
  findInboxCounts,
  type InboxCounts,
  type InboxEntry,
  InboxState,
} from "@/services/api"
import type {PageOf, PageQuery} from "@/composables/usePagedTable"
import {readOr} from "@/utils/answers"

export type {AnsweredEmail, InboxCounts, InboxEntry}
export {InboxState}

/** One page, newest first; an empty one where it could not be read. */
export async function loadInboxPage(query: PageQuery): Promise<PageOf<InboxEntry>> {
  const page = await readOr(findInbox({query: {page: query.page, ...(query.search ? {search: query.search} : {})}}), null)
  if (!page) return {rows: [], totalElements: 0, totalPages: 1}
  return {rows: page.content ?? [], totalElements: page.page?.totalElements ?? 0, totalPages: Math.max(1, page.page?.totalPages ?? 1)}
}

/** What the facts count, or nothing where they could not be read. */
export const readInboxCounts = (): Promise<InboxCounts | null> => readOr(findInboxCounts(), null)
