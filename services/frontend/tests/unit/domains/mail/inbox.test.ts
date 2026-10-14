import {describe, expect, it} from "vitest"
import {InboxState, followsOf, inboxStateWord} from "@/domains/mail"

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
})
