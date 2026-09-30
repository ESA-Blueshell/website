/**
 * A membership's mandate. Every answer masks the account to its last four; the full number
 * never comes back to the browser.
 */
import {findMandate, IncassoStanding, type MandateResponse, recordMandate, type RecordMandateRequest} from "@/services/api"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"
import {readOr} from "@/utils/answers"
import {refusable} from "../refusals"

export type {MandateResponse, RecordMandateRequest}
export {IncassoStanding}

/** The membership's mandate and incasso standing, or nothing where it could not be read. */
export const readMandate = (membershipId: number): Promise<MandateResponse | null> =>
  readOr(findMandate({path: {membershipId}}), null)

/** Records a paper mandate, or replaces the one before it. */
export const saveMandate = (membershipId: number, body: RecordMandateRequest): Promise<Saved<MandateResponse> | Refused> =>
  refusable(recordMandate({path: {membershipId}, body}), "That mandate could not be recorded.")
