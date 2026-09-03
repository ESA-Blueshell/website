package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.JoiningContributionAsk
import net.blueshell.api.contribution.persistence.ContributionPeriodRepository
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import net.blueshell.api.user.api.UserService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Asks a new member for their contribution, and records having asked.
 *
 * The ask is recorded as a [ContributionReminder] like any other, because that is what it is:
 * one asking of one member to pay for one period. Without the row the treasurer's last-sent
 * column would read empty for somebody asked a fortnight ago, and the next bulk send would go
 * out as a first request carrying a different hand-typed deadline than the one the member
 * already has.
 *
 * The email it queues is not the reminder, though. The job type says which of the two the
 * record is rendered as, which keeps the distinction out of the schema — the record really is
 * the same thing, only the sentence differs.
 */
@Service
class JoiningContributionAskService(
    private val periods: ContributionPeriodRepository,
    private val users: UserService,
    private val reminders: ContributionReminderService,
    private val jobs: TrackedJobDispatcher,
) : JoiningContributionAsk {

    @Transactional
    override fun askOnJoining(userId: Long, membershipStartDate: LocalDate) {
        val period = periods.findCurrentOrLatestContributionPeriod()
        if (period == null) {
            log.info("No contribution period, so user {} joins without being asked for a fee", userId)
            return
        }

        // A new member is always regular, so a fee type always resolves: only honorary has none.
        val feeType = resolveFeeType(MemberType.REGULAR, membershipStartDate, period) ?: return
        val ask = reminders.create(
            ContributionReminder(
                user = users.findById(userId),
                contributionPeriod = period,
                feeType = feeType,
                amount = resolveFeeAmount(feeType, period),
                paymentDueDate = LocalDate.now().plus(PAYMENT_WINDOW),
            ),
        )

        jobs.runAsync(
            EmailJobs.JoiningContribution,
            EmailJobs.JoiningContributionPayload(requireNotNull(ask.id)),
        )
    }

    companion object {
        /** How long a new member has to pay before the board follows it up. */
        private val PAYMENT_WINDOW: java.time.Period = java.time.Period.ofWeeks(2)

        private val log = LoggerFactory.getLogger(JoiningContributionAskService::class.java)
    }
}
