package net.blueshell.api.contribution.domain

import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.dto.bulk.FeeCycleGroup
import net.blueshell.api.shared.enums.MemberType
import java.time.LocalDate

/**
 * One member's place in a fee cycle: which side of the direct-debit partition they are on,
 * what they owe and why, and whether they will be written to at all.
 *
 * A row that will not be written to carries its reason rather than being left out, so a
 * member's absence from the send is visible instead of silent.
 */
data class FeeCycleParticipant(
    val userId: Long,
    val name: String,
    val memberType: MemberType,
    /** Start date of the membership every decision here was judged against. */
    val memberSince: LocalDate?,
    val group: FeeCycleGroup,
    val disposition: BulkRowDisposition,
    val reason: BulkRowReason?,
    /** Null exactly when no fee applies, which is when the member is honorary. */
    val feeType: BulkFeeType?,
    /** Derived from [feeType] and the period. Never typed by anyone. */
    val amount: Double?,
    /** When this member was last asked for this period, if they have been. */
    val lastAskedOn: LocalDate?,
) {
    /**
     * Whether the cycle will write to this member.
     *
     * `EXCLUDED` is not overridable and there is nothing else a fee cycle skips: every
     * member in the plan has not paid, which is what put them there.
     */
    val willSend: Boolean get() = disposition == BulkRowDisposition.INCLUDED
}

/**
 * The whole cycle for one period: every member of it who has not paid, partitioned.
 *
 * Preview and send are the same plan read twice, so they cannot disagree about who is
 * included or what they owe.
 */
data class FeeCyclePlan(
    val contributionPeriodId: Long,
    val participants: List<FeeCycleParticipant>,
) {
    fun group(group: FeeCycleGroup): List<FeeCycleParticipant> = participants.filter { it.group == group }

    fun byUserId(userId: Long): FeeCycleParticipant? = participants.firstOrNull { it.userId == userId }

    val recipients: List<FeeCycleParticipant> get() = participants.filter { it.willSend }
}
