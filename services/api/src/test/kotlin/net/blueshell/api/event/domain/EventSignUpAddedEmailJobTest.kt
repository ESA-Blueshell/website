package net.blueshell.api.event.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.Guest
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.testsupport.runJob
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper

class EventSignUpAddedEmailJobTest {
    private val objectMapper = JsonMapper()
    private val eventSignUps: EventSignUpService = mockk()
    private val emails: EmailSenderService = mockk(relaxed = true)
    private val job = EventSignUpAddedEmailJob(objectMapper, eventSignUps, emails, "https://blueshell.test")

    private val payload = EventJobs.EventSignupPayload(eventSignUpId = 77L, guestAccessToken = "GORDON-TOKEN")

    @Test
    fun `tells the guest on the sign-up, under its own type, with their access link`() {
        every { eventSignUps.findById(77L) } returns
            EventSignUp(event = Entities.event(id = 5L, title = "LAN Party")).apply {
                guest =
                    Guest.withRawToken(
                        name = "Guest Gordon",
                        discord = "gordon#0001",
                        email = "gordon@example.com",
                        accessToken = "GORDON-TOKEN",
                    )
            }
        val content = slot<EmailContent>()
        every { emails.send(capture(content), "email.event-signup-added", any()) } returns Unit

        job.runJob(objectMapper.writeValueAsString(payload))

        assertThat(job.jobType).isEqualTo("email.event-signup-added")
        assertThat(content.captured.recipientEmail).isEqualTo("gordon@example.com")
        assertThat(content.captured.markdownContent).contains("#accessToken=GORDON-TOKEN")
    }

    @Test
    fun `a sign-up removed again before the email went is permanent, not retryable`() {
        every { eventSignUps.findById(77L) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "EventSignUp not found")

        assertThatThrownBy { job.runJob(objectMapper.writeValueAsString(payload)) }
            .isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `two adds of the same guest are two emails, so nothing is deduplicated`() {
        assertThat(EventJobs.EventSignUpAdded.dedupKey(payload)).isNull()
    }
}
