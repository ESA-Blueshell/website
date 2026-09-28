package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventChange
import net.blueshell.api.event.api.EventChanged
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.platform.integration.mock.MockCalendarAdapter
import net.blueshell.api.platform.integration.mock.MockContactAdapter
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.sync.persistence.ExternalIdMappingRepository
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.testsupport.runJob
import net.blueshell.api.user.api.UserCreated
import net.blueshell.api.user.api.UserDeleted
import net.blueshell.api.user.api.UserUpdated
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
import tools.jackson.databind.ObjectMapper
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

    @Autowired private lateinit var unsyncedEvents: SyncUnsyncedEventsJob

    @Autowired private lateinit var objectMapper: ObjectMapper

    @Autowired private lateinit var tx: TransactionTemplate

    // job_executions / external_id_mapping rows are wiped by TestCleanUpListener
    // between tests, so this reset only takes care of in-memory adapter state.
    // Asserting on the mapping (a
    // row only visible AFTER the job's transaction commits) is the test's
    // signal that the whole pipeline ran; mocking the adapter alone is not
    // enough because the mock is touched in-memory before the surrounding
    // transaction commits.
    @BeforeEach
    fun reset() {
        mockContactAdapter.clear()
        mockCalendarAdapter.clear()
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
            val current =
                mappings.findByAggregateTypeAndAggregateIdAndSystem(
                    "USER",
                    user.id!!,
                    TargetSystem.BREVO.name,
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
    fun `publishing UserUpdated pushes the edit to the user's contact`() {
        val user = createUserWithRole(Role.MEMBER)
        tx.executeWithoutResult { publisher.publishEvent(UserCreated(user.id!!)) }
        awaitMapping("USER", user.id!!, TargetSystem.BREVO)
        jdbc.update("UPDATE users SET first_name = ? WHERE id = ?", "Renamed", user.id)

        tx.executeWithoutResult { publisher.publishEvent(UserUpdated(user.id!!)) }

        awaitCondition {
            mockContactAdapter.getAllContacts().values.any { it.email == user.email && it.firstName == "Renamed" }
        }
        assertThat(mockContactAdapter.getAllContacts().values.filter { it.email == user.email })
            .describedAs("the edit updates the contact rather than adding another")
            .hasSize(1)
    }

    @Test
    fun `publishing EventChanged for an edit updates the event on the calendar`() {
        val event: Event = createEventFixture()
        tx.executeWithoutResult { publisher.publishEvent(EventChanged(event.id!!, EventChange.CREATED)) }
        val externalId = awaitMapping("EVENT", event.id!!, TargetSystem.GOOGLE_CALENDAR).externalId!!
        jdbc.update("UPDATE events SET title = ? WHERE id = ?", "Renamed event", event.id)

        tx.executeWithoutResult { publisher.publishEvent(EventChanged(event.id!!, EventChange.UPDATED)) }

        awaitCondition { mockCalendarAdapter.findByExternalId(externalId)?.title == "Renamed event" }
        assertThat(mockCalendarAdapter.getEventCount()).isEqualTo(1)
    }

    @Test
    fun `publishing EventChanged for an unapproval takes the event off the calendar`() {
        val event: Event = createEventFixture()
        tx.executeWithoutResult { publisher.publishEvent(EventChanged(event.id!!, EventChange.CREATED)) }
        val externalId = awaitMapping("EVENT", event.id!!, TargetSystem.GOOGLE_CALENDAR).externalId!!
        jdbc.update("UPDATE events SET approved = false WHERE id = ?", event.id)

        tx.executeWithoutResult { publisher.publishEvent(EventChanged(event.id!!, EventChange.UNAPPROVED)) }

        awaitCondition {
            mappings.findByAggregateTypeAndAggregateIdAndSystem("EVENT", event.id!!, TargetSystem.GOOGLE_CALENDAR.name)?.externalId == null
        }
        assertThat(mockCalendarAdapter.findByExternalId(externalId)).isNull()
    }

    @Test
    fun `the calendar sweep syncs an event whose sync never ran`() {
        val event: Event = createEventFixture()

        unsyncedEvents.runJob(objectMapper.writeValueAsString(CalendarJobs.SyncUnsyncedEventsPayload()))

        val mapping = awaitMapping("EVENT", event.id!!, TargetSystem.GOOGLE_CALENDAR)
        assertThat(mockCalendarAdapter.findByExternalId(mapping.externalId!!)).isNotNull
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
