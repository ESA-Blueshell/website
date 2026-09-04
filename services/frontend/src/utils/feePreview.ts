import type {ContributionPeriodResponse} from "@/services/api"
import {BulkFeeType} from "@/utils/bulkRow"

/**
 * Fee type to €, read off the selected period.
 *
 * Type to amount only: which type *applies* is the api's answer, read from the membership's
 * start against the period's cutoff. This exists so the amount re-renders the moment a row's
 * type changes, without a round trip. The same rule as `resolveFeeAmount` in the api's
 * contribution domain, implemented twice deliberately — the api prices what it sends and this
 * prices what the treasurer is looking at. Change one, change the other.
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
