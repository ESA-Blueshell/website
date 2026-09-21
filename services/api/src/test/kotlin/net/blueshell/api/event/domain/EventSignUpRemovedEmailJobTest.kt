package net.blueshell.api.event.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.EmailJobs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class EventSignUpRemovedEmailJobTest {
    private val objectMapper = JsonMapper()
    private val emails: EmailSenderService = mockk(relaxed = true)
    private val job = EventSignUpRemovedEmailJob(objectMapper, emails, "https://blueshell.test")

    private val payload =
        EmailJobs.EventSignUpRemovedPayload(
            recipientEmail = "gordon@example.com",
            recipientName = "Guest Gordon",
            eventTitle = "LAN Party",
        )

    @Test
    fun `sends the removal email to whoever the payload names`() {
        val content = slot<EmailContent>()
        every { emails.send(capture(content), any(), any()) } returns Unit

        job.handle(objectMapper.writeValueAsString(payload))

        assertThat(content.captured.recipientEmail).isEqualTo("gordon@example.com")
        assertThat(content.captured.recipientName).isEqualTo("Guest Gordon")
        assertThat(content.captured.subject).contains("LAN Party")
    }

    @Test
    fun `files the email under its own type`() {
        assertThat(job.jobType).isEqualTo("email.event-signup-removed")

        job.handle(objectMapper.writeValueAsString(payload))

        verify { emails.send(any(), "email.event-signup-removed", any()) }
    }

    @Test
    fun `reads the payload rather than the sign-up, which is gone by now`() {
        val content = slot<EmailContent>()
        every { emails.send(capture(content), any(), any()) } returns Unit

        job.handle(
            objectMapper.writeValueAsString(
                payload.copy(recipientName = "Ada Lovelace", recipientEmail = "ada@example.com"),
            ),
        )

        assertThat(content.captured.markdownContent).contains("Dear Ada Lovelace")
        assertThat(content.captured.recipientEmail).isEqualTo("ada@example.com")
    }
}
