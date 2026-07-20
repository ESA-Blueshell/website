import {MemberType, type ContributionPeriodResponse} from "@/services/api"
import type {FeeType} from "@/utils/bulkRow"

/**
 * Minimal fee helper: maps a KNOWN fee type to its € amount from the selected period's
 * fees, for live re-display as the operator changes a row's fee selector. This is NOT a
 * port of the server's resolveFeeType — the recommended type always comes from the
 * server preview row; this only does type → € lookup. Porting the tier resolution to
 * the browser is unsafe because it needs the LATEST-membership start, which the FE does
 * not have (it derives the EARLIEST). See docs/proposals/bulk-actions/REDESIGN.md §5.1.
 */

// Re-exported for existing importers that pulled FeeType off feePreview.
export type {FeeType}

export function effectiveAmount(
  feeType: FeeType | null | undefined,
  period: Pick<ContributionPeriodResponse, "fullYearFee" | "halfYearFee" | "alumniFee"> | null | undefined,
): number | null {
  if (!feeType || !period) return null
  switch (feeType) {
    case "FULL_YEAR_FEE":
      return period.fullYearFee
    case "HALF_YEAR_FEE":
      return period.halfYearFee
    case "ALUMNI_FEE":
      return period.alumniFee
    default:
      return null
  }
}

export const feeTypeLabels: Record<FeeType, string> = {
  FULL_YEAR_FEE: "Full-year fee",
  HALF_YEAR_FEE: "Half-year fee",
  ALUMNI_FEE: "Alumni fee",
}

export const feeTypeItems: Array<{title: string; value: FeeType}> = [
  {title: feeTypeLabels.FULL_YEAR_FEE, value: "FULL_YEAR_FEE"},
  {title: feeTypeLabels.HALF_YEAR_FEE, value: "HALF_YEAR_FEE"},
  {title: feeTypeLabels.ALUMNI_FEE, value: "ALUMNI_FEE"},
]

/**
 * Auto-select the fee type for a reminder/incasso row, given the membership and the
 * half-year cutoff date. Locked rule:
 *   - honorary member       → excluded (no fee), returns null
 *   - membership type ALUMNI → ALUMNI_FEE
 *   - startDate <= cutoff    → FULL_YEAR_FEE (boundary start == cutoff resolves to FULL)
 *   - startDate  > cutoff    → HALF_YEAR_FEE
 *
 * Both dates are ISO (YYYY-MM-DD) so lexical comparison is date-correct.
 */
export function autoFeeType(
  membership: {type: MemberType; startDate: string} | null | undefined,
  isHonorary: boolean,
  cutoffDate: string,
): FeeType | null {
  if (isHonorary || !membership) return null
  if (membership.type === MemberType.ALUMNI) return "ALUMNI_FEE"
  if (!cutoffDate) return "FULL_YEAR_FEE"
  return membership.startDate > cutoffDate ? "HALF_YEAR_FEE" : "FULL_YEAR_FEE"
}
