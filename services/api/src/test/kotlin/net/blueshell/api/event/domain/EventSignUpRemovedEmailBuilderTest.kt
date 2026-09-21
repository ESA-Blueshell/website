package net.blueshell.api.event.domain

import net.blueshell.api.shared.job.EmailJobs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EventSignUpRemovedEmailBuilderTest {
    private val payload =
        EmailJobs.EventSignUpRemovedPayload(
            recipientEmail = "gordon@example.com",
            recipientName = "Guest Gordon",
            eventTitle = "LAN Party",
        )

    @Test
    fun `addresses the person the sign-up named`() {
        val email = createEventSignUpRemovedEmail(payload, "https://blueshell.test")

        assertThat(email.recipientEmail).isEqualTo("gordon@example.com")
        assertThat(email.recipientName).isEqualTo("Guest Gordon")
        assertThat(email.markdownContent).contains("Dear Guest Gordon")
    }

    @Test
    fun `names the event in the subject and the body`() {
        val email = createEventSignUpRemovedEmail(payload, "https://blueshell.test")

        assertThat(email.subject).contains("LAN Party")
        assertThat(email.markdownContent).contains("**LAN Party**")
    }

    @Test
    fun `points at the events page on the configured frontend`() {
        val email = createEventSignUpRemovedEmail(payload, "https://blueshell.test")

        assertThat(email.markdownContent).contains("https://blueshell.test/events")
    }
}
