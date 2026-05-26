package net.blueshell.api.platform.integration.calendar.job

import net.blueshell.api.platform.integration.calendar.application.job.SyncCalendarEventJob
import net.blueshell.api.platform.integration.sync.application.CalendarSyncService
import net.blueshell.api.shared.job.CalendarJobs
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import tools.jackson.databind.ObjectMapper

class SyncCalendarEventJobTest {

    private val objectMapper = ObjectMapper()
    private val calendarSync: CalendarSyncService = mock()
    private val job = SyncCalendarEventJob(objectMapper, calendarSync)

    @Test
    fun `delegates to CalendarSyncService sync with the payload eventId`() {
        job.handle(objectMapper.writeValueAsString(CalendarJobs.SyncCalendarEventPayload(7L)))

        verify(calendarSync).sync(eq(7L))
    }
}
