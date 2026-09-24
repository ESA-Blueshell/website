package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventChanged
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.shared.job.DiscordPostJobs
import net.blueshell.api.shared.job.JobQueue
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * What sets a reconcile going: every change to an event, and the morning run at 08:00 Amsterdam
 * time for every approved event near its time, from the day its posts may be due to the morning
 * its events-calendar post comes down.
 */
@Component
class DiscordEventPostTriggers(
    private val jobs: JobQueue,
    private val events: EventPosts,
) {
    /* Settable so a test can fix the moment; nothing else changes it. */
    internal var clock: Clock = Clock.systemUTC()

    @ApplicationModuleListener
    fun on(event: EventChanged) {
        jobs.runAsync(DiscordPostJobs.Reconcile, DiscordPostJobs.ReconcilePayload(event.eventId, Trigger.CHANGE.name))
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Amsterdam")
    fun morning() {
        runMorning(clock.instant())
    }

    /** The morning run as if it were [at]; answers how many events it looked at. */
    fun runMorning(at: Instant): Int {
        val near = events.approvedOverlapping(at.minus(BEHIND), at.plus(AHEAD))
        near.forEach { jobs.runAsync(DiscordPostJobs.Reconcile, DiscordPostJobs.ReconcilePayload(it, Trigger.MORNING.name, at)) }
        return near.size
    }

    private companion object {
        /* An events-calendar post comes down the morning after the event ends. */
        val BEHIND: Duration = Duration.ofDays(2)

        /* The events-info post goes out a fortnight ahead of the event's day. */
        val AHEAD: Duration = Duration.ofDays(15)
    }
}
