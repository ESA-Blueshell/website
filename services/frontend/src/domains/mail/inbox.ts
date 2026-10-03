import {type Conversation, type ConversationItem, ConversationKind, type InboxEntry, InboxState} from "@/services/api"

/** What a received message's state reads as; an automatic reply is kept apart from what needs an answer. */
export const inboxStateWord = (entry: Pick<InboxEntry, "state" | "automatic">): string =>
  entry.automatic ? "Automatic reply" : ({[InboxState.NEW]: "New", [InboxState.REPLIED]: "Replied", [InboxState.HANDLED]: "Handled"})[entry.state]

/** What a message follows: the site's email it answers, else the address it was sent to. */
export function followsOf(entry: Pick<InboxEntry, "answers" | "toAddress">, kindOf: (type: string) => string): string | null {
  if (entry.answers) return `Answers ${kindOf(entry.answers.emailType).toLowerCase()}`
  const to = entry.toAddress?.split("@")[0]
  return to ? `To ${to}@` : null
}

/** Who a conversation is between and how long it is, such as "Lars Mulder and the site, 2 emails". */
export function conversationSummary(conversation: Pick<Conversation, "message" | "items">): string {
  const {message, items} = conversation
  const who = message.senderName ?? message.fromName ?? message.fromAddress
  return `${who} and the site, ${items.length} ${items.length === 1 ? "email" : "emails"}`
}

/** Who one step of a conversation is from: the site, the person who wrote in, or the board member who replied. */
export function authorOf(item: Pick<ConversationItem, "kind" | "fromAddress" | "writtenByName">, message: Pick<InboxEntry, "senderName" | "fromName" | "fromAddress">): string {
  if (item.kind === ConversationKind.SENT) return "The site"
  if (item.kind === ConversationKind.REPLY) return item.writtenByName ? `${item.writtenByName}, from the site` : "The board, from the site"
  return item.fromAddress === message.fromAddress ? message.senderName ?? message.fromName ?? message.fromAddress : item.fromAddress ?? "Unknown sender"
}
