package net.blueshell.api.platform.integration.sync

import net.blueshell.api.domain.event.application.event.EventChange
import net.blueshell.api.domain.event.application.event.EventChanged
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.user.application.event.UserCreated
import net.blueshell.api.domain.user.application.event.UserDeleted
import net.blueshell.api.platform.integration.mock.MockCalendarAdapter
import net.blueshell.api.platform.integration.mock.MockContactAdapter
import net.blueshell.api.platform.integration.sync.persistence.repository.ExternalIdMappingRepository
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.CalendarJobs
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration

/** Verifies that publishing user / event domain events drives the queued sync pipeline end-to-end. */
@SpringBootTest
class SyncListenerIT : UserTestSupport() {

    @Autowired private lateinit var publisher: ApplicationEventPublisher
    @Autowired private lateinit var mockContactAdapter: MockContactAdapter
    @Autowired private lateinit var mockCalendarAdapter: MockCalendarAdapter
    @Autowired private lateinit var mappings: ExternalIdMappingRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var tx: TransactionTemplate

    @BeforeEach
    fun reset() {
        mockContactAdapter.clear()
        mockCalendarAdapter.clear()
        mappings.deleteAll()
        jdbc.update("DELETE FROM EVENT_PUBLICATION")
        jdbc.update("DELETE FROM JOB_EXECUTION")
    }

    @Test
    fun `publishing UserCreated enqueues a SyncContact job that pushes to every contact target`() {
        val user = createUserWithRole(Role.MEMBER)

        tx.executeWithoutResult { publisher.publishEvent(UserCreated(user.id!!)) }

        awaitJobSuccess(ContactJobs.SyncContact.type)
        awaitCondition { mockContactAdapter.getAllContacts().any { it.value.email == user.email } }

        val mapping = mappings.findByAggregateTypeAndAggregateIdAndSystem(
            "USER", user.id!!, TargetSystem.BREVO.name,
        )
        assertThat(mapping).describedAs("external_id_mapping should hold the new external id").isNotNull
        assertThat(mapping!!.externalId).isNotBlank
    }

    @Test
    fun `publishing UserDeleted enqueues a RemoveContact job that clears every contact target`() {
        val user = createUserWithRole(Role.MEMBER)
        tx.executeWithoutResult { publisher.publishEvent(UserCreated(user.id!!)) }
        awaitJobSuccess(ContactJobs.SyncContact.type)
        awaitCondition { mockContactAdapter.getAllContacts().isNotEmpty() }
        val externalId = mockContactAdapter.getAllContacts().keys.single()

        tx.executeWithoutResult { publisher.publishEvent(UserDeleted(user.id!!)) }

        awaitJobSuccess(ContactJobs.RemoveContact.type)
        awaitCondition { externalId !in mockContactAdapter.getAllContacts().keys }
        val mapping = mappings.findByAggregateTypeAndAggregateIdAndSystem(
            "USER", user.id!!, TargetSystem.BREVO.name,
        )
        assertThat(mapping?.externalId).describedAs("mapping external id is cleared on delete").isNull()
    }

    @Test
    fun `publishing EventChanged enqueues a SyncCalendarEvent job that pushes the approved event`() {
        val event: Event = createEventFixture()
        tx.executeWithoutResult { publisher.publishEvent(EventChanged(event.id!!, EventChange.CREATED)) }

        awaitJobSuccess(CalendarJobs.SyncCalendarEvent.type)
        awaitCondition { mockCalendarAdapter.getAllEvents().isNotEmpty() }
        val mapping = mappings.findByAggregateTypeAndAggregateIdAndSystem(
            "EVENT", event.id!!, TargetSystem.GOOGLE_CALENDAR.name,
        )
        assertThat(mapping?.externalId).describedAs("calendar external id should be stored").isNotNull
    }

    @Test
    fun `Modulith writes an event_publication row that completes after the listener enqueues`() {
        val user = createUserWithRole(Role.MEMBER)
        tx.executeWithoutResult { publisher.publishEvent(UserCreated(user.id!!)) }
        awaitJobSuccess(ContactJobs.SyncContact.type)

        val rows = jdbc.queryForList(
            "SELECT LISTENER_ID, COMPLETION_DATE FROM EVENT_PUBLICATION WHERE EVENT_TYPE = ?",
            UserCreated::class.java.name,
        )
        assertThat(rows).describedAs("Modulith should persist the UserCreated publication").isNotEmpty
        val contactListenerRow = rows.firstOrNull { (it["LISTENER_ID"] as String).contains("ContactSyncListener") }
        assertThat(contactListenerRow).describedAs("listener row for ContactSyncListener must exist").isNotNull
        assertThat(contactListenerRow!!["COMPLETION_DATE"])
            .describedAs("row should be marked complete after the listener returns")
            .isNotNull
    }

    private fun awaitCondition(condition: () -> Boolean) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50)).until(condition)
    }

    private fun awaitJobSuccess(
        jobType: String,
        expectedCount: Int = 1,
        timeoutMs: Long = 10_000,
        pollMs: Long = 100,
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
            .describedAs("Expected $expectedCount successful $jobType jobs, but found $successCount among $executions")
            .isGreaterThanOrEqualTo(expectedCount)
    }
}
