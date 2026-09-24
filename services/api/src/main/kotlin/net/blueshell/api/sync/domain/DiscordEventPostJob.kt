package net.blueshell.api.sync.domain

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.DiscordPostJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Clock

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

    /* Settable so a test can fix the moment; nothing else changes it. */
    internal var clock: Clock = Clock.systemUTC()

    override fun handlePayload(payload: DiscordPostJobs.ReconcilePayload) {
        posts.reconcile(payload.eventId, Trigger.valueOf(payload.trigger), payload.at ?: clock.instant())
    }
}
