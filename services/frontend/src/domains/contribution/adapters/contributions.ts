/**
 * Recording what a member has paid, in bulk. Idempotent on both sides: a member already in
 * the state asked for is skipped rather than written twice.
 */
import {findContributionsByPeriodId, markPaid, markUnpaid} from "@/services/api"

export type BulkContributionCall = typeof markPaid

export const recordPaid: BulkContributionCall = markPaid
export const recordUnpaid: BulkContributionCall = markUnpaid

/** Who has paid for the period. Throws where it could not be read, since nobody paid would be wrong. */
export const listPaidUserIds = async (periodId: number): Promise<Set<number>> =>
  new Set(((await findContributionsByPeriodId({path: {periodId}, throwOnError: true})).data ?? []).map((one) => one.userId))
