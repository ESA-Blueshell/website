/**
 * Auth domain adapter — the only file in this domain that imports from `@/services/api`
 * (frontend ADR-002).
 */
import {
  answerChallenge as answerChallengeCall,
  authenticate,
  type LoginResponse,
  reenrol as reenrolCall,
  SignInStatus,
  stepUp as stepUpCall,
} from "@/services/api"
import {codeOf, reasonFor} from "../refusals"

/**
 * What came of the password step, as the login form has to tell them apart: a wrong password is
 * the reader's to correct, a code comes next, and a locked or reset account says who to ask.
 * The status codes stay here, because which number means what is the wire's business.
 */
export type SignInResult =
  | {outcome: "signed-in"; login: LoginResponse}
  | {outcome: "two-factor"}
  | {outcome: "rejected"}
  | {outcome: "refused"; code: string; reason: string}
  | {outcome: "failed"; cause: unknown}

export async function signIn(username: string, password: string): Promise<SignInResult> {
  const response = await authenticate({body: {username, password}})

  if (response.data?.status === SignInStatus.TWO_FACTOR_REQUIRED) return {outcome: "two-factor"}
  if (response.data?.login) return {outcome: "signed-in", login: response.data.login}
  if (response.status === 401) return {outcome: "rejected"}
  const code = codeOf(response.error)
  if (code) return {outcome: "refused", code, reason: reasonFor(response.error, "Signing in was refused.")}
  return {outcome: "failed", cause: response}
}

/** What came of a code at the challenge, or of a step-up. */
export type CodeResult =
  | {outcome: "signed-in"; login: LoginResponse}
  | {outcome: "refused"; code: string; reason: string}
  | {outcome: "failed"; cause: unknown}

export async function answerChallenge(code: string, trustThisBrowser: boolean): Promise<CodeResult> {
  const response = await answerChallengeCall({body: {code, trustThisBrowser}})
  if (response.data?.login) return {outcome: "signed-in", login: response.data.login}
  return refusalOf(response.error, response)
}

export async function reenrol(token: string, username: string, password: string): Promise<CodeResult> {
  const response = await reenrolCall({body: {token, username, password}})
  if (response.data?.login) return {outcome: "signed-in", login: response.data.login}
  return refusalOf(response.error, response)
}

/** Proves the reader inside their sign-in: a code where two-factor is on, the password where not. */
export async function stepUp(proof: {code?: string; password?: string}): Promise<{ok: true} | {ok: false; reason: string}> {
  const response = await stepUpCall({body: proof})
  if (!response.error) return {ok: true}
  return {ok: false, reason: reasonFor(response.error, "That could not be checked. Try again.")}
}

function refusalOf(error: unknown, response: unknown): CodeResult {
  const code = codeOf(error)
  if (code) return {outcome: "refused", code, reason: reasonFor(error, "That was refused.")}
  return {outcome: "failed", cause: response}
}
