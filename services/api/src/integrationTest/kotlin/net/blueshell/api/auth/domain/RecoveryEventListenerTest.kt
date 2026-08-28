package net.blueshell.api.auth.domain

import net.blueshell.api.domain.user.application.event.UserCreated
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
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
