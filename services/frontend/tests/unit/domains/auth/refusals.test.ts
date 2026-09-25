import {describe, expect, it} from "vitest"
import {codeOf, needsStepUp, reasonFor} from "@/domains/auth/refusals"
import {describeSecurityEvent} from "@/domains/auth/securityEvents"

describe("account security refusals", () => {
  it.each([
    ["WrongPassword", "That password is not right."],
    ["ChallengeExpired", "That took too long, or too many codes were wrong. Sign in with your password again."],
    ["CodeLimitReached", "Too many wrong codes for this account. Try again in fifteen minutes."],
    ["StepUpRequired", "Confirm it is you first."],
    ["NothingToConfirm", "Start setting up two-factor again."],
    ["TwoFactorRequired", "Holding a board, treasurer or admin role requires two-factor, so it can be replaced but not turned off."],
    ["TwoFactorOff", "Two-factor is not on for this account."],
    ["NotLocked", "That account is not locked."],
    ["NotAwaitingReenrolment", "That person is not waiting to set up two-factor again."],
    ["OwnAccount", "Another admin has to do this for you."],
    ["ReenrolmentRequired", "An admin reset your two-factor. Sign in with the re-enrolment link in your email."],
    ["AccountLocked", "This account is locked. Contact the board to have it unlocked."],
    ["EmailTaken", "That address belongs to another account."],
  ])("reads %s as a sentence", (code, sentence) => {
    expect(reasonFor({code}, "fallback")).toBe(sentence)
  })

  it("counts the tries a wrong code leaves", () => {
    expect(reasonFor({code: "WrongCode"}, "x")).toBe("That code is not right.")
    expect(reasonFor({code: "WrongCode", triesLeft: 1}, "x")).toBe("That code is not right. 1 try left.")
    expect(reasonFor({code: "WrongCode", triesLeft: 3}, "x")).toBe("That code is not right. 3 tries left.")
  })

  it("tells a step-up refusal apart, thrown or answered", () => {
    expect(codeOf({code: "OwnAccount"})).toBe("OwnAccount")
    expect(codeOf(null)).toBeUndefined()
    expect(needsStepUp({code: "StepUpRequired"})).toBe(true)
    expect(needsStepUp({response: {data: {code: "StepUpRequired"}}})).toBe(true)
    expect(needsStepUp({code: "WrongPassword"})).toBe(false)
    expect(needsStepUp(null)).toBe(false)
  })

  it("names who did something when it was not the person", () => {
    expect(describeSecurityEvent({kind: "ACCOUNT_LOCKED", actorKind: "PERSON"} as never)).toBe("Account locked")
    expect(describeSecurityEvent({kind: "BREAK_GLASS", actorKind: "OPERATOR"} as never)).toBe("Break-glass command used, by an operator")
    expect(describeSecurityEvent({kind: "ACCOUNT_UNLOCKED", actorKind: "PERSON", actorName: "Ro Ot"} as never))
      .toBe("Account unlocked, by Ro Ot")
    expect(describeSecurityEvent({kind: "SOMETHING_NEW", actorKind: "SYSTEM"} as never)).toBe("SOMETHING_NEW")
  })
})
