import {type InboxEntry, InboxState} from "@/services/api"

/** What a received message's state reads as; an automatic reply is kept apart from what needs an answer. */
export const inboxStateWord = (entry: Pick<InboxEntry, "state" | "automatic">): string =>
  entry.automatic ? "Automatic reply" : ({[InboxState.NEW]: "New", [InboxState.REPLIED]: "Replied", [InboxState.HANDLED]: "Handled"})[entry.state]

/** What a message follows: the site's email it answers, else the address it was sent to. */
export function followsOf(entry: Pick<InboxEntry, "answers" | "toAddress">, kindOf: (type: string) => string): string | null {
  if (entry.answers) return `Answers ${kindOf(entry.answers.emailType).toLowerCase()}`
  const to = entry.toAddress?.split("@")[0]
  return to ? `To ${to}@` : null
}
