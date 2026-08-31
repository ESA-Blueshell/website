import type {ContributionPeriodResponse} from "@/services/api"
import {BulkFeeType} from "@/utils/bulkRow"

/**
 * Fee type to €, read off the selected period.
 *
 * Type → amount only. Which type *applies* to a member is the api's answer, resolved from
 * the membership's start against the cutoff the period carries; this exists so the amount
 * re-renders the moment the treasurer changes a row's type, without a round trip.
 */
export function effectiveAmount(
  feeType: BulkFeeType | null | undefined,
  period: Pick<ContributionPeriodResponse, "fullYearFee" | "halfYearFee" | "alumniFee"> | null | undefined,
): number | null {
  if (!feeType || !period) return null
  switch (feeType) {
    case BulkFeeType.FULL_YEAR_FEE:
      return period.fullYearFee
    case BulkFeeType.HALF_YEAR_FEE:
      return period.halfYearFee
    case BulkFeeType.ALUMNI_FEE:
      return period.alumniFee
    default:
      return null
  }
}

export const feeTypeLabels: Record<BulkFeeType, string> = {
  [BulkFeeType.FULL_YEAR_FEE]: "Full-year fee",
  [BulkFeeType.HALF_YEAR_FEE]: "Half-year fee",
  [BulkFeeType.ALUMNI_FEE]: "Alumni fee",
}

/**
 * The fee-type picker's options, enumerated from the generated enum rather than written
 * out, so a fee type added on the api reaches the picker without a second edit here.
 */
export const feeTypeItems: Array<{title: string; value: BulkFeeType}> =
  Object.values(BulkFeeType).map((value) => ({title: feeTypeLabels[value], value}))
