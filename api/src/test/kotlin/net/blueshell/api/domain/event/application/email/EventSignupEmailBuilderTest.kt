package net.blueshell.api.domain.event.application.email

import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Tests for event signup email builder.
 *
 * Verifies EmailContent is created correctly for guest signups (ADR-019, ADR-022).
 */
class EventSignupEmailBuilderTest {

    private val frontendUrl = "https://test-frontend.com"
    private val appUrl = "https://test-app.com"

    @Test
    fun `createEventSignupEmail builds correct EmailContent`() {
        // Given: Event signup with guest
        val signUp = createTestSignUp(
            eventTitle = "Summer Gaming Tournament",
            guestName = "John Doe",
            guestEmail = "john@example.com",
            accessToken = "test-token-123",
            location = "Campus Building A"
        )

        // When: Building event signup email
        val emailContent = createEventSignupEmail(signUp, frontendUrl, appUrl)

        // Then: EmailContent has correct fields
        assertThat(emailContent.recipientEmail).isEqualTo("john@example.com")
        assertThat(emailContent.recipientName).isEqualTo("John Doe")
        assertThat(emailContent.subject).isEqualTo("Event Registration Confirmed - Summer Gaming Tournament")
        assertThat(emailContent.senderName).isEqualTo("Blueshell Events")

        // And: Body contains event details
        assertThat(emailContent.markdownContent)
            .contains("Dear John Doe")
            .contains("Summer Gaming Tournament")
            .contains("Campus Building A")
            .contains("$frontendUrl/events/signups/edit/test-token-123")
            .contains("$frontendUrl/events#")
    }

    @Test
    fun `email contains all event information`() {
        // Given: Complete event signup
        val signUp = createTestSignUp(
            eventTitle = "Test Event",
            guestName = "Jane Smith",
            guestEmail = "jane@example.com",
            startTime = Instant.parse("2024-06-15T18:00:00Z"),
            endTime = Instant.parse("2024-06-15T21:00:00Z"),
            location = "Student Union"
        )

        // When: Building email
        val emailContent = createEventSignupEmail(signUp, frontendUrl, appUrl)

        // Then: Email contains complete event info
        assertThat(emailContent.markdownContent)
            .contains("**Event Information:**")
            .contains("**Event:** Test Event")
            .contains("**Date:**")
            .contains("2024-06-15")
            .contains("**Location:** Student Union")
    }

    @Test
    fun `email provides default location message when location is blank`() {
        // Given: Event without specific location
        val signUp = createTestSignUp(location = null)

        // When: Building email
        val emailContent = createEventSignupEmail(signUp, frontendUrl, appUrl)

        // Then: Default location message is shown
        assertThat(emailContent.markdownContent)
            .contains("Location details will be provided closer to the event date")
    }

    @Test
    fun `email includes important links for guest`() {
        // Given: Event signup with access token (note: id will be null in test, but builder should handle it)
        val signUp = createTestSignUp(
            accessToken = "unique-access-token"
        )

        // When: Building email
        val emailContent = createEventSignupEmail(signUp, frontendUrl, appUrl)

        // Then: Email includes edit link and event details link
        assertThat(emailContent.markdownContent)
            .contains("**Important Links:**")
            .contains("[View full event details]($frontendUrl/events#")
            .contains("[Edit your registration]($frontendUrl/events/signups/edit/unique-access-token)")
    }

    @Test
    fun `email includes next steps and Discord link`() {
        // Given: Event signup
        val signUp = createTestSignUp()

        // When: Building email
        val emailContent = createEventSignupEmail(signUp, frontendUrl, appUrl)

        // Then: Email includes what's next and community links
        assertThat(emailContent.markdownContent)
            .contains("**What's Next?**")
            .contains("Keep an eye on your email")
            .contains("[Discord community](https://discord.gg/dFam2yqXu7)")
            .contains(appUrl)
            .contains("We're excited to see you at the event!")
    }

    @Test
    fun `throws exception when guest is null`() {
        // Given: Signup without guest
        val signUp = EventSignUp().apply {
            event = createTestEvent()
            // guest is null
        }

        // When/Then: Exception is thrown
        val exception = assertThrows<IllegalArgumentException> {
            createEventSignupEmail(signUp, frontendUrl, appUrl)
        }
        assertThat(exception.message).contains("Event signup email requires a guest signup")
    }

    @Test
    fun `handles same start and end time`() {
        // Given: Event with same start/end time
        val dateTime = Instant.parse("2024-06-15T18:00:00Z")
        val signUp = createTestSignUp(startTime = dateTime, endTime = dateTime)

        // When: Building email
        val emailContent = createEventSignupEmail(signUp, frontendUrl, appUrl)

        // Then: Date information is present (exact format depends on implementation)
        assertThat(emailContent.markdownContent)
            .contains("2024-06-15")
    }

    private fun createTestSignUp(
        eventTitle: String = "Test Event",
        guestName: String = "Test Guest",
        guestEmail: String = "guest@example.com",
        accessToken: String = "test-token",
        location: String? = "Test Location",
        startTime: Instant = Instant.now(),
        endTime: Instant = Instant.now().plus(3, ChronoUnit.HOURS)
    ): EventSignUp {
        val event = createTestEvent(eventTitle, location, startTime, endTime)
        val guest = Guest().apply {
            this.name = guestName
            this.email = guestEmail
            this.accessToken = accessToken
        }

        return EventSignUp().apply {
            this.event = event
            this.guest = guest
        }
    }

    private fun createTestEvent(
        title: String = "Test Event",
        location: String? = "Test Location",
        startTime: Instant = Instant.now(),
        endTime: Instant = Instant.now().plus(3, ChronoUnit.HOURS)
    ): Event {
        return Event().apply {
            // Note: id will be null until persisted - this is fine for unit tests
            this.title = title
            this.location = location
            this.startTime = startTime
            this.endTime = endTime
        }
    }
}
