package net.blueshell.api.contribution.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.testsupport.runJob
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate

class ContributionEmailJobsTest {
    private val objectMapper = JsonMapper()
    private val emails: EmailSenderService = mockk(relaxed = true)
    private val gone = ResponseStatusException(HttpStatus.NOT_FOUND, "gone")

    @Test
    fun `a joining ask whose record is gone fails for good and sends nothing`() {
        val reminders: ContributionReminderService = mockk { every { findById(any<Long>()) } throws gone }
        val job = JoiningContributionEmailJob(objectMapper, reminders, emails, PaymentChannels(BankProperties(), "https://blueshell.test"))

        assertThat(job.jobType).isEqualTo(ContributionJobs.JoiningContribution.type)
        assertThatThrownBy {
            job.runJob(objectMapper.writeValueAsString(ContributionJobs.JoiningContributionPayload(1L)))
        }.isInstanceOf(NonRetryableJobException::class.java)
        verify(exactly = 0) { emails.send(any(), any(), any()) }
    }

    @Test
    fun `a pre-notification whose record is gone fails for good and sends nothing`() {
        val notifications: IncassoNotificationService = mockk { every { findById(any<Long>()) } throws gone }
        val job = IncassoNotificationEmailJob(objectMapper, notifications, emails)

        assertThat(job.jobType).isEqualTo(ContributionJobs.IncassoNotification.type)
        assertThatThrownBy {
            job.runJob(objectMapper.writeValueAsString(ContributionJobs.IncassoNotificationPayload(1L)))
        }.isInstanceOf(NonRetryableJobException::class.java)
        verify(exactly = 0) { emails.send(any(), any(), any()) }
    }

    @Test
    fun `a pre-notification from a run names the account it is collected from`() {
        val told =
            IncassoNotification(
                user = Entities.user(id = 1, firstName = "Mila", lastName = "Vries"),
                contributionPeriod = Entities.period(4),
                feeType = BulkFeeType.FULL_YEAR_FEE,
                amount = 25.0,
                debitDate = LocalDate.of(2026, 11, 1),
                ibanMasked = "NL00",
                mandateReference = "BLUESHELL-1-20250901",
            )
        val notifications: IncassoNotificationService = mockk { every { findById(1L) } returns told }
        val sent = slot<EmailContent>()
        every { emails.send(capture(sent), any(), any()) } returns Unit

        IncassoNotificationEmailJob(objectMapper, notifications, emails)
            .runJob(objectMapper.writeValueAsString(ContributionJobs.IncassoNotificationPayload(1L)))

        assertThat(sent.captured.markdownContent).contains("account **NL•• … ••00**", "BLUESHELL-1-20250901")
    }

    @Test
    fun `a joining ask sends the one fee it states`() {
        val ask =
            Entities.reminder(user = Entities.user(id = 1, firstName = "Mila", lastName = "Vries"), period = Entities.period(4)).apply {
                feeType = BulkFeeType.HALF_YEAR_FEE
                amount = 12.5
                paymentDueDate = LocalDate.of(2026, 11, 1)
            }
        val reminders: ContributionReminderService = mockk { every { findById(2L) } returns ask }
        val sent = slot<EmailContent>()
        every { emails.send(capture(sent), any(), any()) } returns Unit

        JoiningContributionEmailJob(objectMapper, reminders, emails, PaymentChannels(BankProperties(), "https://blueshell.test"))
            .runJob(objectMapper.writeValueAsString(ContributionJobs.JoiningContributionPayload(2L)))

        assertThat(sent.captured.markdownContent).contains("12,50")
    }
}
