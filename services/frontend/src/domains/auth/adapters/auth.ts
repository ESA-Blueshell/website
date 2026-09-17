/**
 * Auth domain adapter — the only file in this domain that imports from `@/services/api`
 * (frontend ADR-002).
 */
import {authenticate, type LoginResponse} from "@/services/api"

/**
 * What came of an attempt to sign in, as the login form has to tell them apart: a wrong password
 * is the reader's to correct and gets its own message, and anything else is a fault to report.
 * The status code stays here, because which number means "wrong password" is the wire's business.
 */
export type SignInResult =
  | {outcome: "signed-in"; login: LoginResponse}
  | {outcome: "rejected"}
  | {outcome: "failed"; cause: unknown}

export async function signIn(username: string, password: string): Promise<SignInResult> {
  const response = await authenticate({body: {username, password}})

  if (response.status === 200 && response.data) return {outcome: "signed-in", login: response.data}
  if (response.status === 401) return {outcome: "rejected"}
  return {outcome: "failed", cause: response}
}
