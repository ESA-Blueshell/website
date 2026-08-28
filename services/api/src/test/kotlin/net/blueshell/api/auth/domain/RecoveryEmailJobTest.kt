package net.blueshell.api.auth.domain

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.NonRetryableJobException
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
class RecoveryEmailJobTest {
    private val objectMapper = JsonMapper()
    private val users: UserService = mockk()
    private val emails: EmailSenderService = mockk(relaxed = true)
    private val job = RecoveryEmailJob(objectMapper, users, emails, "http://localhost:3000")

    private fun run(userId: Long, token: String = "token", purpose: TokenPurpose = TokenPurpose.PASSWORD_RESET) =
        job.handle(objectMapper.writeValueAsString(EmailJobs.RecoveryPayload(userId, token, purpose)))

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
    fun `a signup continuation token is never emailed`() {
        every { users.findById(7L) } returns mockk<User>(relaxed = true)

        assertThatThrownBy { run(7L, "selector.verifier", TokenPurpose.SIGNUP_CONTINUATION) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must never be emailed")
    }
}
