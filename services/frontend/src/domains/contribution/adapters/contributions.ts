/**
 * Recording what a member has paid, in bulk. Idempotent on both sides: a member already in
 * the state asked for is skipped rather than written twice.
 */
import {markPaid, markUnpaid} from "@/services/api"

export type BulkContributionCall = typeof markPaid

export const recordPaid: BulkContributionCall = markPaid
export const recordUnpaid: BulkContributionCall = markUnpaid
