package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.user.persistence.Membership
import java.time.Instant

/** One time a member was asked to pay for a period, a contribution reminder or an incasso notification. */
data class Ask(
    val feeType: BulkFeeType,
    val amount: Double,
    val askedAt: Instant,
)

/** What a member owes for a period, with the fee type that says why. */
data class Owed(
    val feeType: BulkFeeType,
    val amount: Double,
)

/**
 * The membership type [period] is asked at (api ADR-040): the one in force today, except that a
 * period over before the member turned honorary stays owed at the type they held then.
 */
fun askedType(
    membership: Membership,
    period: ContributionPeriod,
): MemberType {
    if (membership.memberType != MemberType.HONORARY) return membership.memberType
    val held = membership.types.filter { !it.since.isAfter(period.endDate) }.maxByOrNull { it.since }
    return held?.type ?: membership.memberType
}

/** The fee [membership] is asked for [period], with the half-year cutoff read off its start; none once honorary. */
fun resolveFeeType(
    membership: Membership,
    period: ContributionPeriod,
): BulkFeeType? = resolveFeeType(askedType(membership, period), membership.startDate, period)

/** What [membership] owes for [period]: the latest ask's amount, or before any ask what it would be asked. */
fun owed(
    membership: Membership,
    period: ContributionPeriod,
    asks: List<Ask>,
): Owed? {
    val feeType = resolveFeeType(membership, period) ?: return null
    return asks.maxByOrNull { it.askedAt }?.let { Owed(it.feeType, it.amount) } ?: Owed(feeType, resolveFeeAmount(feeType, period))
}

/** A reminder that quoted every fee option, or one written before the amount was kept, asked no one amount. */
fun ContributionReminder.asAsk(): Ask? {
    val type = feeType ?: return null
    return amount?.let { Ask(type, it, askedAt) }
}

fun IncassoNotification.asAsk(): Ask = Ask(feeType, amount, askedAt)

/** Every ask among [reminded] and [notified] that stated one amount, grouped by [key]. */
fun asksBy(
    reminded: List<ContributionReminder>,
    notified: List<IncassoNotification>,
    key: (ContributionReminder) -> Long,
    keyOf: (IncassoNotification) -> Long,
): Map<Long, List<Ask>> =
    (reminded.mapNotNull { one -> one.asAsk()?.let { key(one) to it } } + notified.map { keyOf(it) to it.asAsk() })
        .groupBy({ it.first }, { it.second })
