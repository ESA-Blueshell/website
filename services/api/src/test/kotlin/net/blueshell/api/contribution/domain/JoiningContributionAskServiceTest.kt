package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionPeriodRepository
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDate

/**
 * What a new member is asked for, and that the asking is recorded.
 */
class JoiningContributionAskServiceTest {

    private val periods = mock<ContributionPeriodRepository>()
    private val users = mock<UserService>()
    private val reminders = mock<ContributionReminderService>()
    private val jobs = mock<JobQueue>()
    private val service = JoiningContributionAskService(periods, users, reminders, jobs)

    private val member = User(
        username = "newcomer",
        email = "newcomer@example.com",
        password = "dummy",
        initials = "NC",
        firstName = "New",
        lastName = "Comer",
        phoneNumber = "0612345678",
        discord = "newcomer#0001",
    )

    private fun period(
        start: LocalDate = LocalDate.of(2025, 9, 1),
        cutoff: LocalDate = LocalDate.of(2026, 2, 1),
    ) = ContributionPeriod(
        startDate = start,
        endDate = LocalDate.of(2026, 8, 31),
        halfYearCutoffDate = cutoff,
        halfYearFee = 12.50,
        fullYearFee = 20.0,
        alumniFee = 10.0,
    )

    private fun givenAPeriod(period: ContributionPeriod = period()): ContributionPeriod {
        whenever(periods.findCurrentOrLatestContributionPeriod()).thenReturn(period)
        whenever(users.findById(USER_ID)).thenReturn(member)
        whenever(reminders.create(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as ContributionReminder).also { it.id = ASK_ID }
        }
        return period
    }

    private fun recordedAsk(): ContributionReminder {
        val captor = argumentCaptor<ContributionReminder>()
        verify(reminders).create(captor.capture())
        return captor.firstValue
    }

    @Test
    fun `records the ask against the current period, priced for the joining date`() {
        val period = givenAPeriod()

        service.askOnJoining(USER_ID, LocalDate.of(2025, 10, 1))

        val ask = recordedAsk()
        assertThat(ask.user).isSameAs(member)
        assertThat(ask.contributionPeriod).isSameAs(period)
        assertThat(ask.feeType).isEqualTo(BulkFeeType.FULL_YEAR_FEE)
        assertThat(ask.amount).isEqualTo(20.0)
    }

    // The rule the treasurer's own tooling uses: the period's cutoff, not the calendar.
    @Test
    fun `prices a membership starting after the cutoff as half a year`() {
        givenAPeriod()

        service.askOnJoining(USER_ID, LocalDate.of(2026, 3, 1))

        assertThat(recordedAsk().feeType).isEqualTo(BulkFeeType.HALF_YEAR_FEE)
        assertThat(recordedAsk().amount).isEqualTo(12.50)
    }

    /**
     * Counted from the date the membership starts, which is the date the member is told they
     * joined. Reading the clock again here instead would agree with it only for as long as
     * the two reads land on the same day, and would say nothing about a membership that did
     * not start today.
     */
    @Test
    fun `gives the member two weeks from the date they joined`() {
        givenAPeriod()

        service.askOnJoining(USER_ID, LocalDate.of(2025, 10, 1))

        assertThat(recordedAsk().paymentDueDate).isEqualTo(LocalDate.of(2025, 10, 15))
    }

    @Test
    fun `queues the joining email against the ask it just recorded`() {
        givenAPeriod()

        service.askOnJoining(USER_ID, LocalDate.now())

        verify(jobs).runAsync(
            eq(EmailJobs.JoiningContribution),
            eq(EmailJobs.JoiningContributionPayload(ASK_ID)),
        )
    }

    // Nothing to quote and nothing to record it against, so the member simply joins.
    @Test
    fun `says nothing when there is no contribution period`() {
        whenever(periods.findCurrentOrLatestContributionPeriod()).thenReturn(null)

        service.askOnJoining(USER_ID, LocalDate.now())

        verify(reminders, never()).create(any())
        verifyNoInteractions(jobs)
    }

    private companion object {
        const val USER_ID = 7L
        const val ASK_ID = 42L
    }
}
