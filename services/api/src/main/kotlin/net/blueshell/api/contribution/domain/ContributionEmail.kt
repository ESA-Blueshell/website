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

    /**
     * Which email this member gets: the flag's default, unless the treasurer switched the row.
     * The only place a default becomes a decision, so the send and its date checks cannot
     * read it differently.
     */
    fun kind(switched: Map<Long, ContributionEmailKind>): ContributionEmailKind =
        switched[userId] ?: defaultKind

    /** A warning is a default the caller can overrule; a hard exclusion is not. */
    fun willSend(forciblyIncluded: Set<Long>): Boolean = when (disposition) {
        BulkRowDisposition.INCLUDED -> true
        BulkRowDisposition.WARNING -> userId in forciblyIncluded
        else -> false
    }
}

/**
 * What one send would do to one selection. The preview and the send read the same plan, so
 * they cannot disagree about who is written to or what they owe.
 */
data class ContributionEmailPlan(
    val contributionPeriodId: Long,
    val rows: List<ContributionEmailRow>,
    /** Selected ids resolving to nobody. No row can be drawn for them, and the send refuses them. */
    val unknownUserIds: List<Long> = emptyList(),
) {
    fun byUserId(userId: Long): ContributionEmailRow? = rows.firstOrNull { it.userId == userId }

    fun recipients(forciblyIncluded: Set<Long>): List<ContributionEmailRow> =
        rows.filter { it.willSend(forciblyIncluded) }
}
