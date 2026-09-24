package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventChanged
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.shared.job.DiscordPostJobs
import net.blueshell.api.shared.job.DiscordPostTrigger
import net.blueshell.api.shared.job.JobQueue
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * What sets a reconcile going: every change to an event; the morning run at 08:00 for every
 * approved event near its time; and an hourly look at events on now or just over, which takes a
 * Discord event down within the hour of its end rather than the next morning.
 */
@Component
class DiscordEventPostTriggers(
    private val jobs: JobQueue,
    private val events: EventPosts,
) {
    /* Settable for tests only. */
    internal var clock: Clock = Clock.systemUTC()

    @ApplicationModuleListener
    fun on(event: EventChanged) = queue(event.eventId, DiscordPostTrigger.CHANGE, null)

    @Scheduled(cron = "0 0 8 * * *", zone = DiscordPostSchedule.ZONE_ID)
    fun morning() {
        runMorning(clock.instant())
    }

    /* A change's rules: it puts up only what is due on the event's own day, so nothing comes early. */
    @Scheduled(cron = "0 5 * * * *", zone = DiscordPostSchedule.ZONE_ID)
    fun hourly() {
        val now = clock.instant()
        events.approvedOverlapping(now.minus(HOURLY_BEHIND), now).forEach { queue(it, DiscordPostTrigger.CHANGE, null) }
    }

    /** The morning run as if it were [at]; answers how many events it looked at. */
    fun runMorning(at: Instant): Int {
        val near = events.approvedOverlapping(at.minus(MORNING_BEHIND), at.plus(MORNING_AHEAD))
        near.forEach { queue(it, DiscordPostTrigger.MORNING, at) }
        return near.size
    }

    private fun queue(
        eventId: Long,
        trigger: DiscordPostTrigger,
        at: Instant?,
    ) {
        jobs.runAsync(DiscordPostJobs.Reconcile, DiscordPostJobs.ReconcilePayload(eventId, trigger, at))
    }

    private companion object {
        /* An events-calendar post comes down the morning after the event ends. */
        val MORNING_BEHIND: Duration = Duration.ofDays(2)

        /* The events-info post goes out a fortnight ahead of the event's day. */
        val MORNING_AHEAD: Duration = Duration.ofDays(15)

        val HOURLY_BEHIND: Duration = Duration.ofHours(2)
    }
}
