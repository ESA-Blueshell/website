package net.blueshell.api.sync.domain

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.jobs.api.RetrySchedule
import net.blueshell.api.shared.job.JobDefinition
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration

// From two minutes, doubling to two hours: about ten hours of trying before it gives up.
private val THROUGH_THE_DAY = RetrySchedule(10, Duration.ofMinutes(2), 2.0, Duration.ofHours(2))

/**
 * One of the bot's three jobs for an event: keeps its thing under the event's lock and reports
 * what that did, or why it did nothing. The schedule is a getter over a constant, not a field: the
 * handlers are proxied, and a proxy's own fields are empty.
 */
abstract class DiscordEventPostJob(
    objectMapper: ObjectMapper,
    private val lock: DiscordEventLock,
    definition: JobDefinition<DiscordPostJobs.EventPostPayload>,
) : AbstractJsonJobHandler<DiscordPostJobs.EventPostPayload>(objectMapper, definition) {
    override val retrySchedule: RetrySchedule get() = THROUGH_THE_DAY

    protected abstract fun keep(
        eventId: Long,
        forced: Boolean,
    ): Kept

    override fun handlePayload(payload: DiscordPostJobs.EventPostPayload) {
        val kept = lock.holding(payload.eventId) { keep(payload.eventId, forced) }
        kept.skipped?.let(::skip)
        kept.effect?.let { did(it, kept.link) }
    }
}

/** The events-info post. */
@Component
class DiscordAnnouncementJob(
    objectMapper: ObjectMapper,
    private val posts: DiscordEventPosts,
    lock: DiscordEventLock,
) : DiscordEventPostJob(objectMapper, lock, DiscordPostJobs.Announcement) {
    override fun keep(
        eventId: Long,
        forced: Boolean,
    ) = posts.keepAnnouncement(eventId, forced)
}

@Component
class DiscordCalendarPostJob(
    objectMapper: ObjectMapper,
    private val posts: DiscordEventPosts,
    lock: DiscordEventLock,
) : DiscordEventPostJob(objectMapper, lock, DiscordPostJobs.CalendarPost) {
    override fun keep(
        eventId: Long,
        forced: Boolean,
    ) = posts.keepCalendarPost(eventId, forced)
}

@Component
class DiscordEventJob(
    objectMapper: ObjectMapper,
    private val posts: DiscordEventPosts,
    lock: DiscordEventLock,
) : DiscordEventPostJob(objectMapper, lock, DiscordPostJobs.DiscordEvent) {
    override fun keep(
        eventId: Long,
        forced: Boolean,
    ) = posts.keepDiscordEvent(eventId)
}
