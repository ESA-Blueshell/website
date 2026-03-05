package net.blueshell.api.platform.integration.email.service

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.email.EmailTransportClient
import net.blueshell.api.platform.integration.email.application.service.EmailService
import net.blueshell.api.shared.enums.ResetType
import net.blueshell.api.shared.job.NonRetryableJobException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Unit tests verifying that missing-entity (404) errors are converted to
 * NonRetryableJobException so the job system marks them DEAD on the first attempt.
 */
class EmailServiceMissingEntityTest {

    private val templateService = mockk<EmailTemplateService>(relaxed = true)
    private val emailClient = mockk<EmailTransportClient>(relaxed = true)
    private val users = mockk<UserService>()
    private val reminders = mockk<ContributionReminderService>()
    private val eventSignUps = mockk<EventSignUpService>()
    private val emailService = mockk<EmailService>(relaxed = true)

    private val emailSenderService = EmailSenderService(
        templateService = templateService,
        emailClient = emailClient,
        users = users,
        reminders = reminders,
        eventSignUps = eventSignUps,
        emailService = emailService,
        frontendUrl = "http://localhost:3000",
        appUrl = "http://localhost:8080"
    )

    @Test
    fun `sendUserResetEmail throws NonRetryableJobException when user not found`() {
        every { users.findById(99L) } throws ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        assertThatThrownBy { emailSenderService.sendUserResetEmail(99L, "token", ResetType.PASSWORD_RESET) }
            .isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `sendEventSignupEmail throws NonRetryableJobException when signup not found`() {
        every { eventSignUps.findById(42L) } throws ResponseStatusException(HttpStatus.NOT_FOUND, "EventSignUp not found")

        assertThatThrownBy { emailSenderService.sendEventSignupEmail(42L, "guest-token") }
            .isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `sendContributionReminderEmail throws NonRetryableJobException when reminder not found`() {
        every { reminders.findById(any<ContributionReminder.Id>()) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found")

        assertThatThrownBy { emailSenderService.sendContributionReminderEmail(1L, 2L) }
            .isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `non-404 ResponseStatusException is not wrapped and remains retryable`() {
        every { users.findById(99L) } throws ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Server error")

        assertThatThrownBy { emailSenderService.sendUserResetEmail(99L, "token", ResetType.PASSWORD_RESET) }
            .isInstanceOf(ResponseStatusException::class.java)
            .isNotInstanceOf(NonRetryableJobException::class.java)
    }
}
