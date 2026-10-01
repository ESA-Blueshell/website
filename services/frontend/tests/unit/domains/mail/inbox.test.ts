import {describe, expect, it} from "vitest"
import {ConversationKind, InboxState, authorOf, conversationSummary, followsOf, inboxStateWord} from "@/domains/mail"

describe("the inbox", () => {
  it("names a state, an automatic reply apart, and what a message follows", () => {
    expect(inboxStateWord({state: InboxState.NEW, automatic: false})).toBe("New")
    expect(inboxStateWord({state: InboxState.REPLIED, automatic: false})).toBe("Replied")
    expect(inboxStateWord({state: InboxState.HANDLED, automatic: false})).toBe("Handled")
    expect(inboxStateWord({state: InboxState.NEW, automatic: true})).toBe("Automatic reply")

    const kind = (type: string) => (type === "email.contribution-reminder" ? "Contribution reminder" : type)
    expect(followsOf({answers: {emailId: 9, emailType: "email.contribution-reminder"}, toAddress: null}, kind)).toBe("Answers contribution reminder")
    expect(followsOf({answers: null, toAddress: "partners@esa-blueshell.nl"}, kind)).toBe("To partners@")
    expect(followsOf({answers: null, toAddress: null}, kind)).toBeNull()
  })

  it("says who a conversation is between and who wrote each step", () => {
    const message = {senderName: "Lars Mulder", fromName: "Lars", fromAddress: "lars@example.com"}
    const step = (kind: ConversationKind, fields: Record<string, unknown> = {}) => ({kind, fromAddress: null, writtenByName: null, ...fields})
    expect(conversationSummary({message: {...message, id: 1, subject: "x", receivedAt: "", state: InboxState.NEW, automatic: false},
      items: [step(ConversationKind.SENT), step(ConversationKind.RECEIVED)]})).toBe("Lars Mulder and the site, 2 emails")
    expect(conversationSummary({message: {fromAddress: "a@x.nl", id: 1, subject: "x", receivedAt: "", state: InboxState.NEW, automatic: false},
      items: [step(ConversationKind.RECEIVED)]})).toBe("a@x.nl and the site, 1 email")

    expect(authorOf(step(ConversationKind.SENT), message)).toBe("The site")
    expect(authorOf(step(ConversationKind.REPLY, {writtenByName: "Alice Board"}), message)).toBe("Alice Board, from the site")
    expect(authorOf(step(ConversationKind.REPLY), message)).toBe("The board, from the site")
    expect(authorOf(step(ConversationKind.RECEIVED, {fromAddress: "lars@example.com"}), message)).toBe("Lars Mulder")
    expect(authorOf(step(ConversationKind.RECEIVED, {fromAddress: "other@example.com"}), message)).toBe("other@example.com")
    expect(authorOf(step(ConversationKind.RECEIVED), message)).toBe("Unknown sender")
  })
})
