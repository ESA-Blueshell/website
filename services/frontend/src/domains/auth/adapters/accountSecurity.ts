/**
 * The security page's and the user manager's reads and writes of account security, through the
 * generated SDK (frontend ADR-002). A refused write answers the sentence to show.
 */
import {
  accountStanding,
  type AccountStandingResponse,
  answerTwoFactorOffer,
  changePassword,
  confirmEmailChange,
  confirmTwoFactor,
  emailAddress,
  type EmailAddressResponse,
  endSignIn,
  forgetTrustedBrowser,
  forgetTrustedBrowsers,
  lock,
  mySecurityEvents,
  previewRecoveryEmail,
  type RecoveryEmailPreviewResponse,
  regenerateBackupCodes,
  requestEmailChange,
  resendReenrolmentLink,
  resetTwoFactor,
  securityEvents,
  type SecurityEventPageResponse,
  setUpTwoFactor,
  type SignInResponse,
  signIns,
  signOutElsewhere,
  signOutEverywhere,
  TokenPurpose,
  trustedBrowsers,
  type TrustedBrowserResponse,
  turnOffTwoFactor,
  twoFactorSaved,
  type TwoFactorSetupResponse,
  type TwoFactorStanding,
  twoFactorStanding,
  unlock,
} from "@/services/api"
import type {Refused} from "@/types/api"
import type {Answer} from "@/utils/answers"
import type {Saved} from "@/utils/refusals"
import {accepted, needsStepUp, refusable} from "../refusals"

/** A refused write, and whether proving the reader again would clear it. */
export type StepRefused = Refused & {needsStepUp: boolean}

/** A write that went through, or the refusal to show and whether a step-up would clear it. */
export type Written = {ok: true} | StepRefused

async function stepRefusable<T>(call: Promise<Answer<T>>, fallback: string): Promise<Saved<T> | StepRefused> {
  const res = await call
  const said = await refusable(Promise.resolve(res), fallback)
  return said.ok ? said : {...said, needsStepUp: needsStepUp(res.error)}
}

async function stepAccepted(call: Promise<Answer<unknown>>, fallback: string): Promise<Written> {
  const res = await call
  const said = await accepted(Promise.resolve(res), fallback)
  return said.ok ? said : {...said, needsStepUp: needsStepUp(res.error)}
}

const codes = (said: Saved<{codes: string[]}> | StepRefused): Saved<string[]> | StepRefused =>
  said.ok ? {ok: true, saved: said.saved.codes} : said

export async function readTwoFactor(): Promise<TwoFactorStanding | null> {
  const response = await twoFactorStanding()
  return response.data ?? null
}

/** Without a password only a granted role waiting on two-factor gets a secret, on a sign-in it just opened. */
export const startTwoFactorSetUp = (password?: string): Promise<Saved<TwoFactorSetupResponse> | StepRefused> =>
  stepRefusable(setUpTwoFactor({body: password ? {password} : {}}), "Setting up two-factor failed.")

export const confirmTwoFactorCode = async (code: string): Promise<Saved<string[]> | StepRefused> =>
  codes(await stepRefusable(confirmTwoFactor({body: {code}}), "That code could not be checked."))

export const finishTwoFactorSetUp = (): Promise<Written> =>
  stepAccepted(twoFactorSaved(), "Two-factor could not be turned on.")

export const removeTwoFactor = (): Promise<Written> =>
  stepAccepted(turnOffTwoFactor(), "Two-factor could not be turned off.")

export const newBackupCodes = async (): Promise<Saved<string[]> | StepRefused> =>
  codes(await stepRefusable(regenerateBackupCodes(), "New backup codes could not be made."))

export const answerOffer = (): Promise<Written> =>
  stepAccepted(answerTwoFactorOffer(), "The answer could not be saved.")

export const savePassword = (currentPassword: string, newPassword: string): Promise<Written> =>
  stepAccepted(changePassword({body: {currentPassword, newPassword}}), "The password could not be changed.")

export async function readEmailAddress(): Promise<EmailAddressResponse | null> {
  return (await emailAddress()).data ?? null
}

export const askToMoveEmail = (email: string): Promise<Written> =>
  stepAccepted(requestEmailChange({body: {email}}), "The address could not be changed.")

export const confirmNewEmail = (token: string): Promise<Written> =>
  stepAccepted(confirmEmailChange({body: {token}}), "That link does not work any more.")

/** Follows a lock link; answers who to contact, the same whatever the link was. */
export async function lockAccount(token: string): Promise<string | null> {
  const response = await lock({body: {token}})
  return response.data?.contactEmail ?? null
}

export async function listSignIns(): Promise<SignInResponse[]> {
  return (await signIns()).data ?? []
}

export const endOneSignIn = (id: string): Promise<Written> =>
  stepAccepted(endSignIn({path: {signInId: id}}), "That sign-in could not be ended.")

export const endEverySignIn = (): Promise<Written> =>
  stepAccepted(signOutEverywhere(), "Signing out everywhere failed.")

export const endOtherSignIns = (): Promise<Written> =>
  stepAccepted(signOutElsewhere(), "The other sign-ins could not be ended.")

export async function listTrustedBrowsers(): Promise<TrustedBrowserResponse[]> {
  return (await trustedBrowsers()).data ?? []
}

export const forgetOneTrustedBrowser = (id: number): Promise<Written> =>
  stepAccepted(forgetTrustedBrowser({path: {id}}), "That browser could not be forgotten.")

export const forgetEveryTrustedBrowser = (): Promise<Written> =>
  stepAccepted(forgetTrustedBrowsers(), "The browsers could not be forgotten.")

export async function readMySecurityLog(page = 0): Promise<SecurityEventPageResponse | null> {
  return (await mySecurityEvents({query: {page, size: 20}})).data ?? null
}

export async function readSecurityLogOf(userId: number, page = 0): Promise<SecurityEventPageResponse | null> {
  return (await securityEvents({path: {userId}, query: {page, size: 20}})).data ?? null
}

export async function readAccountStanding(userId: number): Promise<AccountStandingResponse | null> {
  return (await accountStanding({path: {userId}})).data ?? null
}

export const resetTwoFactorOf = (userId: number, reason: string): Promise<Written> =>
  stepAccepted(resetTwoFactor({path: {userId}, body: {reason}}), "Two-factor could not be reset.")

/** The re-enrolment email as the person would receive it, with an inert link. */
export async function previewReenrolment(userId: number): Promise<RecoveryEmailPreviewResponse | null> {
  const {data} = await previewRecoveryEmail({path: {userId}, query: {purpose: TokenPurpose.TWO_FACTOR_REENROLMENT}})
  return data ?? null
}

export const resendReenrolment = (userId: number): Promise<Written> =>
  stepAccepted(resendReenrolmentLink({path: {userId}}), "The link could not be sent.")

export const unlockAccount = (userId: number, reason: string, email?: string): Promise<Written> =>
  stepAccepted(unlock({path: {userId}, body: {reason, email: email || undefined}}), "The account could not be unlocked.")
