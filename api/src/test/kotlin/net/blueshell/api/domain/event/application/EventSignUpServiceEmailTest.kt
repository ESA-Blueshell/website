package net.blueshell.api.domain.event.application

import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Tests email job scheduling for event signups.
 *
 * Verifies that when a guest signs up for an event, an email job is scheduled
 * to send confirmation (ADR-019, ADR-022).
 */
class EventSignUpServiceEmailTest : ServiceTestSupport() {

    @Autowired
    private lateinit var eventSignUpService: EventSignUpService

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `schedules signup confirmation email for guest signups`() {
        // Given: Event signup with guest
        val signUp = createEventSignUpWithGuest()

        // When: Creating signup via service (triggers event)
        val created = eventSignUpService.create(signUp)

        // Then: Email job is scheduled
        val jobs = findJobsByType(EmailJobs.EventSignup.type)
        assertThat(jobs)
            .describedAs("Should schedule event signup email job")
            .isNotEmpty

        val jobPayload = jobs.first().payload
        assertThat(jobPayload)
            .describedAs("Job payload should contain signUpId")
            .contains("\"eventSignUpId\":${created.id}")
    }

    @Test
    fun `does not schedule email for user-only signups without guest`() {
        // Given: User and event signup with user only (no guest)
        val user = createAndSaveUser()
        val event = createAndSaveEvent()
        val signUp = EventSignUp().apply {
            this.event = event
            this.user = user
            this.userId = user.id
            // No guest set
        }

        // When: Creating signup via service (triggers event)
        eventSignUpService.create(signUp)

        // Then: No email job is scheduled (users don't get confirmation emails)
        val jobs = findJobsByType(EmailJobs.EventSignup.type)
        assertThat(jobs)
            .describedAs("Should not schedule email for user-only signups")
            .isEmpty()
    }

    private fun createEventSignUpWithGuest(): EventSignUp {
        val event = createAndSaveEvent()
        val guest = Guest().apply {
            name = "Test Guest"
            discord = "guest#1234"
            email = "guest@example.com"
            accessToken = "test-access-token-${System.currentTimeMillis()}"
        }

        return EventSignUp().apply {
            this.event = event
            this.guest = guest
        }
    }

    private fun createAndSaveEvent(): Event {
        val committee = createAndSaveCommittee()
        val event = Event()
        event.committee = committee
        event.title = "Test Event"
        event.location = "Test Location"
        event.startTime = Instant.now().plus(7, ChronoUnit.DAYS)
        event.endTime = Instant.now().plus(7, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS)
        event.approved = true
        event.signUp = true
        return persist(event)
    }

    private fun createAndSaveCommittee(): Committee {
        val committee = Committee()
        committee.name = "Test Committee"
        committee.description = "Test committee for event signup tests"
        return persist(committee)
    }

    private fun createAndSaveUser(): User {
        val user = User(
            username = "testuser",
            password = passwordEncoder.encode("Password123!"),
            firstName = "Test",
            lastName = "User"
        )
        user.email = "testuser@example.com"
        user.enabled = true
        user.roles = mutableSetOf(Role.MEMBER)
        return persist(user)
    }
}
