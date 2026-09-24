package net.blueshell.api.sync.domain

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.jobs.api.RetrySchedule
import net.blueshell.api.shared.job.DiscordPostJobs
import net.blueshell.api.shared.job.JobQueue
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/* From two minutes, doubling to two hours: about ten hours of trying before it gives up. */
private val THROUGH_THE_DAY = RetrySchedule(10, Duration.ofMinutes(2), 2.0, Duration.ofHours(2))

/**
 * The events-info post; once it is up, the Discord event beside it is queued at once. Type and
 * schedule are getters over constants, not fields, in all three: the handlers are proxied, and a
 * proxy's own fields are empty.
 */
@Component
class DiscordAnnouncementJob(
    objectMapper: ObjectMapper,
    private val posts: DiscordEventPosts,
    private val lock: DiscordEventLock,
    private val jobs: JobQueue,
) : AbstractJsonJobHandler<DiscordPostJobs.EventPostPayload>(objectMapper, DiscordPostJobs.Announcement.payloadType) {
    override val jobType: String get() = DiscordPostJobs.Announcement.type
    override val retrySchedule: RetrySchedule get() = THROUGH_THE_DAY

    override fun handlePayload(payload: DiscordPostJobs.EventPostPayload) {
        if (lock.holding(payload.eventId) { posts.keepAnnouncement(payload.eventId) }) {
            jobs.runAsync(DiscordPostJobs.DiscordEvent, payload)
        }
    }
}

@Component
class DiscordCalendarPostJob(
    objectMapper: ObjectMapper,
    private val posts: DiscordEventPosts,
    private val lock: DiscordEventLock,
) : AbstractJsonJobHandler<DiscordPostJobs.EventPostPayload>(objectMapper, DiscordPostJobs.CalendarPost.payloadType) {
    override val jobType: String get() = DiscordPostJobs.CalendarPost.type
    override val retrySchedule: RetrySchedule get() = THROUGH_THE_DAY

    override fun handlePayload(payload: DiscordPostJobs.EventPostPayload) =
        lock.holding(payload.eventId) { posts.keepCalendarPost(payload.eventId) }
}

@Component
class DiscordEventJob(
    objectMapper: ObjectMapper,
    private val posts: DiscordEventPosts,
    private val lock: DiscordEventLock,
) : AbstractJsonJobHandler<DiscordPostJobs.EventPostPayload>(objectMapper, DiscordPostJobs.DiscordEvent.payloadType) {
    override val jobType: String get() = DiscordPostJobs.DiscordEvent.type
    override val retrySchedule: RetrySchedule get() = THROUGH_THE_DAY

    override fun handlePayload(payload: DiscordPostJobs.EventPostPayload) =
        lock.holding(payload.eventId) { posts.keepDiscordEvent(payload.eventId) }
}
