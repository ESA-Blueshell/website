package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.query.MembershipQuery
import net.blueshell.api.domain.user.command.ExecuteBulkEndMembershipCommand
import net.blueshell.api.domain.user.command.PreviewBulkEndMembershipCommand
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.dto.bulk.BulkActionType
import net.blueshell.api.shared.dto.bulk.BulkPreviewResult
import net.blueshell.api.shared.dto.bulk.BulkPreviewRow
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
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
class PreviewBulkEndMembershipHandler(
    private val memberships: MembershipService,
    private val users: UserService,
) : CommandHandler<PreviewBulkEndMembershipCommand, BulkPreviewResult> {
    override val commandType = PreviewBulkEndMembershipCommand::class

    @Transactional(readOnly = true)
    override fun handle(command: PreviewBulkEndMembershipCommand): BulkPreviewResult {
        // The preview's row computation now happens FRONTEND-side; the endpoint is kept
        // only to hand the FE the server's `today` (so the "started today" boundary is
        // evaluated in the server's timezone, not the browser's). We still return rows
        // and counts so existing consumers/tests keep working and remain the server truth.
        // See docs/proposals/bulk-actions/REDESIGN.md §4.
        val today = LocalDate.now()
        val rows = command.userIds.distinct().map { userId ->
            val user = users.findById(userId)
            val active = memberships.findByQuery(MembershipQuery(userId = userId)).filter { it.endDate == null }
            val endable = active.filter { it.startDate.isBefore(today) }
            BulkPreviewRow(
                userId = userId,
                name = user.fullName,
                memberType = active.firstOrNull()?.memberType,
                memberSince = active.firstOrNull()?.startDate,
                disposition = if (endable.isNotEmpty()) BulkRowDisposition.INCLUDED else BulkRowDisposition.SKIPPED,
                reason = when {
                    endable.isNotEmpty() -> null
                    active.isNotEmpty() -> BulkRowReason.STARTED_TODAY
                    else -> BulkRowReason.NO_ACTIVE_MEMBERSHIP
                },
            )
        }
        return BulkPreviewResult.of(BulkActionType.END_MEMBERSHIP, null, rows, serverToday = today)
    }
}

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
