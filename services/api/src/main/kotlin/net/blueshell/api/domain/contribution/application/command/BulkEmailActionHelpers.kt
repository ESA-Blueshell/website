package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.enums.MemberType
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import java.time.LocalDate

/**
 * The single decision an email-style bulk action reaches for one user.
 * Computed by the execute handler to determine side effects.
 * The [decideReminder]/[decideIncasso] functions are shared decision logic.
 * See docs/proposals/bulk-actions/REDESIGN.md §3.
 */
data class EmailBulkDecision(
    val userId: Long,
    val name: String,
    val memberType: MemberType,
    val memberSince: LocalDate?,
    val disposition: BulkRowDisposition,
    val reason: BulkRowReason?,
    val recommendedFeeType: BulkFeeType?,
    val amount: Double?,
    val lastSentOn: LocalDate?,
    /** True when the user has no email; execute must skip even if operator re-includes. */
    val emailMissing: Boolean,
)

/**
 * Guard: the cutoff date must fall within the contribution period's [startDate, endDate]
 * (inclusive). Mirrors the frontend rule so a direct API call cannot pick a cutoff outside
 * the period and skew fee-type resolution. See docs/proposals/bulk-actions/REDESIGN.md §3.
 */
internal fun requireCutoffWithinPeriod(cutoffDate: LocalDate, period: ContributionPeriod) {
    if (cutoffDate.isBefore(period.startDate) || cutoffDate.isAfter(period.endDate)) {
        throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "cutoffDate must fall within the contribution period [${period.startDate}, ${period.endDate}]",
        )
    }
}

/**
 * Validate operator-supplied fee-type overrides against the computed decisions.
 * Rejects (HTTP 400) an override for a user who is EXCLUDED/HONORARY (no fee applies)
 * or who is not in the operator's included set. Missing override → recommended type.
 * See docs/proposals/bulk-actions/REDESIGN.md §3 (fee-override validation).
 */
internal fun validateFeeTypeOverrides(
    feeTypeOverrides: Map<Long, BulkFeeType>,
    includedUserIds: Set<Long>,
    decisionsByUser: Map<Long, EmailBulkDecision>,
) {
    feeTypeOverrides.keys.forEach { userId ->
        val decision = decisionsByUser[userId]
        if (decision == null || decision.recommendedFeeType == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Fee-type override supplied for user $userId who is excluded from this action",
            )
        }
        if (userId !in includedUserIds) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Fee-type override supplied for user $userId who is not included in this action",
            )
        }
    }
}
