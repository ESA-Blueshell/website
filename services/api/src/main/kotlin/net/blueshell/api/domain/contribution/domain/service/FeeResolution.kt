package net.blueshell.api.domain.contribution.domain.service

import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.MemberType
import java.time.LocalDate

/**
 * Resolves the fee amount owed by a member for a contribution period,
 * accounting for member type and membership start date relative to cutoff.
 *
 * Rules:
 * - REGULAR: half-year fee if membership started AFTER the cutoff, else full-year (boundary start == cutoff pays full year, matching the frontend rule)
 * - ALUMNI: alumni fee
 * - HONORARY: excluded (returns null)
 *
 * @param memberType the member's type
 * @param membershipStartDate the date the membership started (null = unresolvable)
 * @param cutoffDate the date used to determine half-year vs full-year for REGULAR members
 * @param period the contribution period carrying the fees
 * @return the resolved fee in euros, or null if excluded (honorary)
 */
fun resolveMemberFee(
    memberType: MemberType,
    membershipStartDate: LocalDate?,
    cutoffDate: LocalDate,
    period: ContributionPeriod,
): Double? = when (memberType) {
    MemberType.REGULAR -> {
        if (membershipStartDate != null && membershipStartDate > cutoffDate) {
            period.halfYearFee
        } else {
            period.fullYearFee
        }
    }
    MemberType.ALUMNI -> period.alumniFee
    MemberType.HONORARY -> null // Excluded
    MemberType.NONE -> period.fullYearFee // Fallback for no membership
}

/**
 * Resolves the [BulkFeeType] recommended for a member based on their type and
 * membership start date relative to the half-year cutoff. Returns null for
 * HONORARY members (they are excluded).
 *
 * Rules (mirror of [resolveMemberFee]):
 * - REGULAR started on or before cutoff → FULL_YEAR_FEE
 * - REGULAR started after cutoff → HALF_YEAR_FEE
 * - ALUMNI → ALUMNI_FEE
 * - HONORARY → null (excluded)
 */
fun resolveFeeType(
    memberType: MemberType,
    membershipStartDate: LocalDate?,
    cutoffDate: LocalDate,
): BulkFeeType? = when (memberType) {
    MemberType.REGULAR -> {
        if (membershipStartDate != null && membershipStartDate > cutoffDate) {
            BulkFeeType.HALF_YEAR_FEE
        } else {
            BulkFeeType.FULL_YEAR_FEE
        }
    }
    MemberType.ALUMNI -> BulkFeeType.ALUMNI_FEE
    MemberType.HONORARY -> null // Excluded
    MemberType.NONE -> BulkFeeType.FULL_YEAR_FEE // Fallback for no membership
}

/**
 * Resolves the € amount for a given [BulkFeeType] from the contribution period.
 * This is a pure function with no side effects.
 */
fun resolveFeeAmount(feeType: BulkFeeType, period: ContributionPeriod): Double = when (feeType) {
    BulkFeeType.FULL_YEAR_FEE -> period.fullYearFee
    BulkFeeType.HALF_YEAR_FEE -> period.halfYearFee
    BulkFeeType.ALUMNI_FEE -> period.alumniFee
}

/**
 * Best-effort recovery of the [BulkFeeType] that produced a persisted [amount] for a
 * given [period], by matching the amount against the period's fee options. Used by the
 * send path, where only the resolved amount is stored on the reminder / incasso record
 * (the fee type itself is not persisted). Falls back to [BulkFeeType.FULL_YEAR_FEE] when
 * no fee option matches, so the email always states a reason.
 */
fun resolveFeeTypeFromAmount(amount: Double, period: ContributionPeriod): BulkFeeType = when (amount) {
    period.halfYearFee -> BulkFeeType.HALF_YEAR_FEE
    period.alumniFee -> BulkFeeType.ALUMNI_FEE
    else -> BulkFeeType.FULL_YEAR_FEE
}

/**
 * Human-readable reason for why a specific [BulkFeeType] applies to a member, stated
 * inline in reminder / incasso emails so the amount is never quoted without context.
 */
fun feeReason(feeType: BulkFeeType): String = when (feeType) {
    BulkFeeType.ALUMNI_FEE -> "the alumni fee, as you are an alumni member"
    BulkFeeType.HALF_YEAR_FEE -> "the half-year fee, as your membership started during the second half of the year"
    BulkFeeType.FULL_YEAR_FEE -> "the full-year fee"
}
