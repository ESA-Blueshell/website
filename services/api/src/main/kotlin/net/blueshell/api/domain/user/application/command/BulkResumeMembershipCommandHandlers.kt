package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.ExecuteBulkResumeMembershipCommand
import net.blueshell.api.domain.user.command.PreviewBulkResumeMembershipCommand
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.dto.bulk.BulkActionType
import net.blueshell.api.shared.dto.bulk.BulkPreviewResult
import net.blueshell.api.shared.dto.bulk.BulkPreviewRow
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.enums.MemberType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Determine how a single user's membership will be treated by the resume/start-new action.
 * Called by both preview and execute handlers with the same inputs.
 *
 * @param memberships All (active, non-deleted) membership rows for the user.
 * @param basisPeriodStart Start date of the globally most-recent ContributionPeriod.
 * @param basisPeriodEnd End date of the globally most-recent ContributionPeriod.
 * @return A sealed [ResumeOutcome] describing what will happen.
 */
private sealed class ResumeOutcome {
    /** User already has an active membership — skip. */
    object AlreadyActive : ResumeOutcome()

    /** User's most-recent membership ended within the basis period — resume it. */
    data class Resume(val membership: Membership) : ResumeOutcome()

    /** No resumable membership found — insert a new one. */
    data class StartNew(val copyFrom: Membership?) : ResumeOutcome()
}

private fun classifyUser(
    memberships: List<Membership>,
    basisPeriodStart: LocalDate,
    basisPeriodEnd: LocalDate,
): ResumeOutcome {
    // Already active?
    if (memberships.any { it.endDate == null }) return ResumeOutcome.AlreadyActive

    // Pick latest membership by startDate
    val latest = memberships.maxByOrNull { it.startDate }

    // If latest membership ended within [basisPeriodStart, basisPeriodEnd], resume it
    if (latest != null) {
        val endDate = latest.endDate
        if (endDate != null && !endDate.isBefore(basisPeriodStart) && !endDate.isAfter(basisPeriodEnd)) {
            return ResumeOutcome.Resume(latest)
        }
    }

    // Otherwise start new — copy memberType/incasso from latest if available
    return ResumeOutcome.StartNew(copyFrom = latest)
}

@Component
class PreviewBulkResumeMembershipHandler(
    private val memberships: MembershipService,
    private val users: UserService,
    private val periods: ContributionPeriodService,
) : CommandHandler<PreviewBulkResumeMembershipCommand, BulkPreviewResult> {
    override val commandType = PreviewBulkResumeMembershipCommand::class

    @Transactional(readOnly = true)
    override fun handle(command: PreviewBulkResumeMembershipCommand): BulkPreviewResult {
        val basisPeriod = periods.findLatest()
        if (basisPeriod == null) {
            // No contribution period at all — every row is SKIPPED
            val rows = command.userIds.distinct().map { userId ->
                val user = users.findById(userId)
                BulkPreviewRow(
                    userId = userId,
                    name = user.fullName,
                    disposition = BulkRowDisposition.SKIPPED,
                    reason = BulkRowReason.NO_CONTRIBUTION_PERIOD,
                )
            }
            return BulkPreviewResult.of(BulkActionType.RESUME_MEMBERSHIP, null, rows)
        }

        val rows = command.userIds.distinct().map { userId ->
            val user = users.findById(userId)
            val userMemberships = memberships.findByUserId(userId)
            val outcome = classifyUser(userMemberships, basisPeriod.startDate, basisPeriod.endDate)

            when (outcome) {
                is ResumeOutcome.AlreadyActive -> {
                    val active = userMemberships.first { it.endDate == null }
                    BulkPreviewRow(
                        userId = userId,
                        name = user.fullName,
                        memberType = active.memberType,
                        memberSince = active.startDate,
                        disposition = BulkRowDisposition.SKIPPED,
                        reason = BulkRowReason.ALREADY_ACTIVE,
                    )
                }
                is ResumeOutcome.Resume -> {
                    BulkPreviewRow(
                        userId = userId,
                        name = user.fullName,
                        memberType = outcome.membership.memberType,
                        memberSince = outcome.membership.startDate,
                        disposition = BulkRowDisposition.INCLUDED,
                        reason = BulkRowReason.WILL_RESUME,
                    )
                }
                is ResumeOutcome.StartNew -> {
                    val memberType = outcome.copyFrom?.memberType ?: MemberType.REGULAR
                    BulkPreviewRow(
                        userId = userId,
                        name = user.fullName,
                        memberType = memberType,
                        memberSince = outcome.copyFrom?.startDate,
                        disposition = BulkRowDisposition.INCLUDED,
                        reason = BulkRowReason.WILL_START_NEW,
                    )
                }
            }
        }
        return BulkPreviewResult.of(BulkActionType.RESUME_MEMBERSHIP, null, rows)
    }
}

@Component
class ExecuteBulkResumeMembershipHandler(
    private val memberships: MembershipService,
    private val users: UserService,
    private val periods: ContributionPeriodService,
) : CommandHandler<ExecuteBulkResumeMembershipCommand, BulkActionResult> {
    override val commandType = ExecuteBulkResumeMembershipCommand::class

    @Transactional
    override fun handle(command: ExecuteBulkResumeMembershipCommand): BulkActionResult {
        val basisPeriod = periods.findLatest()
        if (basisPeriod == null) {
            // All skipped — nothing to do
            return BulkActionResult(applied = 0, skipped = command.userIds.distinct().size, queued = 0)
        }

        val today = LocalDate.now()
        var applied = 0
        var skipped = 0

        command.userIds.distinct().forEach { userId ->
            val userMemberships = memberships.findByUserId(userId)
            val outcome = classifyUser(userMemberships, basisPeriod.startDate, basisPeriod.endDate)

            when (outcome) {
                is ResumeOutcome.AlreadyActive -> skipped++
                is ResumeOutcome.Resume -> {
                    outcome.membership.endDate = null
                    memberships.update(outcome.membership)
                    applied++
                }
                is ResumeOutcome.StartNew -> {
                    val user = users.findById(userId)
                    val memberType = outcome.copyFrom?.memberType ?: MemberType.REGULAR
                    val incasso = outcome.copyFrom?.incasso ?: false
                    memberships.create(
                        Membership(
                            user = user,
                            startDate = today,
                            endDate = null,
                            memberType = memberType,
                            incasso = incasso,
                        )
                    )
                    applied++
                }
            }
        }

        return BulkActionResult(applied = applied, skipped = skipped, queued = 0)
    }
}
