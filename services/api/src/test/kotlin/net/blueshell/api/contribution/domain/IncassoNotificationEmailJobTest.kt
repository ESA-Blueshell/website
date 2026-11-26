package net.blueshell.api.contribution.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.testsupport.runJob
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The notice period the pre-notification has to arrive before the debit. */
class IncassoNotificationEmailJobTest {
    private val objectMapper = JsonMapper()
    private val notifications: IncassoNotificationService = mockk()
    private val emails: EmailSenderService = mockk(relaxed = true)
    private val debitDate = LocalDate.of(2026, 3, 15)
    private val payload = objectMapper.writeValueAsString(ContributionJobs.IncassoNotificationPayload(1L))

    @Test
    fun `a run past the notice period fails for good and sends nothing`() {
        val job = job(clockAt("2026-03-02T12:00:00Z"))
        every { notifications.findById(1L) } returns notification()

        assertThatThrownBy { job.runJob(payload) }
            .isInstanceOf(NonRetryableJobException::class.java)
            .hasMessageContaining("notice period")
            .hasMessageContaining(debitDate.toString())

        verify(exactly = 0) { emails.send(any(), any(), any()) }
    }

    @Test
    fun `a run on the last day of the notice period sends`() {
        val job = job(clockAt("2026-03-01T12:00:00Z"))
        every { notifications.findById(1L) } returns notification()

        job.runJob(payload)

        verify(exactly = 1) { emails.send(any(), any(), any()) }
    }

    @Test
    fun `a forced run past the deadline still sends`() {
        val job = job(clockAt("2026-03-02T12:00:00Z"))
        every { notifications.findById(1L) } returns notification()

        job.runJob(payload, forced = true)

        verify(exactly = 1) { emails.send(any(), any(), any()) }
    }

    @Test
    fun `by default only a debit already past is refused`() {
        val job = IncassoNotificationEmailJob(objectMapper, notifications, emails)
        every { notifications.findById(1L) } returns notification()

        assertThatThrownBy { job.runJob(payload) }.isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `a member counts as notified when a record exists for the period and debit date`() {
        val repository: IncassoNotificationRepository = mockk()
        every { repository.existsByUser_IdAndContributionPeriod_IdAndDebitDate(7L, 3L, debitDate) } returns true

        assertThat(IncassoNotificationService(repository, mockk(), mockk()).alreadyNotified(7L, 3L, debitDate)).isTrue()
    }

    private fun job(clock: Clock) = IncassoNotificationEmailJob(objectMapper, notifications, emails, 14, clock)

    private fun clockAt(instant: String) = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)

    private fun notification() =
        IncassoNotification(
            user =
                User(
                    username = "ann",
                    email = "ann@example.com",
                    password = "dummy",
                    initials = "AO",
                    firstName = "Ann",
                    lastName = "One",
                    phoneNumber = "0612345678",
                    discord = "ann#0001",
                ),
            contributionPeriod =
                ContributionPeriod(
                    startDate = LocalDate.of(2025, 9, 1),
                    endDate = LocalDate.of(2026, 8, 31),
                    halfYearCutoffDate = LocalDate.of(2026, 2, 1),
                    halfYearFee = 25.0,
                    fullYearFee = 45.0,
                    alumniFee = 10.0,
                ),
            feeType = BulkFeeType.FULL_YEAR_FEE,
            amount = 45.0,
            debitDate = debitDate,
        )
}
