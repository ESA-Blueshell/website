package net.blueshell.api.sync.domain

import net.blueshell.api.shared.job.JobDefinition

object CalendarJobs {
    object SyncCalendarEvent : JobDefinition<SyncCalendarEventPayload> {
        override val type: String = "calendar.sync-event"
        override val payloadType: Class<SyncCalendarEventPayload> = SyncCalendarEventPayload::class.java
    }

    data class SyncCalendarEventPayload(
        val eventId: Long,
    )
}
