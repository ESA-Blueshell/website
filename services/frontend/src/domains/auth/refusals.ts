// TWIN: `auth/domain/AccountSecurityRefusal.kt` declares the codes and their facts. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

interface RefusalBody extends RefusalCode {
  triesLeft?: number
}

const sentences: Record<string, (r: RefusalBody) => string> = {
  WrongPassword: () => "That password is not right.",
  WrongCode: r => r.triesLeft == null
    ? "That code is not right."
    : `That code is not right. ${r.triesLeft} ${r.triesLeft === 1 ? "try" : "tries"} left.`,
  ChallengeExpired: () => "That took too long, or too many codes were wrong. Sign in with your password again.",
  CodeLimitReached: () => "Too many wrong codes for this account. Try again in fifteen minutes.",
  StepUpRequired: () => "Confirm it is you first.",
  NothingToConfirm: () => "Start setting up two-factor again.",
  TwoFactorRequired: () => "Holding a board, treasurer or admin role requires two-factor, so it can be replaced but not turned off.",
  TwoFactorOff: () => "Two-factor is not on for this account.",
  NotLocked: () => "That account is not locked.",
  NotAwaitingReenrolment: () => "That person is not waiting to set up two-factor again.",
  OwnAccount: () => "Another admin has to do this for you.",
  ReenrolmentRequired: () => "An admin reset your two-factor. Sign in with the re-enrolment link in your email.",
  AccountLocked: () => "This account is locked. Contact the board to have it unlocked.",
  EmailTaken: () => "That address belongs to another account.",
}

export const {sentenceFor, reasonFor} = refusalReader(sentences)

export const codeOf = (body: unknown): string | undefined => (body as RefusalCode | null | undefined)?.code

/** Whether a refused write, thrown or answered, asks for a step-up first. */
export const needsStepUp = (error: unknown): boolean => {
  const thrown = error as {code?: string; response?: {data?: RefusalCode}} | null | undefined
  return thrown?.code === "StepUpRequired" || thrown?.response?.data?.code === "StepUpRequired"
}
