package net.blueshell.api.contribution.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate

class ContributionReminderEmailJobTest {
    private val objectMapper = JsonMapper()
    private val reminders: ContributionReminderService = mockk()
    private val emails: EmailSenderService = mockk(relaxed = true)
    private val job = ContributionReminderEmailJob(objectMapper, reminders, emails, PaymentChannels(BankProperties(), "https://blueshell.test"))

    @Test
    fun `a missing reminder is permanent, not retryable`() {
        every { reminders.findById(any<Long>()) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found")

        assertThatThrownBy {
            job.handle(objectMapper.writeValueAsString(EmailJobs.ContributionReminderPayload(1L)))
        }.isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `an ask that stated a fee quotes that amount and the reason for it`() {
        val sent = handle(
            reminder(BulkFeeType.FULL_YEAR_FEE, 45.0, LocalDate.of(2026, 10, 1)),
        )

        assertThat(sent.markdownContent).contains("**Amount due: \u20AC45,00**")
        assertThat(sent.markdownContent).contains("1 October 2026")
    }

    @Test
    fun `an ask that stated none lists the period's fee options`() {
        val sent = handle(reminder(null, null, null))

        assertThat(sent.markdownContent).doesNotContain("Amount due")
        assertThat(sent.markdownContent).contains("45,00")
        assertThat(sent.markdownContent).contains("25,00")
    }

    private fun handle(reminder: ContributionReminder): EmailContent {
        every { reminders.findById(1L) } returns reminder
        val sent = slot<EmailContent>()
        every { emails.send(capture(sent), any(), any()) } returns Unit

        job.handle(objectMapper.writeValueAsString(EmailJobs.ContributionReminderPayload(1L)))

        return sent.captured
    }

    private fun reminder(feeType: BulkFeeType?, amount: Double?, dueDate: LocalDate?) = ContributionReminder(
        user = User(
            username = "ann",
            email = "ann@example.com",
            password = "dummy",
            initials = "AO",
            firstName = "Ann",
            lastName = "One",
            phoneNumber = "0612345678",
            discord = "ann#0001",
        ),
        contributionPeriod = ContributionPeriod(
            startDate = LocalDate.of(2025, 9, 1),
            endDate = LocalDate.of(2026, 8, 31),
            halfYearCutoffDate = LocalDate.of(2026, 2, 1),
            halfYearFee = 25.0,
            fullYearFee = 45.0,
            alumniFee = 10.0,
        ),
        feeType = feeType,
        amount = amount,
        paymentDueDate = dueDate,
    )
}
