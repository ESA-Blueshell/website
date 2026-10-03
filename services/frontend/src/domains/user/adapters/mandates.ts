/**
 * A membership's mandate. Every answer masks the account; the full number comes back to the
 * browser only for a board member's reveal.
 */
import {
  findMandate,
  findOwnMandate,
  IncassoStanding,
  type MandateResponse,
  type OwnMandateResponse,
  recordMandate,
  type RecordMandateRequest,
  revealIban,
  setUpMandate,
  setUpOwnMandate,
} from "@/services/api"
import {SIGNUP_TOKEN_HEADER} from "@/plugins/signupContinuation"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"
import {readOr} from "@/utils/answers"
import {accepted, reasonFor, refusable} from "../refusals"

export type {MandateResponse, OwnMandateResponse, RecordMandateRequest}
export {IncassoStanding}

/** The membership's mandate and incasso standing, or nothing where it could not be read. */
export const readMandate = (membershipId: number): Promise<MandateResponse | null> =>
  readOr(findMandate({path: {membershipId}}), null)

/** Records a paper mandate, or replaces the one before it. */
export const saveMandate = (membershipId: number, body: RecordMandateRequest): Promise<Saved<MandateResponse> | Refused> =>
  refusable(recordMandate({path: {membershipId}, body}), "That mandate could not be recorded.")

/**
 * The membership's full IBAN, for a board member. The api writes each reveal to the member's
 * security log; the caller keeps the answer in memory only, never in storage.
 */
export async function revealMandateIban(membershipId: number): Promise<Saved<string> | Refused> {
  const answered = await refusable(revealIban({path: {membershipId}}), "The IBAN could not be shown.")
  return answered.ok ? {ok: true, saved: answered.saved.iban} : answered
}

/** The reader's own mandate, masked, or nothing where it could not be read. */
export const readOwnMandate = (): Promise<OwnMandateResponse | null> => readOr(findOwnMandate(), null)

/**
 * The reader sets up or changes incasso, signed today. During a signup it goes on the signup's
 * token and waits for the membership; it answers nothing then, since the reader has no session.
 */
export async function setUpIncasso(
  body: {iban: string; accountHolder: string; authorised: boolean},
  signupToken?: string,
): Promise<{ok: true; saved: OwnMandateResponse | null} | (Refused & {needsStepUp?: boolean})> {
  if (signupToken) {
    const answered = await accepted(setUpMandate({headers: {[SIGNUP_TOKEN_HEADER]: signupToken}, body}), "Your bank details could not be saved.")
    return answered.ok ? {ok: true, saved: null} : answered
  }
  // From the account page a change waits on a step-up, which the page asks for and then saves again.
  const res = await setUpOwnMandate({body})
  if (res.data) return {ok: true, saved: res.data}
  return {
    ok: false,
    reason: reasonFor(res.error, "Your bank details could not be saved."),
    needsStepUp: (res.error as {code?: string} | undefined)?.code === "StepUpRequired",
  }
}
