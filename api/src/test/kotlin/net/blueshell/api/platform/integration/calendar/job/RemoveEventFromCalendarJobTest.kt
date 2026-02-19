package net.blueshell.api.platform.integration.calendar.job

import com.fasterxml.jackson.databind.ObjectMapper
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

class RemoveEventFromCalendarJobTest : ServiceTestSupport() {

    @Autowired
    private lateinit var removeEventFromCalendarJob: RemoveEventFromCalendarJob

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
    fun `removes calendar event and clears google id for active event`() {
        val (event, googleId) = createEventWithCalendarEntry()
        val payload = objectMapper.writeValueAsString(CalendarEventRef(event.id!!))

        removeEventFromCalendarJob.handle(payload)

        val updated = eventService.findById(event.id!!)
        assertThat(updated.googleId).isNull()
        assertThat(mockCalendarAdapter.findByExternalId(googleId)).isNull()
    }

    @Test
    fun `removes calendar event for soft deleted event`() {
        val (event, googleId) = createEventWithCalendarEntry()
        softDeleteEvent(event.id!!)
        val payload = objectMapper.writeValueAsString(CalendarEventRef(event.id!!))

        removeEventFromCalendarJob.handle(payload)

        val deleted = eventService.findByIdIncludingDeletedOrNull(event.id!!)
        assertThat(deleted).isNotNull
        assertThat(deleted!!.googleId).isEqualTo(googleId)
        assertThat(mockCalendarAdapter.findByExternalId(googleId)).isNull()
    }

    private fun createEventWithCalendarEntry(): Pair<Event, String> {
        val committee = persist(
            Committee(
                name = "Calendar Integration Committee",
                description = "Committee for calendar integration tests"
            )
        )
        val event = persist(
            Event(
                committee = committee,
                title = "Calendar Remove Test Event",
                description = "Calendar remove flow test",
                location = "Campus Hall",
                startTime = Instant.now().plus(1, ChronoUnit.DAYS),
                endTime = Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                approved = true
            )
        )

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
