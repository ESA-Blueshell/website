package net.blueshell.api.domain.event.application

import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

/**
 * Tests email job scheduling for event signups.
 *
 * Verifies that when a guest signs up for an event, an email job is scheduled
 * to send confirmation (ADR-019, ADR-022).
 */
class EventSignUpServiceEmailTest : ServiceTestSupport() {

    @Autowired
    private lateinit var eventSignUpService: EventSignUpService

    @Test
    fun `schedules signup confirmation email for guest signups`() {
        // Given: Event signup with guest
        val signUp = createEventSignUpWithGuest()

        // When: Creating signup
        val created = persist(signUp)

        // Then: Email job is scheduled
        val jobs = findJobsByType(EmailJobs.EventSignup.type)
        assertThat(jobs)
            .describedAs("Should schedule event signup email job")
            .isNotEmpty

        val jobPayload = jobs.first().payload
        assertThat(jobPayload)
            .describedAs("Job payload should contain signUpId")
            .contains("\"signUpId\":${created.id}")
    }

    @Test
    fun `does not schedule email for user-only signups without guest`() {
        // Given: Event signup with user only (no guest)
        val signUp = EventSignUp().apply {
            userId = 123L
            // No guest set
        }

        // When: Creating signup
        persist(signUp)

        // Then: No email job is scheduled (users don't get confirmation emails)
        val jobs = findJobsByType(EmailJobs.EventSignup.type)
        assertThat(jobs)
            .describedAs("Should not schedule email for user-only signups")
            .isEmpty()
    }

    private fun createEventSignUpWithGuest(): EventSignUp {
        val guest = Guest().apply {
            name = "Test Guest"
            email = "guest@example.com"
            accessToken = "test-access-token-${System.currentTimeMillis()}"
        }

        return EventSignUp().apply {
            this.guest = guest
        }
    }
}
