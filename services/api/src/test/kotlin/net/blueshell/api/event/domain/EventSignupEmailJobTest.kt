package net.blueshell.api.event.domain

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper

class EventSignupEmailJobTest {
    private val objectMapper = JsonMapper()
    private val eventSignUps: EventSignUpService = mockk()
    private val emails: EmailSenderService = mockk(relaxed = true)
    private val job = EventSignupEmailJob(objectMapper, eventSignUps, emails, "http://localhost:3000")

    @Test
    fun `a missing sign-up is permanent, not retryable`() {
        every { eventSignUps.findById(42L) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "EventSignUp not found")

        assertThatThrownBy {
            job.handle(objectMapper.writeValueAsString(EmailJobs.EventSignupPayload(42L, "guest-token")))
        }.isInstanceOf(NonRetryableJobException::class.java)
    }
}
