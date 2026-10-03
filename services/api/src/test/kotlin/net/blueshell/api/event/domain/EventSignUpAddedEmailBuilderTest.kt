package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.Guest
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class EventSignUpAddedEmailBuilderTest {
    private val frontendUrl = "https://blueshell.test"

    private fun signUp(location: String? = "Campus Building A"): EventSignUp {
        val event =
            Entities.event(
                id = 5L,
                title = "LAN Party",
                startTime = Instant.parse("2026-06-15T18:00:00Z"),
                endTime = Instant.parse("2026-06-15T21:30:00Z"),
            )
        event.location = location
        return EventSignUp(event = event).apply {
            guest =
                Guest.withRawToken(
                    name = "Guest Gordon",
                    discord = "gordon#0001",
                    email = "gordon@example.com",
                    accessToken = "GORDON-TOKEN",
                )
        }
    }

    @Test
    fun `addresses the guest the sign-up names`() {
        val email = createEventSignUpAddedEmail(signUp(), frontendUrl, "GORDON-TOKEN")

        assertThat(email.recipientEmail).isEqualTo("gordon@example.com")
        assertThat(email.recipientName).isEqualTo("Guest Gordon")
        assertThat(email.markdownContent).contains("Dear Guest Gordon")
        assertThat(email.senderNameOverride).isEqualTo("Blueshell Events")
    }

    @Test
    fun `says the board added them, and names the event in the subject and the body`() {
        val email = createEventSignUpAddedEmail(signUp(), frontendUrl, "GORDON-TOKEN")

        assertThat(email.subject).isEqualTo("Added to the sign-ups - LAN Party")
        assertThat(email.markdownContent).contains("The board added you to the sign-ups of **LAN Party**.")
    }

    @Test
    fun `says when the event is in Amsterdam time, and where`() {
        val email = createEventSignUpAddedEmail(signUp(), frontendUrl, "GORDON-TOKEN")

        assertThat(email.markdownContent)
            .contains("15 June 2026, 20:00 to 23:30")
            .contains("Campus Building A")
    }

    @Test
    fun `leaves the location out where the event has none`() {
        val email = createEventSignUpAddedEmail(signUp(location = null), frontendUrl, "GORDON-TOKEN")

        assertThat(email.markdownContent).doesNotContain("Where")
    }

    @Test
    fun `carries the event page and the guest's access link`() {
        val email = createEventSignUpAddedEmail(signUp(), frontendUrl, "GORDON-TOKEN")

        assertThat(email.markdownContent)
            .contains("https://blueshell.test/events/5")
            .contains("https://blueshell.test/events/signups/edit#accessToken=GORDON-TOKEN")
    }

    @Test
    fun `promises nothing about an event that may already be over`() {
        val email = createEventSignUpAddedEmail(signUp(), frontendUrl, "GORDON-TOKEN")

        assertThat(email.markdownContent)
            .doesNotContain("Thank you for registering")
            .doesNotContain("see you at the event")
    }

    @Test
    fun `refuses a sign-up that has no guest`() {
        val accountSignUp = EventSignUp(event = Entities.event(id = 5L), userId = 9L)

        assertThatThrownBy { createEventSignUpAddedEmail(accountSignUp, frontendUrl, "TOKEN") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
