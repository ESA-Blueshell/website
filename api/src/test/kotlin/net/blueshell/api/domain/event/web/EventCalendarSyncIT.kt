package net.blueshell.api.domain.event.web

import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.platform.integration.mock.MockCalendarAdapter
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.CalendarEventRef
import net.blueshell.api.shared.job.CalendarJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * End-to-end tests verifying that event CRUD operations correctly sync
 * calendar entries via the SyncEvent job.
 */
@SpringBootTest
@TestPropertySource(properties = ["app.jobs.auto-dispatch=true"])
class EventCalendarSyncIT : UserTestSupport() {

    @Autowired
    private lateinit var mockCalendarAdapter: MockCalendarAdapter

    @Autowired
    private lateinit var eventService: EventService

    @Autowired
    private lateinit var jobs: TrackedJobDispatcher

    @BeforeEach
    fun clearMocks() {
        mockCalendarAdapter.clear()
    }

    @Test
    fun `creating approved event adds to calendar`() {
        val event = createEventFixture(approved = true)

        // Sync the event to calendar via auto-dispatch
        enqueueInTransaction {
            jobs.enqueue(
                CalendarJobs.SyncEvent,
                CalendarEventRef(event.id!!)
            )
        }

        awaitJobStatus(CalendarJobs.SyncEvent.type, JobExecutionStatus.SUCCESS)

        assertThat(mockCalendarAdapter.getAllEvents())
            .describedAs("Calendar should contain the event")
            .isNotEmpty()

        val updated = eventService.findById(event.id!!)
        assertThat(updated.googleId)
            .describedAs("Event should have a googleId after calendar sync")
            .isNotNull()
    }

    @Test
    fun `deleting approved event removes from calendar`() {
        val board = createUserWithRole(Role.BOARD)
        val event = createEventFixture(approved = true)

        // Sync the event to calendar first via dispatcher
        enqueueInTransaction {
            jobs.enqueue(
                CalendarJobs.SyncEvent,
                CalendarEventRef(event.id!!)
            )
        }
        awaitJobStatus(CalendarJobs.SyncEvent.type, JobExecutionStatus.SUCCESS)

        assertThat(mockCalendarAdapter.getAllEvents()).isNotEmpty()

        // Delete the event via HTTP
        mvc.perform(
            delete("/events/{eventId}", event.id)
                .with(bearer(board))
        )
            .andExpect(status().isNoContent)

        awaitJobSuccess(CalendarJobs.SyncEvent.type, expectedCount = 2)

        assertThat(mockCalendarAdapter.getAllEvents())
            .describedAs("Calendar should be empty after event deletion")
            .isEmpty()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun enqueueInTransaction(enqueue: () -> Unit) {
        transactionTemplate.executeWithoutResult {
            enqueue()
        }
    }

    private fun awaitJobStatus(
        jobType: String,
        expected: JobExecutionStatus,
        timeoutMs: Long = 5_000,
        pollMs: Long = 100
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val executions = findJobsByType(jobType)
            if (executions.any { it.status == expected }) return
            Thread.sleep(pollMs)
        }

        val executions = findJobsByType(jobType)
        assertThat(executions)
            .describedAs("Expected at least one job execution for type $jobType")
            .isNotEmpty

        val statuses = executions.map { it.status }
        assertThat(statuses)
            .describedAs("Expected at least one $jobType job with status $expected")
            .contains(expected)
    }

    private fun awaitJobSuccess(
        jobType: String,
        expectedCount: Int,
        timeoutMs: Long = 5_000,
        pollMs: Long = 100
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val successCount = findJobsByType(jobType).count { it.status == JobExecutionStatus.SUCCESS }
            if (successCount >= expectedCount) return
            Thread.sleep(pollMs)
        }

        val executions = findJobsByType(jobType)
        val successCount = executions.count { it.status == JobExecutionStatus.SUCCESS }
        assertThat(successCount)
            .describedAs(
                "Expected $expectedCount successful $jobType jobs, but found $successCount. " +
                    "Statuses: ${executions.map { "${it.id}=${it.status}" }}"
            )
            .isGreaterThanOrEqualTo(expectedCount)
    }
}
