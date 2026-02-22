package net.blueshell.api.platform.integration.email.job

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.mail.Multipart
import jakarta.mail.internet.MimeMessage
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.job.persistence.JobExecution
import net.blueshell.api.platform.integration.mock.MockJavaMailSender
import net.blueshell.api.shared.enums.ResetType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
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
 * Integration tests for email job handlers.
 *
 * Tests job payload parsing and email sending through job execution.
 */
class EmailJobHandlersTest : ServiceTestSupport() {

    @Autowired
    private lateinit var recoveryEmailJob: RecoveryEmailJob

    @Autowired
    private lateinit var eventSignupEmailJob: EventSignupEmailJob

    @Autowired
    private lateinit var contributionReminderEmailJob: ContributionReminderEmailJob

    @Autowired
    private lateinit var mailSender: MockJavaMailSender

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun clearMailbox() {
        mailSender.clear()
    }

    @Nested
    inner class RecoveryEmailJobTest {

        @Test
        fun `processes password reset job and sends email`() {
            // Given: User in database and job execution
            val user = createAndSaveUser("john.doe", "john@example.com")
            val payload = EmailJobs.RecoveryPayload(
                userId = user.id!!,
                token = "reset-token-123",
                resetType = ResetType.PASSWORD_RESET
            )
            val jobExecution = createJobExecution(EmailJobs.Recovery.type, payload)

            // When: Handling job
            recoveryEmailJob.handle(jobExecution.payload)

            // Then: Email is sent
            val emails = mailSender.outbox
            assertThat(emails).hasSize(1)
            assertThat(emails.first().allRecipients.map { it.toString() })
                .contains("john@example.com")
            assertThat(emails.first().subject)
                .contains("Reset Your")
        }

        @Test
        fun `processes user activation job and sends email`() {
            // Given: User and activation job
            val user = createAndSaveUser("jane.smith", "jane@example.com")
            val payload = EmailJobs.RecoveryPayload(
                userId = user.id!!,
                token = "activation-token-456",
                resetType = ResetType.USER_ACTIVATION
            )
            val jobExecution = createJobExecution(EmailJobs.Recovery.type, payload)

            // When: Handling job
            recoveryEmailJob.handle(jobExecution.payload)

            // Then: Activation email is sent
            val emails = mailSender.outbox
            assertThat(emails).hasSize(1)
            assertThat(emails.first().subject).contains("Activate")
        }

        @Test
        fun `processes member activation job and sends email`() {
            // Given: User and member activation job
            val user = createAndSaveUser("board.member", "board@example.com")
            val payload = EmailJobs.RecoveryPayload(
                userId = user.id!!,
                token = "member-token-789",
                resetType = ResetType.MEMBER_ACTIVATION
            )
            val jobExecution = createJobExecution(EmailJobs.Recovery.type, payload)

            // When: Handling job
            recoveryEmailJob.handle(jobExecution.payload)

            // Then: Member activation email is sent
            val emails = mailSender.outbox
            assertThat(emails).hasSize(1)
            assertThat(extractHtmlContent(emails.first()))
                .contains("board of Blueshell")
        }

        @Test
        fun `job type matches Recovery type`() {
            assertThat(recoveryEmailJob.jobType).isEqualTo(EmailJobs.Recovery.type)
        }
    }

    @Nested
    inner class EventSignupEmailJobTest {

        @Test
        fun `processes event signup job and sends email`() {
            // Given: Event signup in database and job
            val event = createAndSaveEvent("Test Tournament", "Campus Hall")
            val guestAccessToken = "event-signup-token-${System.currentTimeMillis()}"
            val signUp = createAndSaveSignUp(event, "Guest User", "guest@example.com", guestAccessToken)
            val payload = EmailJobs.EventSignupPayload(eventSignUpId = signUp.id!!, guestAccessToken = guestAccessToken)
            val jobExecution = createJobExecution(EmailJobs.EventSignup.type, payload)

            // When: Handling job
            eventSignupEmailJob.handle(jobExecution.payload)

            // Then: Signup email is sent
            val emails = mailSender.outbox
            assertThat(emails).hasSize(1)
            assertThat(emails.first().allRecipients.map { it.toString() })
                .contains("guest@example.com")
            assertThat(emails.first().subject)
                .contains("Event Registration Confirmed")
                .contains("Test Tournament")
        }

        @Test
        fun `job type matches EventSignup type`() {
            assertThat(eventSignupEmailJob.jobType).isEqualTo(EmailJobs.EventSignup.type)
        }
    }

    @Nested
    inner class ContributionReminderEmailJobTest {

        @Test
        fun `processes contribution reminder job and sends email`() {
            // Given: User, period, reminder, and job
            val user = createAndSaveUser("contributor", "contributor@example.com")
            val period = createAndSavePeriod()
            createAndSaveReminder(user, period)

            val payload = EmailJobs.ContributionReminderPayload(
                userId = user.id!!,
                contributionPeriodId = period.id!!
            )
            val jobExecution = createJobExecution(EmailJobs.ContributionReminder.type, payload)

            // When: Handling job
            contributionReminderEmailJob.handle(jobExecution.payload)

            // Then: Reminder email is sent
            val emails = mailSender.outbox
            assertThat(emails).hasSize(1)
            assertThat(emails.first().allRecipients.map { it.toString() })
                .contains("contributor@example.com")
            assertThat(emails.first().subject)
                .contains("Contribution Payment Reminder")
        }

        @Test
        fun `job type matches ContributionReminder type`() {
            assertThat(contributionReminderEmailJob.jobType)
                .isEqualTo(EmailJobs.ContributionReminder.type)
        }
    }

    @Nested
    inner class JobPayloadParsing {

        @Test
        fun `RecoveryEmailJob parses JSON payload correctly`() {
            // Given: User and JSON payload
            val user = createAndSaveUser("parse.test", "parse@example.com")
            val payloadJson = """
                {
                    "userId": ${user.id},
                    "token": "test-token",
                    "resetType": "PASSWORD_RESET"
                }
            """.trimIndent()
            val jobExecution = JobExecution().apply {
                this.jobType = EmailJobs.Recovery.type
                this.payload = payloadJson
            }
            persist(jobExecution)

            // When: Handling job
            recoveryEmailJob.handle(jobExecution.payload)

            // Then: Email is sent (payload parsed correctly)
            assertThat(mailSender.outbox).hasSize(1)
        }

        @Test
        fun `EventSignupEmailJob parses JSON payload correctly`() {
            // Given: Event signup and JSON payload
            val event = createAndSaveEvent("Parse Test", "Location")
            val guestAccessToken = "parse-token-${System.currentTimeMillis()}"
            val signUp = createAndSaveSignUp(event, "Guest", "guest@example.com", guestAccessToken)
            val payloadJson = """
                {
                    "eventSignUpId": ${signUp.id},
                    "guestAccessToken": "$guestAccessToken"
                }
            """.trimIndent()
            val jobExecution = JobExecution().apply {
                this.jobType = EmailJobs.EventSignup.type
                this.payload = payloadJson
            }
            persist(jobExecution)

            // When: Handling job
            eventSignupEmailJob.handle(jobExecution.payload)

            // Then: Email is sent (payload parsed correctly)
            assertThat(mailSender.outbox).hasSize(1)
        }

        @Test
        fun `ContributionReminderEmailJob parses JSON payload correctly`() {
            // Given: Reminder and JSON payload
            val user = createAndSaveUser("reminder.test", "reminder@example.com")
            val period = createAndSavePeriod()
            createAndSaveReminder(user, period)

            val payloadJson = """
                {
                    "userId": ${user.id},
                    "contributionPeriodId": ${period.id}
                }
            """.trimIndent()
            val jobExecution = JobExecution().apply {
                this.jobType = EmailJobs.ContributionReminder.type
                this.payload = payloadJson
            }
            persist(jobExecution)

            // When: Handling job
            contributionReminderEmailJob.handle(jobExecution.payload)

            // Then: Email is sent (payload parsed correctly)
            assertThat(mailSender.outbox).hasSize(1)
        }
    }

    // Helper methods
    private fun createJobExecution(jobType: String, payload: Any): JobExecution {
        val jobExecution = JobExecution()
        jobExecution.jobType = jobType
        jobExecution.payload = objectMapper.writeValueAsString(payload)
        return persist(jobExecution)
    }

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
