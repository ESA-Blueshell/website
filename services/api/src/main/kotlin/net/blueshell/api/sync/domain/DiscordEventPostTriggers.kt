package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventChanged
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.event.api.EventSignUpsChanged
import net.blueshell.api.shared.job.DiscordPostJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.tracking.Actor
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * What queues the bot's jobs for an event: every change to it; the morning run at 08:00 for every
 * approved event near its time; and an hourly look at events on now or just over, which takes a
 * Discord event down within the hour of its end rather than the next morning. Only a job with
 * something to do is queued: one for what is already out, or for what is due.
 */
@Component
class DiscordEventPostTriggers(
    private val jobs: JobQueue,
    private val events: EventPosts,
    private val ledger: PostLedger,
) {
    // Settable for tests only.
    internal var clock: Clock = Clock.systemUTC()

    @ApplicationModuleListener
    fun on(event: EventChanged) = queue(event.eventId, event.changeType.asTrigger(), event.actor)

    // Only the posts show the count, and only one already out has it to change.
    @ApplicationModuleListener
    fun on(signUps: EventSignUpsChanged) {
        val payload = DiscordPostJobs.EventPostPayload(signUps.eventId)
        val trigger = JobTrigger.SIGN_UPS_CHANGED
        if (out(signUps.eventId, DiscordArtefact.INFO_POST)) jobs.runAsync(DiscordPostJobs.Announcement, payload, trigger, signUps.actor)
        if (out(
                signUps.eventId,
                DiscordArtefact.CALENDAR_POST,
            )
        ) {
            jobs.runAsync(DiscordPostJobs.CalendarPost, payload, trigger, signUps.actor)
        }
    }

    @Scheduled(cron = "0 0 8 * * *", zone = DiscordPostSchedule.ZONE_ID)
    fun morning() {
        runMorning()
    }

    @Scheduled(cron = "0 5 * * * *", zone = DiscordPostSchedule.ZONE_ID)
    fun hourly() {
        val now = clock.instant()
        events
            .approvedOverlapping(now.minus(HOURLY_BEHIND), now)
            .filter { out(it, DiscordArtefact.DISCORD_EVENT) }
            .forEach { jobs.runAsync(DiscordPostJobs.DiscordEvent, DiscordPostJobs.EventPostPayload(it), JobTrigger.HOURLY_RUN) }
    }

    /** The morning run; answers how many events it looked at. */
    fun runMorning(): Int {
        val now = clock.instant()
        val near = events.approvedOverlapping(now.minus(MORNING_BEHIND), now.plus(MORNING_AHEAD))
        near.forEach { queue(it, JobTrigger.MORNING_RUN) }
        return near.size
    }

    private fun queue(
        eventId: Long,
        trigger: JobTrigger,
        actor: Actor? = null,
    ) {
        val due =
            events
                .of(eventId)
                ?.takeIf { it.live }
                ?.let { DiscordPostSchedule.due(it.startTime, it.endTime, clock.instant()) }
        val payload = DiscordPostJobs.EventPostPayload(eventId)
        if (out(eventId, DiscordArtefact.INFO_POST) || due?.withinTwoWeeks == true) {
            jobs.runAsync(DiscordPostJobs.Announcement, payload, trigger, actor)
        }
        if (out(eventId, DiscordArtefact.CALENDAR_POST) || due?.calendarPost == true) {
            jobs.runAsync(DiscordPostJobs.CalendarPost, payload, trigger, actor)
        }
        if (out(eventId, DiscordArtefact.DISCORD_EVENT) || due?.withinTwoWeeks == true) {
            jobs.runAsync(DiscordPostJobs.DiscordEvent, payload, trigger, actor)
        }
    }

    private fun out(
        eventId: Long,
        artefact: DiscordArtefact,
    ) = ledger.find(eventId, artefact) != null

    private companion object {
        // An events-calendar post comes down the morning after the event ends.
        val MORNING_BEHIND: Duration = Duration.ofDays(2)

        // The events-info post goes out a fortnight ahead of the event's day.
        val MORNING_AHEAD: Duration = Duration.ofDays(15)

        val HOURLY_BEHIND: Duration = Duration.ofHours(2)
    }
}
