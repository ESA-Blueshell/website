/**
 * Recovery domain adapter — the only file in this domain that imports from `@/services/api`
 * (frontend ADR-002).
 */
import {
  memberActivate,
  type MemberActivationRequest,
  type PasswordResetRequest,
  pendingActivations,
  previewRecoveryEmail,
  type RecoveryEmailPreviewResponse,
  resendRecoveryEmail,
  resendUserActivation,
  resetPassword,
  restoreDeletedUserById,
  setPassword,
  type TokenPurpose,
  userActivate,
} from "@/services/api"

/**
 * Which activation each account that has not been activated is waiting for, so the manager
 * offers the one that applies rather than both and a guess. Empty where the api would not say.
 */
export async function listPendingActivations(): Promise<Record<number, TokenPurpose>> {
  const {data} = await pendingActivations()
  return Object.fromEntries((data?.activations ?? []).map(one => [one.userId, one.purpose]))
}

/**
 * Asks for a password reset link. Throws where the request did not get through, which is not the
 * same as an unknown username: the api says the same thing either way, so that nobody can use
 * this to find out who has an account.
 */
export async function requestPasswordReset(username: string): Promise<void> {
  await resetPassword({path: {username}, throwOnError: true})
}

/** Spends a reset token on a new password. Throws with the refusal the form reads its fields from. */
export async function setNewPassword(request: PasswordResetRequest): Promise<void> {
  await setPassword({body: request, throwOnError: true})
}

/**
 * What came of asking for another confirmation link. Whether the account exists, is already
 * confirmed, or nothing was sent, this is `sent`: telling those apart is what would turn the page
 * into a way of finding out who has an account. Being turned away for asking too often is about
 * the caller rather than the account, so it is worth saying, and "sent" would be false.
 */
export type ResendResult = {outcome: "sent"} | {outcome: "rate-limited"; cause: unknown}

export async function resendActivation(username: string): Promise<ResendResult> {
  try {
    await resendUserActivation({path: {username}, throwOnError: true})
    return {outcome: "sent"}
  } catch (cause) {
    if ((cause as {response?: {status?: number}})?.response?.status === 429) {
      return {outcome: "rate-limited", cause}
    }
    return {outcome: "sent"}
  }
}

/**
 * Spends an account activation token. Says whether a membership started with it, which the page
 * tells the reader. Throws on a refusal.
 */
export async function activateUser(token: string): Promise<{membershipStarted: boolean}> {
  const {data} = await userActivate({body: {token}, throwOnError: true})
  return {membershipStarted: data!.membershipStarted}
}

/** Spends a membership activation token. Throws with the refusal the form reads its fields from. */
export async function activateMember(request: MemberActivationRequest): Promise<void> {
  await memberActivate({body: request, throwOnError: true})
}

/** The recovery email as the account would receive it, or nothing where the api would not say. */
export async function previewRecoveryMail(
  userId: number,
  purpose: TokenPurpose,
): Promise<RecoveryEmailPreviewResponse | null> {
  const {data} = await previewRecoveryEmail({path: {userId}, query: {purpose}})
  return data ?? null
}

/**
 * Sends the account the one recovery email its purpose names, so an account the board created
 * stays reachable once its link expired. Throws on a refusal.
 */
export async function resendRecoveryMail(userId: number, purpose: TokenPurpose): Promise<void> {
  await resendRecoveryEmail({path: {userId}, query: {purpose}, throwOnError: true})
}

/** Puts a deleted account back, inside its restore window. Throws on a refusal. */
export async function restoreDeletedUser(userId: number): Promise<void> {
  await restoreDeletedUserById({path: {userId}, throwOnError: true})
}
