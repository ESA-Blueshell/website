package net.blueshell.api.platform.integration.sync

import net.blueshell.api.domain.event.application.event.EventChange
import net.blueshell.api.domain.event.application.event.EventChanged
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.user.application.event.UserCreated
import net.blueshell.api.domain.user.application.event.UserDeleted
import net.blueshell.api.platform.integration.mock.MockCalendarAdapter
import net.blueshell.api.platform.integration.mock.MockContactAdapter
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.platform.integration.sync.persistence.repository.ExternalIdMappingRepository
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration

/** Verifies that publishing user / event domain events drives the queued sync pipeline end-to-end. */
@SpringBootTest
@TestPropertySource(properties = ["app.jobs.auto-dispatch=true"])
class SyncListenerIT : UserTestSupport() {

    @Autowired private lateinit var publisher: ApplicationEventPublisher
    @Autowired private lateinit var mockContactAdapter: MockContactAdapter
    @Autowired private lateinit var mockCalendarAdapter: MockCalendarAdapter
    @Autowired private lateinit var mappings: ExternalIdMappingRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var tx: TransactionTemplate

    // job_executions / external_id_mapping rows are wiped by TestCleanUpListener
    // between tests, so this reset only takes care of in-memory adapter state
    // and the Modulith event_publication outbox. Asserting on the mapping (a
    // row only visible AFTER the job's transaction commits) is the test's
    // signal that the whole pipeline ran; mocking the adapter alone is not
    // enough because the mock is touched in-memory before the surrounding
    // transaction commits.
    @BeforeEach
    fun reset() {
        mockContactAdapter.clear()
        mockCalendarAdapter.clear()
        jdbc.update("DELETE FROM EVENT_PUBLICATION")
    }

    @Test
    fun `publishing UserCreated enqueues a SyncContact job that pushes to every contact target`() {
        val user = createUserWithRole(Role.MEMBER)

        tx.executeWithoutResult { publisher.publishEvent(UserCreated(user.id!!)) }

        val mapping = awaitMapping("USER", user.id!!, TargetSystem.BREVO)
        assertThat(mapping.externalId).describedAs("external id is set after the push").isNotBlank
        assertThat(mockContactAdapter.getAllContacts().values)
            .describedAs("adapter received the user")
            .anySatisfy { contact -> assertThat(contact.email).isEqualTo(user.email) }
    }

    @Test
    fun `publishing UserDeleted enqueues a RemoveContact job that clears every contact target`() {
        val user = createUserWithRole(Role.MEMBER)
        tx.executeWithoutResult { publisher.publishEvent(UserCreated(user.id!!)) }
        val mapping = awaitMapping("USER", user.id!!, TargetSystem.BREVO)
        val externalIdLong = mapping.externalId!!.toLong()

        tx.executeWithoutResult { publisher.publishEvent(UserDeleted(user.id!!)) }

        awaitCondition {
            val current = mappings.findByAggregateTypeAndAggregateIdAndSystem(
                "USER", user.id!!, TargetSystem.BREVO.name,
            )
            current?.externalId == null
        }
        assertThat(mockContactAdapter.getAllContacts().keys)
            .describedAs("adapter dropped the contact after the remove job ran")
            .doesNotContain(externalIdLong)
    }

    @Test
    fun `publishing EventChanged enqueues a SyncCalendarEvent job that pushes the approved event`() {
        val event: Event = createEventFixture()
        tx.executeWithoutResult { publisher.publishEvent(EventChanged(event.id!!, EventChange.CREATED)) }

        val mapping = awaitMapping("EVENT", event.id!!, TargetSystem.GOOGLE_CALENDAR)
        assertThat(mapping.externalId).describedAs("calendar external id is stored").isNotBlank
        assertThat(mockCalendarAdapter.getAllEvents()).describedAs("adapter received the event").isNotEmpty
    }

    @Test
    fun `Modulith writes an event_publication row that completes after the listener enqueues`() {
        val user = createUserWithRole(Role.MEMBER)
        tx.executeWithoutResult { publisher.publishEvent(UserCreated(user.id!!)) }
        awaitMapping("USER", user.id!!, TargetSystem.BREVO)

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
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50)).until(condition)
    }

    private fun awaitMapping(
        aggregateType: String,
        aggregateId: Long,
        system: TargetSystem,
    ): ExternalIdMapping {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50)).until {
            mappings.findByAggregateTypeAndAggregateIdAndSystem(aggregateType, aggregateId, system.name) != null
        }
        return mappings.findByAggregateTypeAndAggregateIdAndSystem(aggregateType, aggregateId, system.name)!!
    }
}
