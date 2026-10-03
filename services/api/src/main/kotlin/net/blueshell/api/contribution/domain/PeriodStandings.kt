package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionRepository
import net.blueshell.api.user.api.MembershipService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/** How the current period stands: who is a member today, who has paid for it, and who is still to. */
data class PeriodStanding(
    val periodId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val members: Int,
    val paid: Int,
    val stillToPay: Int,
    /** Still to pay, and a member only since this period began: their first contribution is pending. */
    val pendingFirstContribution: Int,
)

@Service
class PeriodStandings(
    private val periods: ContributionPeriodService,
    private val memberships: MembershipService,
    private val contributions: ContributionRepository,
    private val clock: Clock,
) {
    /** The current period's standing, or nothing before any period exists. */
    @Transactional(readOnly = true)
    fun current(): PeriodStanding? {
        val period = periods.findLatest() ?: return null
        val today = LocalDate.now(clock)
        val members = memberships.findOverlappingWithMembers(today, today).map { it.userId }.toSet()
        val paid = contributions.findByIdContributionPeriodId(requireNotNull(period.id)).map { it.userId }.toSet()
        val unpaid = members - paid
        val joined = memberships.findByUserIds(unpaid).mapValues { (_, held) -> held.minOf { it.startDate } }
        return PeriodStanding(
            periodId = requireNotNull(period.id),
            startDate = period.startDate,
            endDate = period.endDate,
            members = members.size,
            paid = paid.size,
            stillToPay = unpaid.size,
            pendingFirstContribution = unpaid.count { id -> joined[id]?.let { !it.isBefore(period.startDate) } == true },
        )
    }
}
