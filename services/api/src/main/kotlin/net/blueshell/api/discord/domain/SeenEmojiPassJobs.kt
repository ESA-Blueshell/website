package net.blueshell.api.discord.domain

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTally
import net.blueshell.api.shared.job.JobTrigger
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

object DiscordJobs {
    /** Starts a pass over the server's history for seen emoji, started by hand. Safe to run again: a message counts once. */
    object StartSeenEmojiPass : JobDefinition<SeenEmojiPassPayload> {
        override val type: String = "discord.start-seen-emoji-pass"
        override val payloadType: Class<SeenEmojiPassPayload> = SeenEmojiPassPayload::class.java
    }

    /** Reads the next bounded part of the pass, each read queueing the next until every place is read. */
    object ReadSeenEmojiPass : JobDefinition<SeenEmojiPassPayload> {
        override val type: String = "discord.read-seen-emoji-pass"
        override val payloadType: Class<SeenEmojiPassPayload> = SeenEmojiPassPayload::class.java

        // The next read is queued by the running one.
        override val queuesBehindRunning: Boolean get() = true
    }

    data class SeenEmojiPassPayload(
        val unused: Unit = Unit,
    )
}

/** Marks every place the bot can see unread from its newest message, and queues the first read. */
@Component
class StartSeenEmojiPassJob(
    objectMapper: ObjectMapper,
    private val backfill: SeenEmojiBackfill,
    private val jobs: JobQueue,
) : AbstractJsonJobHandler<DiscordJobs.SeenEmojiPassPayload>(objectMapper, DiscordJobs.StartSeenEmojiPass) {
    override fun handlePayload(payload: DiscordJobs.SeenEmojiPassPayload) {
        backfill.whyNot()?.let { return skip(it) }
        counted(JobTally.PLACES_LEFT, backfill.startPass().toLong())
        jobs.runAsync(DiscordJobs.ReadSeenEmojiPass, DiscordJobs.SeenEmojiPassPayload(), JobTrigger.ANOTHER_JOB)
    }
}

/** Reads a bounded part of the pass, reports it, and queues the next part while places are left. */
@Component
class ReadSeenEmojiPassJob(
    objectMapper: ObjectMapper,
    private val backfill: SeenEmojiBackfill,
    private val jobs: JobQueue,
) : AbstractJsonJobHandler<DiscordJobs.SeenEmojiPassPayload>(objectMapper, DiscordJobs.ReadSeenEmojiPass) {
    override fun handlePayload(payload: DiscordJobs.SeenEmojiPassPayload) {
        backfill.whyNot()?.let { return skip(it) }
        val slice = backfill.readSome(PAGES_PER_RUN)
        counted(JobTally.PLACES_READ, slice.places.size.toLong(), slice.places)
        counted(JobTally.MESSAGES_READ, slice.messages)
        counted(JobTally.EMOJI_SEEN, slice.sightings)
        counted(JobTally.PLACES_LEFT, slice.left)
        if (slice.left > 0) jobs.runAsync(DiscordJobs.ReadSeenEmojiPass, DiscordJobs.SeenEmojiPassPayload(), JobTrigger.ANOTHER_JOB)
    }

    internal companion object {
        // Short enough that a run finishes well inside the stale-job threshold, rate limits included.
        const val PAGES_PER_RUN = 20
    }
}
