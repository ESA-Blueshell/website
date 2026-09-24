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
  endSignIn,
  forgetTrustedBrowser,
  forgetTrustedBrowsers,
  lock,
  mySecurityEvents,
  regenerateBackupCodes,
  requestEmailChange,
  resendReenrolmentLink,
  resetTwoFactor,
  securityEvents,
  type SecurityEventPageResponse,
  setUpTwoFactor,
  type SignInResponse,
  signIns,
  signOutEverywhere,
  trustedBrowsers,
  type TrustedBrowserResponse,
  turnOffTwoFactor,
  twoFactorSaved,
  type TwoFactorSetupResponse,
  type TwoFactorStanding,
  twoFactorStanding,
  unlock,
} from "@/services/api"
import {codeOf, reasonFor} from "../refusals"

/** A write that went through, or the refusal to show and whether a step-up would clear it. */
export type Written<T = void> =
  | {ok: true; value: T}
  | {ok: false; reason: string; needsStepUp: boolean}

function written<T>(error: unknown, value: () => T, fallback: string): Written<T> {
  if (!error) return {ok: true, value: value()}
  return {ok: false, reason: reasonFor(error, fallback), needsStepUp: codeOf(error) === "StepUpRequired"}
}

export async function readTwoFactor(): Promise<TwoFactorStanding | null> {
  const response = await twoFactorStanding()
  return response.data ?? null
}

export async function startTwoFactorSetUp(password: string): Promise<Written<TwoFactorSetupResponse>> {
  const {data, error} = await setUpTwoFactor({body: {password}})
  return written(error, () => data as TwoFactorSetupResponse, "Setting up two-factor failed.")
}

export async function confirmTwoFactorCode(code: string): Promise<Written<string[]>> {
  const {data, error} = await confirmTwoFactor({body: {code}})
  return written(error, () => data?.codes ?? [], "That code could not be checked.")
}

export async function finishTwoFactorSetUp(): Promise<Written> {
  const {error} = await twoFactorSaved()
  return written(error, () => undefined, "Two-factor could not be turned on.")
}

export async function removeTwoFactor(): Promise<Written> {
  const {error} = await turnOffTwoFactor()
  return written(error, () => undefined, "Two-factor could not be turned off.")
}

export async function newBackupCodes(): Promise<Written<string[]>> {
  const {data, error} = await regenerateBackupCodes()
  return written(error, () => data?.codes ?? [], "New backup codes could not be made.")
}

export async function answerOffer(): Promise<Written> {
  const {error} = await answerTwoFactorOffer()
  return written(error, () => undefined, "The answer could not be saved.")
}

export async function savePassword(currentPassword: string, newPassword: string): Promise<Written> {
  const {error} = await changePassword({body: {currentPassword, newPassword}})
  return written(error, () => undefined, "The password could not be changed.")
}

export async function askToMoveEmail(email: string): Promise<Written> {
  const {error} = await requestEmailChange({body: {email}})
  return written(error, () => undefined, "The address could not be changed.")
}

export async function confirmNewEmail(token: string): Promise<Written> {
  const {error} = await confirmEmailChange({body: {token}})
  return written(error, () => undefined, "That link does not work any more.")
}

/** Follows a lock link; answers who to contact, the same whatever the link was. */
export async function lockAccount(token: string): Promise<string | null> {
  const response = await lock({body: {token}})
  return response.data?.contactEmail ?? null
}

export async function listSignIns(): Promise<SignInResponse[]> {
  return (await signIns()).data ?? []
}

export async function endOneSignIn(id: string): Promise<Written> {
  const {error} = await endSignIn({path: {signInId: id}})
  return written(error, () => undefined, "That sign-in could not be ended.")
}

export async function endEverySignIn(): Promise<Written> {
  const {error} = await signOutEverywhere()
  return written(error, () => undefined, "Signing out everywhere failed.")
}

export async function listTrustedBrowsers(): Promise<TrustedBrowserResponse[]> {
  return (await trustedBrowsers()).data ?? []
}

export async function forgetOneTrustedBrowser(id: number): Promise<Written> {
  const {error} = await forgetTrustedBrowser({path: {id}})
  return written(error, () => undefined, "That browser could not be forgotten.")
}

export async function forgetEveryTrustedBrowser(): Promise<Written> {
  const {error} = await forgetTrustedBrowsers()
  return written(error, () => undefined, "The browsers could not be forgotten.")
}

export async function readMySecurityLog(page = 0): Promise<SecurityEventPageResponse | null> {
  return (await mySecurityEvents({query: {page, size: 20}})).data ?? null
}

export async function readSecurityLogOf(userId: number, page = 0): Promise<SecurityEventPageResponse | null> {
  return (await securityEvents({path: {userId}, query: {page, size: 20}})).data ?? null
}

export async function readAccountStanding(userId: number): Promise<AccountStandingResponse | null> {
  return (await accountStanding({path: {userId}})).data ?? null
}

export async function resetTwoFactorOf(userId: number, reason: string): Promise<Written> {
  const {error} = await resetTwoFactor({path: {userId}, body: {reason}})
  return written(error, () => undefined, "Two-factor could not be reset.")
}

export async function resendReenrolment(userId: number): Promise<Written> {
  const {error} = await resendReenrolmentLink({path: {userId}})
  return written(error, () => undefined, "The link could not be sent.")
}

export async function unlockAccount(userId: number, reason: string, email?: string): Promise<Written> {
  const {error} = await unlock({path: {userId}, body: {reason, email: email || undefined}})
  return written(error, () => undefined, "The account could not be unlocked.")
}
