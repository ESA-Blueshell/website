package net.blueshell.api.auth.domain

import net.blueshell.api.user.api.UserCreated
import net.blueshell.api.user.persistence.User
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Tests for RecoveryEventListener verifying email jobs are scheduled.
 *
 * Tests the new email architecture (ADR-019, ADR-022) where:
 * - Listener reacts to domain events
 * - Jobs are scheduled (not sent immediately)
 * - Email content is built in domain, sent by platform
 */
class RecoveryEventListenerTest : ServiceTestSupport() {

    @Autowired
    private lateinit var listener: RecoveryEventListener

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jobs: net.blueshell.api.shared.job.JobQueue

    @Autowired
    private lateinit var activationService: UserActivationService

    @Test
    fun `schedules user activation email when user is created`() {
        // Given: User in database and created event (non-board user)
        val user = createAndSaveUser("newuser", "newuser@example.com", enabled = false)
        val event = UserCreated(user.id!!, createdByBoard = false)

        // When: Event is handled
        listener.onUserCreated(event)

        // Then: User activation email job is scheduled
        val jobs = findJobsByType(EmailJobs.Recovery.type)
        assertThat(jobs)
            .describedAs("Should schedule one recovery email job")
            .hasSize(1)

        val jobPayload = jobs.first().payload
        assertThat(jobPayload)
            .describedAs("Job payload should contain userId")
            .contains("\"userId\":${user.id}")
            .contains("\"tokenPurpose\":\"USER_ACTIVATION\"")
    }

    @Test
    fun `schedules member activation email when user is created by board`() {
        // Given: User in database and created event (board user)
        val user = createAndSaveUser("boarduser", "boarduser@example.com", enabled = false)
        val event = UserCreated(user.id!!, createdByBoard = true)

        // When: Event is handled
        listener.onUserCreated(event)

        // Then: Member activation email job is scheduled
        val jobs = findJobsByType(EmailJobs.Recovery.type)
        assertThat(jobs)
            .describedAs("Should schedule one recovery email job")
            .hasSize(1)

        val jobPayload = jobs.first().payload
        assertThat(jobPayload)
            .describedAs("Job payload should contain userId")
            .contains("\"userId\":${user.id}")
            .contains("\"tokenPurpose\":\"MEMBER_ACTIVATION\"")
    }

    // The account is already committed when this runs, so a throw here answered the
    // registration with a 500 for an account that exists — no token, no way in. The
    // dispatch runs in its own transaction, whose boundary sits inside the catch: a
    // failed write marks it rollback-only, and the commit would otherwise throw past a
    // catch placed within it.
    @Test
    fun `a dispatch that fails does not come back out at the registration`() {
        val user = createAndSaveUser("failuser", "failuser@example.com", enabled = false)
        val event = UserCreated(user.id!!, createdByBoard = false)
        val failing = RecoveryEventListener(
            object : ActivationEmailDispatcher(jobs, activationService) {
                override fun dispatchFor(event: UserCreated) = throw IllegalStateException("mail is down")
            },
        )

        assertThatCode { failing.onUserCreated(event) }.doesNotThrowAnyException()
    }

    @Test
    fun `an account whose activation could not be issued still exists`() {
        val user = createAndSaveUser("keptuser", "keptuser@example.com", enabled = false)
        val failing = RecoveryEventListener(
            object : ActivationEmailDispatcher(jobs, activationService) {
                override fun dispatchFor(event: UserCreated) = throw IllegalStateException("mail is down")
            },
        )

        failing.onUserCreated(UserCreated(user.id!!, createdByBoard = false))

        assertThat(findJobsByType(EmailJobs.Recovery.type)).isEmpty()
    }

    private fun createAndSaveUser(username: String, email: String, enabled: Boolean): User {
        val user = User(
            username = username,
            email = email,
            password = requireNotNull(passwordEncoder.encode("Password123!")) { "PasswordEncoder returned null hash" },
            initials = "TU",
            firstName = "Test",
            lastName = "User",
            phoneNumber = "06${System.currentTimeMillis().toString().takeLast(8)}",
            discord = "$username#0001"
        )
        user.enabled = enabled
        user.roles = mutableSetOf(Role.MEMBER)
        return persist(user)
    }
}
