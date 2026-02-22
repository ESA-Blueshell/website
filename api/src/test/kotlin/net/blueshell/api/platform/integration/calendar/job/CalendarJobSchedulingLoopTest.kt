package net.blueshell.api.platform.integration.calendar.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.calendar.CalendarEventData
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.platform.integration.mock.MockCalendarAdapter
import net.blueshell.api.shared.job.CalendarEventRef
import net.blueshell.api.shared.job.CalendarJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.temporal.ChronoUnit

class CalendarJobSchedulingLoopTest : ServiceTestSupport() {

    @Autowired
    private lateinit var eventService: EventService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var addEventToCalendarJob: AddEventToCalendarJob

    @Autowired
    private lateinit var syncEventToCalendarJob: SyncEventToCalendarJob

    @Autowired
    private lateinit var removeEventFromCalendarJob: RemoveEventFromCalendarJob

    @Autowired
    private lateinit var mockCalendarAdapter: MockCalendarAdapter

    @BeforeEach
    fun clearCalendar() {
        mockCalendarAdapter.clear()
    }

    @Test
    fun `processing add job does not enqueue follow-up sync job`() {
        val created = eventService.create(buildEvent(approved = true))

        assertThat(findJobsByType(CalendarJobs.AddEvent.type)).hasSize(1)
        assertThat(findJobsByType(CalendarJobs.SyncEvent.type)).isEmpty()

        addEventToCalendarJob.handle(payload(created.id!!))

        assertThat(findJobsByType(CalendarJobs.AddEvent.type)).hasSize(1)
        assertThat(findJobsByType(CalendarJobs.SyncEvent.type)).isEmpty()
        assertThat(eventService.findById(created.id!!).googleId).isNotNull()
    }

    @Test
    fun `processing sync job after approval keeps a single scheduled sync job`() {
        val event = persist(buildEvent(approved = false))
        event.approved = true
        val approved = eventService.update(event)

        assertThat(findJobsByType(CalendarJobs.SyncEvent.type)).hasSize(1)

        syncEventToCalendarJob.handle(payload(approved.id!!))

        assertThat(findJobsByType(CalendarJobs.SyncEvent.type)).hasSize(1)
        assertThat(eventService.findById(approved.id!!).googleId).isNotNull()
    }

    @Test
    fun `processing remove job after unapproval keeps a single scheduled remove job`() {
        val event = persist(buildEvent(approved = true))
        val ref = mockCalendarAdapter.addEvent(event.id!!, event.toCalendarData())
        val linked = persist(event.apply { googleId = ref.externalId })
        linked.approved = false
        val unapproved = eventService.update(linked)

        assertThat(findJobsByType(CalendarJobs.RemoveEvent.type)).hasSize(1)

        removeEventFromCalendarJob.handle(payload(unapproved.id!!))

        assertThat(findJobsByType(CalendarJobs.RemoveEvent.type)).hasSize(1)
        assertThat(eventService.findById(unapproved.id!!).googleId).isNull()
    }

    private fun payload(eventId: Long): String {
        return objectMapper.writeValueAsString(CalendarEventRef(eventId))
    }

    private fun buildEvent(approved: Boolean): Event {
        val committee = persist(
            Committee(
                name = "Calendar Job Committee ${System.currentTimeMillis()}",
                description = "Calendar job scheduling regression tests"
            )
        )

        return Event(
            committee = committee,
            title = "Calendar Job Event ${System.currentTimeMillis()}",
            description = "Calendar job loop prevention",
            location = "Campus Hall",
            startTime = Instant.now().plus(1, ChronoUnit.DAYS),
            endTime = Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
            approved = approved
        )
    }

    private fun Event.toCalendarData(): CalendarEventData {
        return CalendarEventData(
            title = title,
            location = location,
            description = description,
            startTime = startTime,
            endTime = endTime,
            approved = approved
        )
    }
}
