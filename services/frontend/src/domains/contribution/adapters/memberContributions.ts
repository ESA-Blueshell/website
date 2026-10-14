/** One person's contributions: every period they were a member in, and recording or withdrawing their payment for one. */
import {
  type BankAccountResponse,
  createContribution,
  deleteContribution,
  findBankAccount,
  findMemberContributions,
  findOwnContributions,
  findPeriodContributions,
  type MemberPeriodContribution,
  type PeriodContributionsView,
  type PeriodMember,
} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import {refusalReader} from "@/utils/refusals"

export type {MemberPeriodContribution, PeriodContributionsView, PeriodMember}

const {accepted} = refusalReader({})

/** Newest period first, or null where they could not be read. */
export const listMemberContributions = (userId: number): Promise<MemberPeriodContribution[] | null> =>
  readOr(findMemberContributions({path: {userId}}), null)

/** The reader's own periods, newest first, or null where they could not be read. */
export const listOwnContributions = (): Promise<MemberPeriodContribution[] | null> => readOr(findOwnContributions(), null)

export type BankAccount = BankAccountResponse

/** The association's account, for a transfer by hand; nothing where it could not be read. */
export const readBankAccount = (): Promise<BankAccount | null> => readOr(findBankAccount(), null)

/** Records that the person paid for the period. */
export const recordPayment = (userId: number, contributionPeriodId: number): Promise<{ok: true} | Refused> =>
  accepted(createContribution({body: {userId, contributionPeriodId}}), "That payment could not be recorded.")

/** Takes the person's payment for the period back off the record. */
export const withdrawPayment = (userId: number, contributionPeriodId: number): Promise<{ok: true} | Refused> =>
  accepted(deleteContribution({path: {userId, contributionPeriodId}}), "That payment could not be withdrawn.")

/** A period's members with their payments, and the payment email runs sent for it; null where unreadable. */
export const readPeriodContributions = (periodId: number): Promise<PeriodContributionsView | null> =>
  readOr(findPeriodContributions({path: {periodId}}), null)
