package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobEffect
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTally
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.text.Shortcodes
import net.blueshell.api.shared.text.ShortcodesResolved
import net.blueshell.api.sync.api.DiscordPublisher
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

object EventTextJobs {
    /** Writes the shortcodes left in one event's description as the emoji they name. */
    object ResolveEmojiShortcodes : JobDefinition<EventShortcodesPayload> {
        override val type: String = "event.resolve-emoji-shortcodes"
        override val payloadType: Class<EventShortcodesPayload> = EventShortcodesPayload::class.java
    }

    /** Queues [ResolveEmojiShortcodes] for every event whose description has a shortcode to write. */
    object ResolveAllEmojiShortcodes : JobDefinition<AllEventsPayload> {
        override val type: String = "event.resolve-all-emoji-shortcodes"
        override val payloadType: Class<AllEventsPayload> = AllEventsPayload::class.java
    }

    data class EventShortcodesPayload(
        val eventId: Long,
    )

    data class AllEventsPayload(
        val unused: Unit = Unit,
    )
}

/**
 * The `:name:` shortcodes in an event's description, written as the emoji they name: the server's
 * own, then the application's, then the standard ones, a name matching in any case once no emoji
 * has it in exactly that case.
 */
@Service
class EventShortcodes(
    private val events: EventService,
    private val publisher: ObjectProvider<DiscordPublisher>,
) {
    private val standard by lazy { Shortcodes.names() }

    /** What each name stands for, as the server has its emoji now; null where there is no bot to ask. */
    fun lookup(): ((String) -> String?)? {
        val custom = publisher.ifAvailable?.customEmoji() ?: return null
        // Reversed, so the first emoji with a name is the one a map keeps.
        val exact = custom.asReversed().associate { it.name to it.written }
        val anyCase = custom.asReversed().associate { it.name.lowercase() to it.written }
        return { name -> exact[name] ?: anyCase[name.lowercase()] ?: standard[name.lowercase()] }
    }

    /** The live events whose description has a shortcode [emojiOf] answers, past and upcoming alike. */
    fun eventsToResolve(emojiOf: (String) -> String?): List<Long> =
        events
            .descriptionsWithColons()
            .filterValues { Shortcodes.resolve(it, emojiOf).resolved.isNotEmpty() }
            .keys
            .sorted()

    /** Rewrites the event's description where a shortcode names an emoji; null where the event is gone. */
    fun resolve(
        eventId: Long,
        emojiOf: (String) -> String?,
    ): ShortcodesResolved? {
        val event = events.findByIdIncludingDeletedOrNull(eventId)?.takeUnless { it.isSoftDeleted } ?: return null
        val resolved = Shortcodes.resolve(event.description.orEmpty(), emojiOf)
        if (resolved.resolved.isNotEmpty()) {
            event.description = resolved.text
            events.update(event)
        }
        return resolved
    }
}

@Component
class ResolveEmojiShortcodesJob(
    objectMapper: ObjectMapper,
    private val shortcodes: EventShortcodes,
) : AbstractJsonJobHandler<EventTextJobs.EventShortcodesPayload>(objectMapper, EventTextJobs.ResolveEmojiShortcodes) {
    override fun handlePayload(payload: EventTextJobs.EventShortcodesPayload) {
        val emojiOf = shortcodes.lookup() ?: return skip("The Discord bot is not set up")
        val resolved = shortcodes.resolve(payload.eventId, emojiOf) ?: return skip("The event is gone")
        counted(JobTally.SHORTCODES_RESOLVED, resolved.resolved.size.toLong(), names = resolved.resolved.distinct())
        counted(JobTally.SHORTCODES_LEFT, resolved.left.size.toLong(), names = resolved.left.distinct())
        did(if (resolved.resolved.isEmpty()) JobEffect.UNCHANGED else JobEffect.EDITED, null)
    }
}

@Component
class ResolveAllEmojiShortcodesJob(
    objectMapper: ObjectMapper,
    private val shortcodes: EventShortcodes,
    private val jobs: JobQueue,
) : AbstractJsonJobHandler<EventTextJobs.AllEventsPayload>(objectMapper, EventTextJobs.ResolveAllEmojiShortcodes) {
    override fun handlePayload(payload: EventTextJobs.AllEventsPayload) {
        val emojiOf = shortcodes.lookup() ?: return skip("The Discord bot is not set up")
        val queued = shortcodes.eventsToResolve(emojiOf)
        queued.forEach {
            jobs.runAsync(EventTextJobs.ResolveEmojiShortcodes, EventTextJobs.EventShortcodesPayload(it), JobTrigger.ANOTHER_JOB)
        }
        counted(JobTally.EVENTS_QUEUED, queued.size.toLong())
    }
}
