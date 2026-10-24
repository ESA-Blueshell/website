package net.blueshell.api.discord.domain

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

object DiscordJobs {
    /**
     * Reads the server's history for seen emoji, a bounded slice per run, each run queueing the
     * next until every place is read. Safe to run again: places already read stay read, and a
     * message counts once.
     */
    object BackfillSeenEmoji : JobDefinition<BackfillSeenEmojiPayload> {
        override val type: String = "discord.backfill-seen-emoji"
        override val payloadType: Class<BackfillSeenEmojiPayload> = BackfillSeenEmojiPayload::class.java

        // The next slice is queued by the running one.
        override val queuesBehindRunning: Boolean get() = true
    }

    /** [discover] looks for channels, threads and forum posts first; a run the job queued itself skips it. */
    data class BackfillSeenEmojiPayload(
        val discover: Boolean = true,
    )
}

@Component
class BackfillSeenEmojiJob(
    objectMapper: ObjectMapper,
    private val backfill: SeenEmojiBackfill,
    private val jobs: JobQueue,
) : AbstractJsonJobHandler<DiscordJobs.BackfillSeenEmojiPayload>(objectMapper, DiscordJobs.BackfillSeenEmoji) {
    override fun handlePayload(payload: DiscordJobs.BackfillSeenEmojiPayload) {
        if (payload.discover && !backfill.discover()) return skip("The Discord bot is not set up")
        if (backfill.readSome(PAGES_PER_RUN)) {
            jobs.runAsync(DiscordJobs.BackfillSeenEmoji, DiscordJobs.BackfillSeenEmojiPayload(discover = false), JobTrigger.ANOTHER_JOB)
        }
    }

    internal companion object {
        // Short enough that a run finishes well inside the stale-job threshold, rate limits included.
        const val PAGES_PER_RUN = 20
    }
}
