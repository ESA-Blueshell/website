/**
 * Writing an email on the site: who it can be addressed to, how many it reaches, and sending it.
 * The only file in this domain that reaches the generated client (frontend ADR-001).
 */
import {
  type Addressee,
  AddresseeKind,
  type Audience,
  findAudiences,
  findReach,
  findReplyToOptions,
  type ReachResponse,
  Role,
  sendTestEmail,
  sendWrittenEmail,
  type WriteEmailRequest,
  type WrittenResponse,
} from "@/services/api"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"
import {readOr} from "@/utils/answers"
import {refusable} from "../refusals"

export type {Addressee, Audience, ReachResponse, WriteEmailRequest, WrittenResponse}
// A role is one of the addressees, so the picker reads the roles from here.
export {AddresseeKind, Role}

/** The cohorts an email can be addressed to, or none where they could not be read. */
export const listAudiences = (): Promise<Audience[]> => readOr(findAudiences(), [])

/** Where replies may go; the association's own address first. */
export const listReplyTo = (): Promise<string[]> => readOr(findReplyToOptions(), [])

/** How many people the addressees reach, and how many have no address. */
export const readReach = (to: Addressee[]): Promise<ReachResponse | null> => readOr(findReach({body: {to}}), null)

/** Queues one copy per person it reaches. */
export const sendWritten = (body: WriteEmailRequest): Promise<Saved<WrittenResponse> | Refused> =>
  refusable(sendWrittenEmail({body}), "The email could not be sent.")

/** One copy to the writer, to see it as it will arrive. */
export const sendTest = (body: WriteEmailRequest): Promise<Saved<WrittenResponse> | Refused> =>
  refusable(sendTestEmail({body}), "The test could not be sent.")
