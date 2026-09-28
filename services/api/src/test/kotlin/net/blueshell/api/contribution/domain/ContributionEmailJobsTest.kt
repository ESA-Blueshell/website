package net.blueshell.api.contribution.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.testsupport.runJob
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper

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
}
