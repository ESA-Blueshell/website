package net.blueshell.api.platform.integration.email.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.email.adapter.EmailTransportClient
import net.blueshell.api.platform.integration.email.application.service.EmailSenderService
import net.blueshell.api.platform.integration.email.application.service.EmailService
import net.blueshell.api.platform.integration.email.application.service.EmailTemplateService
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.NonRetryableJobException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * A preview must be exactly a render: no token issued, no outbox row, no transport. These
 * assert the absences, because an absence is what makes it safe to show.
 */
class RecoveryEmailPreviewTest {

    private val templateService = mockk<EmailTemplateService>()
    private val emailClient = mockk<EmailTransportClient>(relaxed = true)
    private val users = mockk<UserService>()
    private val reminders = mockk<ContributionReminderService>()
    private val eventSignUps = mockk<EventSignUpService>()
    private val emailService = mockk<EmailService>(relaxed = true)

    private val service = EmailSenderService(
        templateService = templateService,
        emailClient = emailClient,
        users = users,
        reminders = reminders,
        eventSignUps = eventSignUps,
        emailService = emailService,
        frontendUrl = "https://esa-blueshell.nl",
        appUrl = "https://esa-blueshell.nl/api",
        senderName = "Blueshell",
        senderAddress = "no-reply@esa-blueshell.nl",
        defaultReplyTo = "sitecie@blueshell.utwente.nl",
    )

    private val alice = User(
        username = "alice",
        email = "alice@example.com",
        password = "hash",
        initials = "A",
        firstName = "Alice",
        lastName = "Regular",
    )

    private fun renderPassesThrough() {
        every { templateService.createEmail(any(), any(), any(), any()) } answers {
            // Hand back the markdown so assertions can inspect what was templated.
            "<html>" + arg<String>(3) + "</html>"
        }
    }

    @Test
    fun `the link carries the placeholder rather than a token`() {
        every { users.findById(7L) } returns alice
        renderPassesThrough()

        val preview = service.previewRecoveryEmail(7L, TokenPurpose.USER_ACTIVATION)

        assertThat(preview.html).contains("PREVIEW-ONLY-NO-TOKEN-ISSUED")
        assertThat(preview.linkPlaceholder).isEqualTo("PREVIEW-ONLY-NO-TOKEN-ISSUED")
        assertThat(preview.subject).isEqualTo("Activate your Account")
        assertThat(preview.purpose).isEqualTo(TokenPurpose.USER_ACTIVATION)
        assertThat(preview.recipientEmail).isEqualTo("alice@example.com")
        assertThat(preview.recipientName).isEqualTo("Alice Regular")
    }

    @Test
    fun `nothing is recorded and nothing is sent`() {
        every { users.findById(7L) } returns alice
        renderPassesThrough()

        service.previewRecoveryEmail(7L, TokenPurpose.MEMBER_ACTIVATION)

        verify(exactly = 0) { emailService.createPending(any(), any(), any()) }
        verify(exactly = 0) { emailClient.send(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a preview renders through the same template as a send`() {
        every { users.findById(7L) } returns alice
        renderPassesThrough()

        service.previewRecoveryEmail(7L, TokenPurpose.PASSWORD_RESET)

        verify(exactly = 1) {
            templateService.createEmail(
                "alice@example.com",
                "Alice Regular",
                "Reset Your Blueshell Account Password",
                any(),
            )
        }
    }

    @Test
    fun `a signup continuation email cannot be previewed either`() {
        every { users.findById(7L) } returns alice

        assertThatThrownBy { service.previewRecoveryEmail(7L, TokenPurpose.SIGNUP_CONTINUATION) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a missing user is reported rather than rendered`() {
        every { users.findById(99L) } throws ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        assertThatThrownBy { service.previewRecoveryEmail(99L, TokenPurpose.USER_ACTIVATION) }
            .isInstanceOf(NonRetryableJobException::class.java)
    }
}
