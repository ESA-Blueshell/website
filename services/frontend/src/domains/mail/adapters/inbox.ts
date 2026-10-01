/** The catch-all mailbox as the board reads it. */
import {
  type AnsweredEmail,
  type Conversation,
  type ConversationItem,
  ConversationKind,
  type EarlierMail,
  findConversation,
  findInbox,
  findInboxCounts,
  type InboxCounts,
  type InboxEntry,
  InboxState,
  markMessageHandled,
  replyToMessage,
} from "@/services/api"
import type {PageOf, PageQuery} from "@/composables/usePagedTable"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"
import {readOr} from "@/utils/answers"
import {refusable} from "../refusals"

export type {AnsweredEmail, Conversation, ConversationItem, EarlierMail, InboxCounts, InboxEntry}
export {ConversationKind, InboxState}

/** One page, newest first; an empty one where it could not be read. */
export async function loadInboxPage(query: PageQuery): Promise<PageOf<InboxEntry>> {
  const page = await readOr(findInbox({query: {page: query.page, ...(query.search ? {search: query.search} : {})}}), null)
  if (!page) return {rows: [], totalElements: 0, totalPages: 1}
  return {rows: page.content ?? [], totalElements: page.page?.totalElements ?? 0, totalPages: Math.max(1, page.page?.totalPages ?? 1)}
}

/** What the facts count, or nothing where they could not be read. */
export const readInboxCounts = (): Promise<InboxCounts | null> => readOr(findInboxCounts(), null)

/** One message with its conversation, or nothing where it could not be read. */
export const readConversation = (id: number): Promise<Conversation | null> => readOr(findConversation({path: {id}}), null)

/** Sends the reply in the conversation's thread and marks the message replied. */
export const sendReply = (id: number, message: string, replyTo: string | null): Promise<Saved<Conversation> | Refused> =>
  refusable(replyToMessage({path: {id}, body: {message, replyTo}}), "The reply could not be sent.")

export const markHandled = (id: number): Promise<Saved<Conversation> | Refused> =>
  refusable(markMessageHandled({path: {id}}), "The message could not be marked handled.")
