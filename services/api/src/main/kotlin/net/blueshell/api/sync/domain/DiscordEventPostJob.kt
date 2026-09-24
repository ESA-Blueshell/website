package net.blueshell.api.sync.domain

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.jobs.api.RetrySchedule
import net.blueshell.api.shared.job.DiscordPostJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration

/** One event's reconcile, queued so a Discord refusal is retried with the queue's backoff. */
@Component
class DiscordEventPostJob(
    objectMapper: ObjectMapper,
    private val posts: DiscordEventPosts,
) : AbstractJsonJobHandler<DiscordPostJobs.ReconcilePayload>(
        objectMapper,
        DiscordPostJobs.Reconcile.payloadType,
    ) {
    override val jobType: String = DiscordPostJobs.Reconcile.type

    /* A getter over a constant, not a field: the handler is proxied, and a proxy's own fields are empty. */
    override val retrySchedule: RetrySchedule
        get() = THROUGH_THE_DAY

    /* Settable so a test can fix the moment; nothing else changes it. */
    internal var clock: Clock = Clock.systemUTC()

    override fun handlePayload(payload: DiscordPostJobs.ReconcilePayload) {
        posts.reconcile(payload.eventId, payload.trigger, payload.at ?: clock.instant())
    }

    private companion object {
        /* From two minutes, doubling to two hours: about ten hours of trying before it gives up. */
        val THROUGH_THE_DAY = RetrySchedule(10, Duration.ofMinutes(2), 2.0, Duration.ofHours(2))
    }
}
