package net.blueshell.api.contribution.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.enums.MemberType
import java.time.LocalDate

/** Which of the two payment emails a member gets. */
@Schema(name = "ContributionEmailKind", enumAsRef = true)
enum class ContributionEmailKind {
    /** Asks the member to transfer what they owe, by a date. */
    REMINDER,

    /** Tells the member what will be taken, and when. Asks for nothing. */
    INCASSO_NOTIFICATION,
}

/** One selected member: which email they get, what they owe, and whether they are written to. */
data class ContributionEmailRow(
    val userId: Long,
    val name: String,
    val memberType: MemberType,
    val memberSince: LocalDate?,
    val disposition: BulkRowDisposition,
    val reason: BulkRowReason?,
    /** From the member's direct-debit flag. A default the treasurer may overrule per row. */
    val defaultKind: ContributionEmailKind,
    /** Null exactly when no fee applies, which is when the member is honorary. */
    val feeType: BulkFeeType?,
    val amount: Double?,
    // Both, so switching a row's email re-reads its history without a round trip.
    val lastRemindedOn: LocalDate?,
    val lastNotifiedOn: LocalDate?,
) {
    val isHardExcluded: Boolean get() = disposition == BulkRowDisposition.EXCLUDED

    /** A warning is a default the operator can overrule; a hard exclusion is not. */
    fun willSend(forciblyIncluded: Set<Long>): Boolean = when (disposition) {
        BulkRowDisposition.INCLUDED -> true
        BulkRowDisposition.WARNING -> userId in forciblyIncluded
        else -> false
    }
}

/**
 * What one send would do to one selection. The table and the send read the same plan, so
 * they cannot disagree about who is written to or what they owe.
 */
data class ContributionEmailPlan(
    val contributionPeriodId: Long,
    val rows: List<ContributionEmailRow>,
) {
    fun byUserId(userId: Long): ContributionEmailRow? = rows.firstOrNull { it.userId == userId }

    fun recipients(forciblyIncluded: Set<Long>): List<ContributionEmailRow> =
        rows.filter { it.willSend(forciblyIncluded) }
}
