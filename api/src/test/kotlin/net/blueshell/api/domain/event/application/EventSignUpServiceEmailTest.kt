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
            .hasSize(1)

        val jobPayload = jobs.first().payload
        assertThat(jobPayload)
            .describedAs("Job payload should contain signUpId")
            .contains("\"eventSignUpId\":${created.id}")
            .contains("\"guestAccessToken\":\"")
    }

    @Test
    fun `does not schedule email for user-only signups without guest`() {
        // Given: User and event signup with user only (no guest)
        val user = createAndSaveUser()
        val event = createAndSaveEvent()
        val signUp = EventSignUp(
            event = event,
            userId = user.id,
            guest = null,
        )

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
        val guest = Guest.withRawToken(
            name = "Test Guest",
            discord = "guest#1234",
            email = "guest@example.com",
            accessToken = "test-access-token-${System.currentTimeMillis()}",
        )

        return EventSignUp(event = event, guest = guest)
    }

    private fun createAndSaveEvent(): Event {
        val committee = createAndSaveCommittee()
        val event = Event(
            committee = committee,
            title = "Test Event",
            location = "Test Location",
            startTime = Instant.now().plus(7, ChronoUnit.DAYS),
            endTime = Instant.now().plus(7, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
            approved = true,
            signUp = true,
        )
        return persist(event)
    }

    private fun createAndSaveCommittee(): Committee {
        return persist(Committee(name = "Test Committee", description = "Test committee for event signup tests"))
    }

    private fun createAndSaveUser(): User {
        val user = User(
            username = "testuser",
            email = "testuser@example.com",
            password = passwordEncoder.encode("Password123!"),
            initials = "TU",
            firstName = "Test",
            lastName = "User",
            phoneNumber = "0612345678",
            discord = "testuser#0001"
        )
        user.enabled = true
        user.roles = mutableSetOf(Role.MEMBER)
        return persist(user)
    }
}
