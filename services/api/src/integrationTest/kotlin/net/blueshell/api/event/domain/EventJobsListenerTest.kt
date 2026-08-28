package net.blueshell.api.event.domain

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.Guest
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

class EventJobsListenerTest : ServiceTestSupport() {

    @Autowired
    private lateinit var listener: EventJobsListener

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `dispatches signup email on EventSignUpCreated with guest`() {
        val event = createEvent()
        val guest = persist(
            Guest.withRawToken(
                name = "Test Guest",
                discord = "guest#0001",
                email = "guest@example.com",
                accessToken = "guest-token-123"
            )
        )
        val signUp = persist(EventSignUp(event = event, guest = guest))

        val evt = EventSignUpCreated(signUp.id!!, guestAccessToken = "guest-token-123")

        listener.onPersist(evt)

        val jobs = findJobsByType(EmailJobs.EventSignup.type)
        assertThat(jobs)
            .describedAs("Should schedule one EventSignup email job")
            .hasSize(1)

        assertThat(jobs.first().payload)
            .contains("\"eventSignUpId\":${signUp.id}")
            .contains("\"guestAccessToken\":\"guest-token-123\"")
    }

    @Test
    fun `does not dispatch email when guestAccessToken is null`() {
        val event = createEvent()
        val guest = persist(
            Guest.withRawToken(
                name = "Test Guest",
                discord = "guest#0002",
                email = "guest2@example.com",
                accessToken = "guest-token-456"
            )
        )
        val signUp = persist(EventSignUp(event = event, guest = guest))

        val evt = EventSignUpCreated(signUp.id!!, guestAccessToken = null)

        listener.onPersist(evt)

        assertThat(findJobsByType(EmailJobs.EventSignup.type))
            .describedAs("Should not schedule email job when guestAccessToken is null")
            .isEmpty()
    }

    @Test
    fun `does not dispatch email when signup has no guest`() {
        val event = createEvent()
        val user = createUser()
        val signUp = persist(EventSignUp(event = event, userId = user.id))

        val evt = EventSignUpCreated(signUp.id!!, guestAccessToken = "some-token")

        listener.onPersist(evt)

        assertThat(findJobsByType(EmailJobs.EventSignup.type))
            .describedAs("Should not schedule email job when signup has no guest")
            .isEmpty()
    }

    private fun createUser(): User {
        val username = "listener_test_${System.currentTimeMillis()}"
        val user = User(
            username = username,
            email = "$username@test.com",
            password = requireNotNull(passwordEncoder.encode("Password123!")) { "PasswordEncoder returned null hash" },
            initials = "TU",
            firstName = "Test",
            lastName = "User",
            phoneNumber = "06${System.currentTimeMillis().toString().takeLast(8)}",
            discord = "$username#0001"
        )
        user.enabled = true
        user.roles = mutableSetOf(Role.MEMBER)
        return persist(user)
    }

    private fun createEvent(): Event {
        val committee = persist(
            Committee(
                name = "Listener Test Committee ${System.currentTimeMillis()}",
                description = "Committee for listener tests"
            )
        )
        return persist(
            Event(
                committee = committee,
                title = "Listener Test Event ${System.currentTimeMillis()}",
                description = "Listener test",
                location = "Test Location",
                startTime = Instant.now().plus(1, ChronoUnit.DAYS),
                endTime = Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                approved = true
            )
        )
    }
}
