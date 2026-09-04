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
 * Recorded as a [ContributionReminder], because that is what it is: one asking of one member
 * for one period. Without the row the treasurer's last-sent column reads empty for somebody
 * asked a fortnight ago. The queued email is not the reminder, though — the job type decides
 * which sentence the record renders as, keeping the distinction out of the schema.
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

        // A new member is always regular, so a fee type always resolves: only honorary has
        // none, and nobody joins honorary. Stated rather than handled — a silent return here
        // would be a member who joined and was never asked, with nothing to say why.
        val feeType = requireNotNull(resolveFeeType(MemberType.REGULAR, membershipStartDate, period)) {
            "A regular membership always has a fee type"
        }
        val ask = reminders.create(
            ContributionReminder(
                user = users.findById(userId),
                contributionPeriod = period,
                feeType = feeType,
                amount = resolveFeeAmount(feeType, period),
                // From the date the membership starts, which is the date the member is told
                // they joined — not a second reading of the clock, which agrees with it only
                // while both land on the same day.
                paymentDueDate = membershipStartDate.plusWeeks(PAYMENT_WINDOW_WEEKS),
            ),
        )

        jobs.runAsync(
            EmailJobs.JoiningContribution,
            EmailJobs.JoiningContributionPayload(requireNotNull(ask.id)),
        )
    }

    companion object {
        /** How long a new member has to pay before the board follows it up. */
        private const val PAYMENT_WINDOW_WEEKS = 2L

        private val log = LoggerFactory.getLogger(JoiningContributionAskService::class.java)
    }
}
