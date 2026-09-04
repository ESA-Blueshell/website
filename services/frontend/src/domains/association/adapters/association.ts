/**
 * Association domain adapter: the only file in this domain that imports from @/services/api
 * (per frontend ADR-002). Everything else imports from here.
 */
import {associationStatistics, findCurrentContributionPeriod} from "@/services/api"
import type {AssociationStatisticsResponse, ContributionPeriodResponse} from "@/services/api"

/** What the association can say about itself in numbers. */
export type AssociationNumbers = AssociationStatisticsResponse

/** A year's contribution, as the association records it. */
export type ContributionPeriod = ContributionPeriodResponse

/**
 * The association's own numbers, or nothing where the api would not say.
 *
 * Nothing rather than a throw, and nothing rather than zeroes: the page these feed shows honest
 * floors until real figures land, and a zero would read as a fact. The generated client resolves
 * on 4xx and 5xx with an `error` instead of throwing, so the error is checked rather than caught.
 */
export async function loadAssociationNumbers(): Promise<AssociationNumbers | null> {
  const res = await associationStatistics()
  if (res.error || !res.data) return null
  return res.data
}

/**
 * The contribution period the association is charging for, or nothing where none is recorded.
 *
 * The same read the signup form's fee component makes, so both pages quote one source. An
 * empty answer is a period nobody has written down yet, which is a fact the page can state.
 */
export async function loadCurrentContributionPeriod(): Promise<ContributionPeriod | null> {
  const res = await findCurrentContributionPeriod()
  if (res.error || !res.data) return null
  return res.data
}
