package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.query.MembershipQuery
import net.blueshell.api.domain.user.command.ExecuteBulkEndMembershipCommand
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.Role
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
    private val users: UserService,
) : CommandHandler<ExecuteBulkEndMembershipCommand, BulkActionResult> {
    override val commandType = ExecuteBulkEndMembershipCommand::class

    @Transactional
    override fun handle(command: ExecuteBulkEndMembershipCommand): BulkActionResult {
        val today = LocalDate.now()
        var applied = 0
        var skipped = 0
        command.userIds.distinct().forEach { userId ->
            // Poisoned-batch guard: an unknown userId must not abort the whole batch
            // (users.findById throws 404). Treat it as skipped and continue.
            if (!users.existsById(userId)) {
                skipped++
                return@forEach
            }
            // Server-side mirror of the client preview's protection rules: users with a
            // role above Member (committee/board/treasurer/admin) and honorary members
            // keep their membership. The FE shows these rows as EXCLUDED; execute
            // re-checks against live data so the protection cannot be bypassed.
            val user = users.findById(userId)
            if (user.roles.any { it in PROTECTED_ROLES }) {
                skipped++
                return@forEach
            }
            val endable = endableMemberships(memberships, userId, today)
            if (endable.isEmpty() || endable.any { it.memberType == MemberType.HONORARY }) {
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

    private companion object {
        /** Roles whose holders cannot have their membership ended. */
        private val PROTECTED_ROLES = setOf(Role.COMMITTEE, Role.BOARD, Role.TREASURER, Role.ADMIN)
    }
}
