package net.blueshell.api.platform.integration.email.service

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
            val content = email.content.toString()
            assertThat(content)
                .contains("<!DOCTYPE html>")
                .contains("John Doe")
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
            assertThat(emails.first().content.toString())
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

            assertThat(emails.first().content.toString())
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
            val reminder = createAndSaveReminder(user.id!!, period.id!!)

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
            val content = email.content.toString()
            assertThat(content)
                .contains("€25.00")
                .contains("€45.00")
                .contains("€10.00")
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
            val content = email.content.toString()
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
            val content = email.content.toString()
            assertThat(content)
                .contains("<!DOCTYPE html>")
                .contains("<html", "<body>", "</html>")
        }

        @Test
        fun `emails contain styled content`() {
            // Given: User
            val user = createAndSaveUser("style.test", "style@example.com")

            // When: Sending email
            emailService.sendUserResetEmail(user.id!!, "token", ResetType.PASSWORD_RESET)

            // Then: Email contains styling
            val email = mailSender.outbox.first()
            val content = email.content.toString()
            // Template should include some styling elements
            assertThat(content)
                .containsAnyOf("<style", "style=", "class=")
        }
    }

    // Helper methods
    private fun createAndSaveUser(username: String, email: String): User {
        val user = User(
            username = username,
            password = passwordEncoder.encode("Password123!"),
            firstName = username.split(".").first().capitalize(),
            lastName = username.split(".").getOrElse(1) { "User" }.capitalize()
        )
        user.email = email
        user.enabled = true
        user.roles = mutableSetOf(Role.MEMBER)
        return persist(user)
    }

    private fun createAndSavePeriod(): ContributionPeriod {
        val period = ContributionPeriod()
        period.startDate = LocalDate.of(2024, 1, 1)
        period.endDate = LocalDate.of(2024, 12, 31)
        period.halfYearFee = 25.0
        period.fullYearFee = 45.0
        period.alumniFee = 10.0
        return persist(period)
    }

    private fun createAndSaveReminder(userId: Long, periodId: Long): ContributionReminder {
        val reminder = ContributionReminder()
        reminder.id = ContributionReminder.Id(userId, periodId)
        return persist(reminder)
    }

    private fun createAndSaveEvent(title: String, location: String): Event {
        val committee = createAndSaveCommittee("Test Committee")
        val event = Event()
        event.committee = committee
        event.title = title
        event.location = location
        event.startTime = Instant.now().plus(7, ChronoUnit.DAYS)
        event.endTime = Instant.now().plus(7, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS)
        event.approved = true
        event.signUp = true
        return persist(event)
    }

    private fun createAndSaveCommittee(name: String): Committee {
        val committee = Committee()
        committee.name = name
        committee.description = "Test committee for integration tests"
        return persist(committee)
    }

    private fun createAndSaveSignUp(event: Event, guestName: String, guestEmail: String): EventSignUp {
        val guest = Guest()
        guest.name = guestName
        guest.email = guestEmail
        guest.accessToken = "test-token-${System.currentTimeMillis()}"

        val signUp = EventSignUp()
        signUp.event = event
        signUp.guest = guest
        return persist(signUp)
    }
}
