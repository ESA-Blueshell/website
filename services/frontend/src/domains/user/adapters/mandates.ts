/**
 * How a person pays and their mandate. Every answer masks the account; the full number comes back
 * to the browser only for a board member's reveal.
 */
import {
  downloadMandatePdf,
  findMandate,
  findOwnMandate,
  IncassoStanding,
  type MandateAddressRequest,
  MandateKind,
  type MandateResponse,
  type OwnMandateResponse,
  recordMandate,
  type RecordMandateRequest,
  revealIban,
  setPaysBy,
  setUpMandate,
  setUpOwnMandate,
} from "@/services/api"
import {SIGNUP_TOKEN_HEADER} from "@/plugins/signupContinuation"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"
import {readOr} from "@/utils/answers"
import {MANDATE_WORDING} from "../mandateWording"
import {accepted, reasonFor, refusable} from "../refusals"

export type {MandateAddressRequest, MandateResponse, OwnMandateResponse, RecordMandateRequest}
export {IncassoStanding, MandateKind}

/** How the person pays and their mandate, or nothing where it could not be read. */
export const readMandate = (userId: number): Promise<MandateResponse | null> => readOr(findMandate({path: {userId}}), null)

/** Records a paper mandate, or replaces the one before it. */
export const saveMandate = (userId: number, body: RecordMandateRequest): Promise<Saved<MandateResponse> | Refused> =>
  refusable(recordMandate({path: {userId}, body}), "That mandate could not be recorded.")

/** Sets whether the person pays by incasso or by transfer; a mandate on file stays either way. */
export const savePaysBy = (userId: number, incasso: boolean): Promise<Saved<MandateResponse> | Refused> =>
  refusable(setPaysBy({path: {userId}, body: {incasso}}), "How they pay could not be saved.")

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
  const answered = await refusable(downloadMandatePdf({path: {userId}}), "The mandate's PDF could not be made.")
  return answered.ok ? {ok: true, file: answered.saved as Blob} : answered
}

/** The reader's own mandate, masked, or nothing where it could not be read. */
export const readOwnMandate = (): Promise<OwnMandateResponse | null> => readOr(findOwnMandate(), null)

/**
 * The reader sets up or changes incasso, signed today. During a signup it goes on the signup's
 * token and is kept on the person; it answers nothing then, since the reader has no session.
 */
export async function setUpIncasso(
  body: {iban: string; accountHolder: string; authorised: boolean; address: MandateAddressRequest},
  signupToken?: string,
): Promise<{ok: true; saved: OwnMandateResponse | null} | (Refused & {needsStepUp?: boolean})> {
  const wordingVersion = MANDATE_WORDING.version
  if (signupToken) {
    // The signup's mandate takes the address the signup has just taken, so it sends none.
    const {iban, accountHolder, authorised} = body
    const answered = await accepted(
      setUpMandate({headers: {[SIGNUP_TOKEN_HEADER]: signupToken}, body: {iban, accountHolder, authorised, wordingVersion}}),
      "Your bank details could not be saved.",
    )
    return answered.ok ? {ok: true, saved: null} : answered
  }
  // From the account page a change waits on a step-up, which the page asks for and then saves again.
  const res = await setUpOwnMandate({body: {...body, wordingVersion}})
  if (res.data) return {ok: true, saved: res.data}
  return {
    ok: false,
    reason: reasonFor(res.error, "Your bank details could not be saved."),
    needsStepUp: (res.error as {code?: string} | undefined)?.code === "StepUpRequired",
  }
}
