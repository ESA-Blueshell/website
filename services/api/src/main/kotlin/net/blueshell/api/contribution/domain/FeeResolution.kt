package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.user.persistence.Membership
import java.time.LocalDate

/**
 * Fee type applicable to a member, or null for HONORARY members who are excluded.
 *
 * The cutoff is the period's own [ContributionPeriod.halfYearCutoffDate] rather than an
 * argument, so a send made in February and one made in June cannot price the same
 * membership differently. A REGULAR membership starting after the cutoff pays the
 * half-year fee; one starting on the cutoff itself pays the full year.
 */
fun resolveFeeType(
    memberType: MemberType,
    membershipStartDate: LocalDate?,
    period: ContributionPeriod,
): BulkFeeType? =
    when (memberType) {
        MemberType.REGULAR -> {
            if (membershipStartDate != null && membershipStartDate > period.halfYearCutoffDate) {
                BulkFeeType.HALF_YEAR_FEE
            } else {
                BulkFeeType.FULL_YEAR_FEE
            }
        }
        MemberType.ALUMNI -> BulkFeeType.ALUMNI_FEE
        MemberType.HONORARY -> null // Excluded
    }

/** The fee [membership] is asked on [day]: its membership type that day, with the cutoff read off its start (api ADR-040). */
fun resolveFeeType(
    membership: Membership,
    day: LocalDate,
    period: ContributionPeriod,
): BulkFeeType? = resolveFeeType(membership.typeOn(day), membership.startDate, period)

/**
 * Resolves the € amount for a given [BulkFeeType] from the contribution period.
 * This is a pure function with no side effects.
 *
 * Mirrored in the browser by `effectiveAmount` in `services/frontend/src/utils/feePreview.ts`,
 * so a row re-prices as the treasurer changes its fee type without a round trip. Two
 * implementations of one rule: changing this one means changing that one.
 */
fun resolveFeeAmount(
    feeType: BulkFeeType,
    period: ContributionPeriod,
): Double =
    when (feeType) {
        BulkFeeType.FULL_YEAR_FEE -> period.fullYearFee
        BulkFeeType.HALF_YEAR_FEE -> period.halfYearFee
        BulkFeeType.ALUMNI_FEE -> period.alumniFee
    }

/**
 * Human-readable reason for why a specific [BulkFeeType] applies to a member, stated
 * inline in both bulk contribution emails so the amount is never quoted without context.
 */
fun feeReason(feeType: BulkFeeType): String =
    when (feeType) {
        BulkFeeType.ALUMNI_FEE -> "the alumni fee, as you are an alumni member"
        BulkFeeType.HALF_YEAR_FEE -> "the half-year fee, as your membership started during the second half of the year"
        BulkFeeType.FULL_YEAR_FEE -> "the full-year fee"
    }
