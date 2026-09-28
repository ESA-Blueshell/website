package net.blueshell.api.sync.domain

import net.blueshell.api.shared.job.JobDefinition

object CalendarJobs {
    object SyncCalendarEvent : JobDefinition<SyncCalendarEventPayload> {
        override val type: String = "calendar.sync-event"
        override val payloadType: Class<SyncCalendarEventPayload> = SyncCalendarEventPayload::class.java
    }

    /** The daily sweep: every event that has not ended and whose sync never ran. */
    object SyncUnsyncedEvents : JobDefinition<SyncUnsyncedEventsPayload> {
        override val type: String = "calendar.sync-unsynced"
        override val payloadType: Class<SyncUnsyncedEventsPayload> = SyncUnsyncedEventsPayload::class.java
    }

    data class SyncCalendarEventPayload(
        val eventId: Long,
    )

    data class SyncUnsyncedEventsPayload(
        val unused: Unit = Unit,
    )
}
