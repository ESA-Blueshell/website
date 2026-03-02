package net.blueshell.api.platform.integration.calendar.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.calendar.CalendarEventData
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.platform.integration.mock.MockCalendarAdapter
import net.blueshell.api.shared.job.CalendarEventRef
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.temporal.ChronoUnit

class SyncEventToCalendarJobTest : ServiceTestSupport() {

    @Autowired
    private lateinit var syncEventToCalendarJob: SyncEventToCalendarJob

    @Autowired
    private lateinit var eventService: EventService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var mockCalendarAdapter: MockCalendarAdapter

    @BeforeEach
    fun clearCalendar() {
        mockCalendarAdapter.clear()
    }

    @Test
    fun `removes calendar event and clears google id for unapproved event`() {
        val (event, googleId) = createEventWithCalendarEntry()
        event.approved = false
        persist(event)
        val payload = objectMapper.writeValueAsString(CalendarEventRef(event.id!!))

        syncEventToCalendarJob.handle(payload)

        val updated = eventService.findById(event.id!!)
        assertThat(updated.googleId).isNull()
        assertThat(mockCalendarAdapter.findByExternalId(googleId)).isNull()
    }

    @Test
    fun `removes calendar event for soft deleted event`() {
        val (event, googleId) = createEventWithCalendarEntry()
        softDeleteEvent(event.id!!)
        val payload = objectMapper.writeValueAsString(CalendarEventRef(event.id!!))

        syncEventToCalendarJob.handle(payload)

        val deleted = eventService.findByIdIncludingDeletedOrNull(event.id!!)
        assertThat(deleted).isNotNull
        // googleId not cleared for soft-deleted events (entity may not be writable)
        assertThat(mockCalendarAdapter.findByExternalId(googleId)).isNull()
    }

    @Test
    fun `skips sync for hard deleted event`() {
        val payload = objectMapper.writeValueAsString(CalendarEventRef(999999L))

        // Should not throw
        syncEventToCalendarJob.handle(payload)
    }

    @Test
    fun `adds approved event to calendar`() {
        val event = persist(buildEvent(approved = true))
        val payload = objectMapper.writeValueAsString(CalendarEventRef(event.id!!))

        syncEventToCalendarJob.handle(payload)

        val updated = eventService.findById(event.id!!)
        assertThat(updated.googleId).isNotNull()
    }

    @Test
    fun `updates calendar event when approved event details change`() {
        val (event, googleId) = createEventWithCalendarEntry()
        val originalTitle = event.title

        // Change event title
        event.title = "Updated Calendar Event Title"
        persist(event)

        val payload = objectMapper.writeValueAsString(CalendarEventRef(event.id!!))
        syncEventToCalendarJob.handle(payload)

        // Same externalId, updated title
        val storedEvent = mockCalendarAdapter.findByExternalId(googleId)
        assertThat(storedEvent).isNotNull
        assertThat(storedEvent!!.title).isEqualTo("Updated Calendar Event Title")
        assertThat(storedEvent.title).isNotEqualTo(originalTitle)

        // googleId should remain the same
        val updated = eventService.findById(event.id!!)
        assertThat(updated.googleId).isEqualTo(googleId)
    }

    private fun createEventWithCalendarEntry(): Pair<Event, String> {
        val event = persist(buildEvent(approved = true))

        val ref = mockCalendarAdapter.addEvent(
            event.id!!,
            CalendarEventData(
                title = event.title,
                location = event.location,
                description = event.description,
                startTime = event.startTime,
                endTime = event.endTime,
                approved = event.approved
            )
        )

        event.googleId = ref.externalId
        val updated = persist(event)
        return updated to ref.externalId
    }

    private fun buildEvent(approved: Boolean): Event {
        val committee = persist(
            Committee(
                name = "Calendar Integration Committee ${System.currentTimeMillis()}",
                description = "Committee for calendar integration tests"
            )
        )
        return Event(
            committee = committee,
            title = "Calendar Sync Test Event ${System.currentTimeMillis()}",
            description = "Calendar sync flow test",
            location = "Campus Hall",
            startTime = Instant.now().plus(1, ChronoUnit.DAYS),
            endTime = Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
            approved = approved
        )
    }

    private fun softDeleteEvent(eventId: Long) {
        transactionTemplate.executeWithoutResult {
            entityManager.createNativeQuery(
                """
                UPDATE events
                SET deleted_at = NOW(), version = version + 1
                WHERE id = :id
                """.trimIndent()
            ).setParameter("id", eventId).executeUpdate()
        }
    }
}
