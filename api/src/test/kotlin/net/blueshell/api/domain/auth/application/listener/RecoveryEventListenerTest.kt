package net.blueshell.api.domain.auth.application.listener

import net.blueshell.api.domain.user.application.event.UserCreated
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

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

    @Test
    fun `schedules user activation email when user is created`() {
        // Given: User created event (non-board user)
        val userId = 123L
        val event = UserCreated(userId, createdByBoard = false)

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
            .contains("\"userId\":$userId")
    }

    @Test
    fun `schedules member activation email when user is created by board`() {
        // Given: User created event (board user)
        val userId = 456L
        val event = UserCreated(userId, createdByBoard = true)

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
            .contains("\"userId\":$userId")
    }
}
