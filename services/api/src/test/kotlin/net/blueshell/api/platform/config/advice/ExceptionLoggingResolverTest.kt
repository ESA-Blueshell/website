package net.blueshell.api.platform.config.advice

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@ExtendWith(OutputCaptureExtension::class)
class ExceptionLoggingResolverTest {
    @Test
    fun `logs the exception's message scrubbed, and leaves the answer to the next resolver`(output: CapturedOutput) {
        val refused = IllegalStateException("Duplicate entry 'ann@example.org-9999' for key 'uk_users_email_deleted_at'")

        val answer =
            ExceptionLoggingResolver().resolveException(
                MockHttpServletRequest("POST", "/users"),
                MockHttpServletResponse(),
                null,
                refused,
            )

        assertThat(answer).isNull()
        assertThat(
            output.all,
        ).contains("POST /users -> IllegalStateException: Duplicate entry '[value]' for key 'uk_users_email_deleted_at'")
    }
}
