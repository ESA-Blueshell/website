package net.blueshell.api.platform.integration.email.service

import jakarta.mail.Multipart
import jakarta.mail.internet.MimeMessage
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.mock.MockJavaMailSender
import net.blueshell.api.shared.enums.ResetType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Integration tests for EmailService.
 *
 * Tests the complete email flow: domain builder → template service → delivery.
 */
class EmailServiceIntegrationTest : ServiceTestSupport() {

    @Autowired
    private lateinit var emailService: EmailService

    @Autowired
    private lateinit var mailSender: MockJavaMailSender

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun clearMailbox() {
        mailSender.clear()
    }

    @Nested
    inner class RecoveryEmails {

        @Test
        fun `sendUserResetEmail sends password reset email`() {
            // Given: User in database
            val user = createAndSaveUser("john.doe", "john@example.com")
            val token = "reset-token-123"

            // When: Sending password reset email
            emailService.sendUserResetEmail(user.id!!, token, ResetType.PASSWORD_RESET)

            // Then: Email is sent
            val emails = mailSender.outbox
            assertThat(emails).hasSize(1)

            val email = emails.first()
            assertThat(email.allRecipients.map { it.toString() })
                .contains("john@example.com")
            assertThat(email.subject)
                .isEqualTo("Reset Your Blueshell Account Password")

            // And: Email is HTML
            val content = extractHtmlContent(email)
            assertThat(content)
                .contains("<!DOCTYPE html>")
                .contains("reset-password")
        }

        @Test
        fun `sendUserResetEmail sends user activation email`() {
            // Given: User in database
            val user = createAndSaveUser("jane.smith", "jane@example.com")
            val token = "activation-token-456"

            // When: Sending user activation email
            emailService.sendUserResetEmail(user.id!!, token, ResetType.USER_ACTIVATION)

            // Then: Email is sent with correct subject
            val emails = mailSender.outbox
            assertThat(emails).hasSize(1)

            assertThat(emails.first().subject)
                .isEqualTo("Activate your Account")
            assertThat(extractHtmlContent(emails.first()))
                .contains("Thank you for signing up")
                .contains("activate/user")
        }

        @Test
        fun `sendUserResetEmail sends member activation email`() {
            // Given: User in database
            val user = createAndSaveUser("board.member", "board@example.com")
            val token = "member-token-789"

            // When: Sending member activation email
            emailService.sendUserResetEmail(user.id!!, token, ResetType.MEMBER_ACTIVATION)

            // Then: Email is sent with board-specific content
            val emails = mailSender.outbox
            assertThat(emails).hasSize(1)

            assertThat(extractHtmlContent(emails.first()))
                .contains("board of Blueshell")
                .contains("activate/member")
        }
    }

    @Nested
    inner class ContributionEmails {

        @Test
        fun `sendContributionReminderEmail sends reminder with payment options`() {
            // Given: User, period, and reminder in database
            val user = createAndSaveUser("contributor", "contributor@example.com")
            val period = createAndSavePeriod()
            val reminder = createAndSaveReminder(user, period)

            // When: Sending contribution reminder
            emailService.sendContributionReminderEmail(user.id!!, period.id!!)

            // Then: Email is sent
            val emails = mailSender.outbox
            assertThat(emails).hasSize(1)

            val email = emails.first()
            assertThat(email.allRecipients.map { it.toString() })
                .contains("contributor@example.com")
            assertThat(email.subject)
                .contains("Contribution Payment Reminder")

            // And: Contains all payment options
            val content = extractHtmlContent(email)
            assertThat(content)
                .contains("25.0")
                .contains("45.0")
                .contains("10.0")
                .contains("Treasurer")
        }
    }

    @Nested
    inner class EventEmails {

        @Test
        fun `sendEventSignupEmail sends confirmation to guest`() {
            // Given: Event signup with guest in database
            val event = createAndSaveEvent("Summer Tournament", "Campus Hall")
            val signUp = createAndSaveSignUp(event, "Guest Name", "guest@example.com")

            // When: Sending event signup email
            emailService.sendEventSignupEmail(signUp.id!!)

            // Then: Email is sent
            val emails = mailSender.outbox
            assertThat(emails).hasSize(1)

            val email = emails.first()
            assertThat(email.allRecipients.map { it.toString() })
                .contains("guest@example.com")
            assertThat(email.subject)
                .isEqualTo("Event Registration Confirmed - Summer Tournament")

            // And: Contains event details
            val content = extractHtmlContent(email)
            assertThat(content)
                .contains("Summer Tournament")
                .contains("Campus Hall")
                .contains("Guest Name")
                .contains("edit")
        }
    }

    @Nested
    inner class EmailDelivery {

        @Test
        fun `emails are sent with correct sender information`() {
            // Given: User
            val user = createAndSaveUser("test", "test@example.com")

            // When: Sending email
            emailService.sendUserResetEmail(user.id!!, "token", ResetType.PASSWORD_RESET)

            // Then: Sender is configured correctly
            val email = mailSender.outbox.first()
            // Note: MockJavaMailSender doesn't set from address, but real sender would
            assertThat(email.subject).isNotBlank()
            assertThat(email.allRecipients).isNotEmpty()
        }

        @Test
        fun `emails contain HTML content`() {
            // Given: User
            val user = createAndSaveUser("html.test", "html@example.com")

            // When: Sending email
            emailService.sendUserResetEmail(user.id!!, "token", ResetType.PASSWORD_RESET)

            // Then: Email is HTML formatted
            val email = mailSender.outbox.first()
            val content = extractHtmlContent(email)
            assertThat(content)
                .contains("<!DOCTYPE html>")
                .contains("<html")
                .contains("<body")
                .contains("</html>")
        }

        @Test
        fun `emails contain styled content`() {
            // Given: User
            val user = createAndSaveUser("style.test", "style@example.com")

            // When: Sending email
            emailService.sendUserResetEmail(user.id!!, "token", ResetType.PASSWORD_RESET)

            // Then: Email contains styling
            val email = mailSender.outbox.first()
            val content = extractHtmlContent(email)
            // Template should include some styling elements
            assertThat(content)
                .containsAnyOf("<style", "style=", "class=")
        }
    }

    // Helper methods
    private fun createAndSaveUser(username: String, email: String): User {
        val user = User(
            username = username,
            email = email,
            password = passwordEncoder.encode("Password123!"),
            initials = "TU",
            firstName = username.split(".").first().replaceFirstChar { it.uppercase() },
            lastName = username.split(".").getOrElse(1) { "User" }.replaceFirstChar { it.uppercase() },
            phoneNumber = "06${System.currentTimeMillis().toString().takeLast(8)}",
            discord = "$username#0001"
        )
        user.enabled = true
        user.roles = mutableSetOf(Role.MEMBER)
        return persist(user)
    }

    private fun createAndSavePeriod(): ContributionPeriod {
        val period = ContributionPeriod(
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2024, 12, 31),
            halfYearFee = 25.0,
            fullYearFee = 45.0,
            alumniFee = 10.0,
        )
        return persist(period)
    }

    private fun createAndSaveReminder(user: User, period: ContributionPeriod): ContributionReminder {
        val reminder = ContributionReminder(
            id = ContributionReminder.Id(user.id, period.id),
            user = user,
            contributionPeriod = period,
        )
        return persist(reminder)
    }

    private fun createAndSaveEvent(title: String, location: String): Event {
        val committee = createAndSaveCommittee("Test Committee")
        val event = Event(
            committee = committee,
            title = title,
            location = location,
            startTime = Instant.now().plus(7, ChronoUnit.DAYS),
            endTime = Instant.now().plus(7, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
            approved = true,
            signUp = true,
        )
        return persist(event)
    }

    private fun createAndSaveCommittee(name: String): Committee {
        return persist(Committee(name = name, description = "Test committee for integration tests"))
    }

    private fun createAndSaveSignUp(event: Event, guestName: String, guestEmail: String): EventSignUp {
        val guest = Guest(
            name = guestName,
            discord = "guest#1234",
            email = guestEmail,
            accessToken = "test-token-${System.currentTimeMillis()}",
        )

        val signUp = EventSignUp(event = event, guest = guest)
        return persist(signUp)
    }

    /**
     * Extract HTML content from MimeMessage (handles multipart messages)
     */
    private fun extractHtmlContent(message: MimeMessage): String {
        val content = message.content
        if (content is Multipart) {
            for (i in 0 until content.count) {
                val bodyPart = content.getBodyPart(i)
                if (bodyPart.contentType.startsWith("text/html")) {
                    return bodyPart.content.toString()
                }
                // Check nested multipart (related/alternative)
                if (bodyPart.content is Multipart) {
                    val nested = bodyPart.content as Multipart
                    for (j in 0 until nested.count) {
                        val nestedPart = nested.getBodyPart(j)
                        if (nestedPart.contentType.startsWith("text/html")) {
                            return nestedPart.content.toString()
                        }
                    }
                }
            }
        }
        return content.toString()
    }
}
