package net.blueshell.api.domain.contribution.domain

import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.enums.MemberType
import java.time.LocalDate

/**
 * Resolves the fee amount owed by a member for a contribution period,
 * accounting for member type and membership start date relative to cutoff.
 *
 * Rules:
 * - REGULAR: half-year fee if membership started on/after cutoff, else full-year
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
        if (membershipStartDate != null && membershipStartDate >= cutoffDate) {
            period.halfYearFee
        } else {
            period.fullYearFee
        }
    }
    MemberType.ALUMNI -> period.alumniFee
    MemberType.HONORARY -> null // Excluded
    MemberType.NONE -> period.fullYearFee // Fallback for no membership
}
