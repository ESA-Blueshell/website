/**
 * One person's contributions: every period they were a member in, and recording or withdrawing
 * their payment for one.
 */
import {createContribution, deleteContribution, findMemberContributions, type MemberPeriodContribution} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import {refusalReader} from "@/utils/refusals"

export type {MemberPeriodContribution}

const {accepted} = refusalReader({})

/** Newest period first, or none where they could not be read. */
export const listMemberContributions = (userId: number): Promise<MemberPeriodContribution[]> =>
  readOr(findMemberContributions({path: {userId}}), [])

/** Records that the person paid for the period. */
export const recordPayment = (userId: number, contributionPeriodId: number): Promise<{ok: true} | Refused> =>
  accepted(createContribution({body: {userId, contributionPeriodId}}), "That payment could not be recorded.")

/** Takes the person's payment for the period back off the record. */
export const withdrawPayment = (userId: number, contributionPeriodId: number): Promise<{ok: true} | Refused> =>
  accepted(deleteContribution({path: {userId, contributionPeriodId}}), "That payment could not be withdrawn.")
