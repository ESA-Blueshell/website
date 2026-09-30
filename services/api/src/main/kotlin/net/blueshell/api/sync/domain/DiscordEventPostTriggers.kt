package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventChanged
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.event.api.EventSignUpsChanged
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Clock
import java.time.Duration

/**
 * What queues the bot's jobs for an event: every change to it, which queues all three in the
 * change's own transaction so each job says what it did or why it did nothing; and the morning run
 * at 08:00 for every approved event not over for long, which lists the Discord event of one however
 * far ahead. The morning run queues only a job with something to do: one for what is already out,
 * or for what is due.
 */
@Component
class DiscordEventPostTriggers(
    private val jobs: JobQueue,
    private val events: EventPosts,
    private val ledger: PostLedger,
) {
    // Settable for tests only.
    internal var clock: Clock = Clock.systemUTC()

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    fun on(event: EventChanged) {
        val payload = DiscordPostJobs.EventPostPayload(event.eventId)
        val trigger = event.changeType.asTrigger()
        ALL.forEach { jobs.runAsync(it, payload, trigger, event.actor) }
    }

    // Only the posts show the count.
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    fun on(signUps: EventSignUpsChanged) {
        val payload = DiscordPostJobs.EventPostPayload(signUps.eventId)
        POSTS.forEach { jobs.runAsync(it, payload, JobTrigger.SIGN_UPS_CHANGED, signUps.actor) }
    }

    @Scheduled(cron = "0 0 8 * * *", zone = DiscordPostSchedule.ZONE_ID)
    fun morning() {
        runMorning()
    }

    /** The morning run; answers how many events it looked at. */
    fun runMorning(): Int {
        val now = clock.instant()
        val kept = events.keptEndingFrom(now.minus(MORNING_BEHIND))
        kept.forEach { queue(it, JobTrigger.MORNING_RUN) }
        return kept.size
    }

    private fun queue(
        eventId: Long,
        trigger: JobTrigger,
    ) {
        val event = events.of(eventId)
        val due = event?.takeIf { it.live }?.let { DiscordPostSchedule.due(it.startTime, it.endTime, clock.instant()) }
        val payload = DiscordPostJobs.EventPostPayload(eventId)
        if (out(eventId, DiscordArtefact.INFO_POST) || due?.withinTwoWeeks == true) {
            jobs.runAsync(DiscordPostJobs.Announcement, payload, trigger)
        }
        if (out(eventId, DiscordArtefact.CALENDAR_POST) || due?.calendarPost == true) {
            jobs.runAsync(DiscordPostJobs.CalendarPost, payload, trigger)
        }
        // Once the event is over, Discord ends its Discord event and the bot leaves it be.
        val endsLater = event == null || clock.instant().isBefore(event.endTime)
        if (endsLater && (due != null || out(eventId, DiscordArtefact.DISCORD_EVENT))) {
            jobs.runAsync(DiscordPostJobs.DiscordEvent, payload, trigger)
        }
    }

    private fun out(
        eventId: Long,
        artefact: DiscordArtefact,
    ) = ledger.find(eventId, artefact) != null

    private companion object {
        val POSTS = listOf(DiscordPostJobs.Announcement, DiscordPostJobs.CalendarPost)
        val ALL = POSTS + DiscordPostJobs.DiscordEvent

        // An events-calendar post comes down the morning after the event ends.
        val MORNING_BEHIND: Duration = Duration.ofDays(2)
    }
}
