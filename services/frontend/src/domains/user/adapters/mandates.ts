/**
 * How a person pays and their mandate. Every answer masks the account; the full number comes back
 * to the browser only for a board member's reveal.
 */
import {needsStepUp} from "@/domains/auth"
import {
  downloadMandatePdf,
  endMandate,
  endOwnMandate,
  findMandate,
  findOwnMandate,
  IncassoStanding,
  MandateKind,
  type MandateResponse,
  type OwnMandateResponse,
  recordMandate,
  type RecordMandateRequest,
  revealIban,
  setUpMandate,
  signOwnMandate,
} from "@/services/api"
import {SIGNUP_TOKEN_HEADER} from "@/plugins/signupContinuation"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"
import {readBlobRefusal, readOr} from "@/utils/answers"
import {MANDATE_WORDING} from "../mandateWording"
import {accepted, reasonFor, refusable} from "../refusals"

export type {MandateResponse, OwnMandateResponse, RecordMandateRequest}
export {IncassoStanding, MandateKind}

/** How the person pays and their mandate, or nothing where it could not be read. */
export const readMandate = (userId: number): Promise<MandateResponse | null> => readOr(findMandate({path: {userId}}), null)

/** Records a paper mandate, or replaces the one before it. */
export const saveMandate = (userId: number, body: RecordMandateRequest): Promise<Saved<MandateResponse> | Refused> =>
  refusable(recordMandate({path: {userId}, body}), "That mandate could not be recorded.")

/** Ends the person's mandate today, as when they stop it on paper; they pay by transfer from then. */
export const stopMandate = (userId: number): Promise<Saved<MandateResponse> | Refused> =>
  refusable(endMandate({path: {userId}}), "We could not stop incasso. Try again.")

/** A write that may wait on a step-up the page asks for, as a change of bank details does. */
type NeedsStepUp<T> = T | (Refused & {needsStepUp?: boolean})

const guarded = (error: unknown, fallback: string) => ({ok: false as const, reason: reasonFor(error, fallback), needsStepUp: needsStepUp(error)})

/** The reader stops their own direct debit today. */
export async function stopOwnDirectDebit(): Promise<NeedsStepUp<Saved<OwnMandateResponse>>> {
  const res = await endOwnMandate()
  return res.data ? {ok: true, saved: res.data} : guarded(res.error, "We could not stop your direct debit. Try again.")
}

/** The reader sets up direct debit from the bank account on file, agreeing to the current wording. */
export async function setUpOwnDirectDebit(): Promise<NeedsStepUp<Saved<OwnMandateResponse>>> {
  const res = await signOwnMandate({body: {authorised: true, wordingVersion: MANDATE_WORDING.version}})
  return res.data ? {ok: true, saved: res.data} : guarded(res.error, "We could not set up direct debit. Try again.")
}

/**
 * The person's full IBAN, for a board member. The api writes each reveal to their security log;
 * the caller keeps the answer in memory only, never in storage.
 */
export async function revealMandateIban(userId: number): Promise<Saved<string> | Refused> {
  const answered = await refusable(revealIban({path: {userId}}), "The IBAN could not be shown.")
  return answered.ok ? {ok: true, saved: answered.saved.iban} : answered
}

/** An online mandate as its PDF, filled in by the api as it answers; nothing keeps a copy. */
export async function fetchMandatePdf(userId: number): Promise<{ok: true; file: Blob} | Refused> {
  const answered = await refusable(readBlobRefusal(downloadMandatePdf({path: {userId}})), "The mandate's PDF could not be made.")
  return answered.ok ? {ok: true, file: answered.saved as Blob} : answered
}

/** The reader's own mandate, masked, or nothing where it could not be read. */
export const readOwnMandate = (): Promise<OwnMandateResponse | null> => readOr(findOwnMandate(), null)

/**
 * The signup's incasso step, on the signup's token: the details are kept on the person, and the
 * mandate takes the address the signup has just taken.
 */
export const setUpIncasso = (
  body: {iban: string; accountHolder: string; authorised: boolean},
  signupToken: string,
): Promise<{ok: true} | Refused> =>
  accepted(
    setUpMandate({headers: {[SIGNUP_TOKEN_HEADER]: signupToken}, body: {...body, wordingVersion: MANDATE_WORDING.version}}),
    "Your bank details could not be saved.",
  )
