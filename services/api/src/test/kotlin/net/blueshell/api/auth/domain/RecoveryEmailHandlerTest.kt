package net.blueshell.api.auth.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.testsupport.runJob
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper

/**
 * A job whose subject is gone can never succeed, so a 404 must become
 * [NonRetryableJobException] and be marked DEAD on the first attempt rather than
 * retried.
 */
class RecoveryEmailHandlerTest {
    private val objectMapper = JsonMapper()
    private val users: UserService = mockk()
    private val emails: EmailSenderService = mockk(relaxed = true)

    private fun job(email: AuthJobs.RecoveryEmail) =
        RecoveryEmailHandler(
            objectMapper,
            email,
            users,
            emails,
            "http://localhost:3000",
            SecurityContacts("board@example.org", "https://api/discord/channel/board", "https://api/discord/channel/suggestions"),
        )

    private fun run(
        userId: Long,
        token: String = "token",
        email: AuthJobs.RecoveryEmail = AuthJobs.PasswordReset,
    ) = job(email).runJob(objectMapper.writeValueAsString(AuthJobs.RecoveryPayload(userId, token)))

    @Test
    fun `a missing user is permanent, not retryable`() {
        every { users.findById(99L) } throws ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        assertThatThrownBy { run(99L) }.isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `a non-404 failure stays retryable`() {
        every { users.findById(99L) } throws
            ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Server error")

        assertThatThrownBy { run(99L) }
            .isInstanceOf(ResponseStatusException::class.java)
            .isNotInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `a re-enrolment link goes out naming who to contact`() {
        every { users.findById(7L) } returns
            User(username = "alice", email = "alice@example.com", password = "h", initials = "A", firstName = "Alice", lastName = "Doe")

        run(7L, "sel.ver", AuthJobs.TwoFactorReenrolment)

        verify {
            emails.send(
                match { it.markdownContent.contains("board@example.org") && it.recipientEmail == "alice@example.com" },
                "email.recovery",
                any(),
            )
        }
    }

    @Test
    fun `a purpose that is never mailed has no email`() {
        assertThatThrownBy { AuthJobs.recoveryEmailFor(TokenPurpose.SIGNUP_CONTINUATION) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("never emailed")
        assertThatThrownBy { AuthJobs.recoveryEmailFor(TokenPurpose.ACCOUNT_LOCK) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `every mailed purpose has its own email job`() {
        val mailed = TokenPurpose.entries.filter { it.isMailable }

        assertThat(mailed.map { AuthJobs.recoveryEmailFor(it).purpose }).isEqualTo(mailed)
        assertThat(AuthJobs.recoveryEmails.map { it.type }).doesNotHaveDuplicates()
    }

    @Test
    fun `registers one handler for each email a link goes out in`() {
        val handlers = RecoveryEmailHandlers(objectMapper, users, emails, "http://localhost:3000", mockk())

        val registered =
            listOf(
                handlers.accountActivationEmailJob(),
                handlers.memberActivationEmailJob(),
                handlers.passwordResetEmailJob(),
                handlers.emailChangeEmailJob(),
                handlers.twoFactorReenrolmentEmailJob(),
            )

        assertThat(registered.map { it.jobType }).containsExactlyElementsOf(AuthJobs.recoveryEmails.map { it.type })
    }
}
