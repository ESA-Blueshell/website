package net.blueshell.api.email.domain

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.mock.InMemoryEmailClient
import net.blueshell.api.auth.domain.RecoveryEmailJob
import net.blueshell.api.contribution.domain.ContributionReminderEmailJob
import net.blueshell.api.domain.event.application.job.EventSignupEmailJob
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.EmailJobs
import tools.jackson.databind.ObjectMapper
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.ServiceTestSupport
import net.blueshell.api.shared.job.NonRetryableJobException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Tests the complete email flow through its real entry point: the job handler in
 * the module that owns the content, then the template service, then delivery.
 */
class EmailServiceIntegrationTest : ServiceTestSupport() {

    @Autowired
    private lateinit var recoveryEmailJob: RecoveryEmailJob

    @Autowired
    private lateinit var eventSignupEmailJob: EventSignupEmailJob

    @Autowired
    private lateinit var contributionReminderEmailJob: ContributionReminderEmailJob

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var emailClient: InMemoryEmailClient

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun clearMailbox() {
        emailClient.reset()
    }

    private fun sendRecovery(userId: Long, token: String, purpose: TokenPurpose) =
        recoveryEmailJob.handle(objectMapper.writeValueAsString(EmailJobs.RecoveryPayload(userId, token, purpose)))

    private fun sendContributionReminder(userId: Long, periodId: Long) =
        contributionReminderEmailJob.handle(
            objectMapper.writeValueAsString(EmailJobs.ContributionReminderPayload(userId, periodId)),
        )

    private fun sendEventSignup(signUpId: Long, guestAccessToken: String) =
        eventSignupEmailJob.handle(
            objectMapper.writeValueAsString(EmailJobs.EventSignupPayload(signUpId, guestAccessToken)),
        )

    @Nested
    inner class RecoveryEmails {

        @Test
        fun `a password reset email reaches the user`() {
            val user = createAndSaveUser("john.doe", "john@example.com")
            val token = "reset-token-123"

            sendRecovery(user.id!!, token, TokenPurpose.PASSWORD_RESET)

            val emails = emailClient.sentEmails
            assertThat(emails).hasSize(1)

            val email = emails.first()
            assertThat(email.toEmail).isEqualTo("john@example.com")
            assertThat(email.subject).isEqualTo("Reset Your Blueshell Account Password")
            assertThat(email.htmlContent)
                .contains("<!DOCTYPE html>")
                .contains("reset-password")
        }

        @Test
        fun `a user activation email reaches the user`() {
            val user = createAndSaveUser("jane.smith", "jane@example.com")
            val token = "activation-token-456"

            sendRecovery(user.id!!, token, TokenPurpose.USER_ACTIVATION)

            val emails = emailClient.sentEmails
            assertThat(emails).hasSize(1)

            assertThat(emails.first().subject).isEqualTo("Activate your Account")
            assertThat(emails.first().htmlContent)
                .contains("Thank you for signing up")
                .contains("activate/user")
        }

        @Test
        fun `a member activation email reaches the board member`() {
            val user = createAndSaveUser("board.member", "board@example.com")
            val token = "member-token-789"

            sendRecovery(user.id!!, token, TokenPurpose.MEMBER_ACTIVATION)

            val emails = emailClient.sentEmails
            assertThat(emails).hasSize(1)

            assertThat(emails.first().htmlContent)
                .contains("board of Blueshell")
                .contains("activate/member")
        }
    }

    @Nested
    inner class ContributionEmails {

        @Test
        fun `a contribution reminder lists the payment options`() {
            val user = createAndSaveUser("contributor", "contributor@example.com")
            val period = createAndSavePeriod()
            val reminder = createAndSaveReminder(user, period)

            sendContributionReminder(user.id!!, period.id!!)

            val emails = emailClient.sentEmails
            assertThat(emails).hasSize(1)

            val email = emails.first()
            assertThat(email.toEmail).isEqualTo("contributor@example.com")
            assertThat(email.subject).contains("Contribution Payment Reminder")
            assertThat(email.htmlContent)
                .contains("€25,00")
                .contains("€45,00")
                .contains("€10,00")
                .contains("Treasurer")
        }
    }

    @Nested
    inner class EventEmails {

        @Test
        fun `an event signup confirmation reaches the guest`() {
            val event = createAndSaveEvent("Summer Tournament", "Campus Hall")
            val guestAccessToken = "event-signup-token-${System.currentTimeMillis()}"
            val signUp = createAndSaveSignUp(event, "Guest Name", "guest@example.com", guestAccessToken)

            sendEventSignup(signUp.id!!, guestAccessToken)

            val emails = emailClient.sentEmails
            assertThat(emails).hasSize(1)

            val email = emails.first()
            assertThat(email.toEmail).isEqualTo("guest@example.com")
            assertThat(email.subject).isEqualTo("Event Registration Confirmed - Summer Tournament")
            assertThat(email.htmlContent)
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
            val user = createAndSaveUser("test", "test@example.com")

            sendRecovery(user.id!!, "token", TokenPurpose.PASSWORD_RESET)

            val email = emailClient.sentEmails.first()
            assertThat(email.subject).isNotBlank()
            assertThat(email.toEmail).isEqualTo("test@example.com")
        }

        @Test
        fun `emails contain HTML content`() {
            val user = createAndSaveUser("html.test", "html@example.com")

            sendRecovery(user.id!!, "token", TokenPurpose.PASSWORD_RESET)

            val email = emailClient.sentEmails.first()
            assertThat(email.htmlContent)
                .contains("<!DOCTYPE html>")
                .contains("<html")
                .contains("<body")
                .contains("</html>")
        }

        @Test
        fun `emails contain styled content`() {
            val user = createAndSaveUser("style.test", "style@example.com")

            sendRecovery(user.id!!, "token", TokenPurpose.PASSWORD_RESET)

            val email = emailClient.sentEmails.first()
            assertThat(email.htmlContent)
                .containsAnyOf("<style", "style=", "class=")
        }
    }

    @Nested
    inner class SmtpFailures {

        @Test
        fun `send failure is retryable RuntimeException, not NonRetryableJobException`() {
            val user = createAndSaveUser("smtp.fail", "smtp@example.com")
            emailClient.simulateSendFailure()
            try {
                assertThatThrownBy {
                    sendRecovery(user.id!!, "token", TokenPurpose.PASSWORD_RESET)
                }
                    .isInstanceOf(RuntimeException::class.java)
                    .isNotInstanceOf(NonRetryableJobException::class.java)
            } finally {
                emailClient.stopSimulateSendFailure()
            }
        }
    }

    // Helper methods
    private fun createAndSaveUser(username: String, email: String): User {
        val user = User(
            username = username,
            email = email,
            password = requireNotNull(passwordEncoder.encode("Password123!")) { "PasswordEncoder returned null hash" },
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

    private fun createAndSaveSignUp(
        event: Event,
        guestName: String,
        guestEmail: String,
        accessToken: String = "test-token-${System.currentTimeMillis()}"
    ): EventSignUp {
        val guest = Guest.withRawToken(
            name = guestName,
            discord = "guest#1234",
            email = guestEmail,
            accessToken = accessToken,
        )

        val signUp = EventSignUp(event = event, guest = guest)
        return persist(signUp)
    }
}
