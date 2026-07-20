package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.query.MembershipQuery
import net.blueshell.api.domain.user.command.ExecuteBulkEndMembershipCommand
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Shared decision for end-membership: a user's active (endDate=null) memberships that
 * started before [actionDate] are endable. Both preview and execute filter with the
 * same predicate and the same single [actionDate] so they cannot diverge across the
 * midnight boundary within a request. See docs/proposals/bulk-actions/REDESIGN.md §3.
 */
private fun endableMemberships(
    memberships: MembershipService,
    userId: Long,
    actionDate: LocalDate,
): List<Membership> =
    memberships.findByQuery(MembershipQuery(userId = userId))
        .filter { it.endDate == null && it.startDate.isBefore(actionDate) }

@Component
class ExecuteBulkEndMembershipHandler(
    private val memberships: MembershipService,
) : CommandHandler<ExecuteBulkEndMembershipCommand, BulkActionResult> {
    override val commandType = ExecuteBulkEndMembershipCommand::class

    @Transactional
    override fun handle(command: ExecuteBulkEndMembershipCommand): BulkActionResult {
        val today = LocalDate.now()
        var applied = 0
        var skipped = 0
        command.userIds.distinct().forEach { userId ->
            val endable = endableMemberships(memberships, userId, today)
            if (endable.isEmpty()) {
                skipped++
            } else {
                endable.forEach { membership ->
                    membership.endDate = today
                    memberships.update(membership)
                }
                applied++
            }
        }
        return BulkActionResult(applied = applied, skipped = skipped, queued = 0)
    }
}
