package net.blueshell.api.contribution.domain

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper

class ContributionReminderEmailJobTest {
    private val objectMapper = JsonMapper()
    private val reminders: ContributionReminderService = mockk()
    private val emails: EmailSenderService = mockk(relaxed = true)
    private val job = ContributionReminderEmailJob(objectMapper, reminders, emails, "http://localhost:3000")

    @Test
    fun `a missing reminder is permanent, not retryable`() {
        every { reminders.findById(any<ContributionReminder.Id>()) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found")

        assertThatThrownBy {
            job.handle(objectMapper.writeValueAsString(EmailJobs.ContributionReminderPayload(1L, 2L)))
        }.isInstanceOf(NonRetryableJobException::class.java)
    }
}
