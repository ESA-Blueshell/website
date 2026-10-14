import {
  type BankAccountResponse,
  findBankAccount,
  findOwnContributions,
  type MemberPeriodContribution,
  type OwnContributions,
} from "@/services/api"
import {readOr} from "@/utils/answers"

export type {BankAccountResponse, MemberPeriodContribution, OwnContributions}

/** The reader's own contribution periods, newest first, or nothing where they could not be read. */
export const readOwnContributions = (): Promise<OwnContributions | null> => readOr(findOwnContributions(), null)

/** The account a member transfers to, or nothing where it could not be read. */
export const readBankAccount = (): Promise<BankAccountResponse | null> => readOr(findBankAccount(), null)
