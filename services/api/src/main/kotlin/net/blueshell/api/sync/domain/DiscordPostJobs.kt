package net.blueshell.api.sync.domain

import net.blueshell.api.shared.job.JobDefinition

/**
 * The bot's three things for an event, one job type each so the job list says which one ran. The
 * payload names only the event: every run judges the event as it stands now, so two queued at
 * once are the same job and the queue keeps one.
 */
object DiscordPostJobs {
    /** The events-info post, in the announcement channel. */
    object Announcement : JobDefinition<EventPostPayload> {
        override val type: String = "discord.announcement"
        override val payloadType: Class<EventPostPayload> = EventPostPayload::class.java
        override val queuesBehindRunning: Boolean = true
    }

    /** The events-calendar post, up while the event's day lasts. */
    object CalendarPost : JobDefinition<EventPostPayload> {
        override val type: String = "discord.post"
        override val payloadType: Class<EventPostPayload> = EventPostPayload::class.java
        override val queuesBehindRunning: Boolean = true
    }

    /** The Discord event in the server's event list. */
    object DiscordEvent : JobDefinition<EventPostPayload> {
        override val type: String = "discord.event"
        override val payloadType: Class<EventPostPayload> = EventPostPayload::class.java
        override val queuesBehindRunning: Boolean = true
    }

    data class EventPostPayload(
        val eventId: Long,
    )
}
