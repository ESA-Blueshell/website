package net.blueshell.api.discord.domain

import net.blueshell.api.discord.persistence.SeenEmojiRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor.DiscardPolicy
import java.util.concurrent.TimeUnit

/** A server emoji used in one message of the association's server, in its text or as a reaction. */
data class EmojiSighting(
    val emojiId: String,
    val name: String,
    val animated: Boolean,
    val messageId: String,
    val at: Instant,
)

/** Where the bot sees emoji used: the gateway, live. */
fun interface EmojiSightings {
    /** Calls [listener] for every server emoji in a new or edited message, or added as a reaction. */
    fun onEmojiSeen(listener: (EmojiSighting) -> Unit)
}

private val CUSTOM_EMOJI = Regex("""<(a?):(\w+):(\d+)>""")

/** The server emoji a message's text holds, as Discord writes them. */
internal fun emojiIn(
    content: String,
    messageId: String,
    at: Instant,
): List<EmojiSighting> =
    CUSTOM_EMOJI
        .findAll(content)
        .map { it.destructured }
        .map { (animated, name, id) -> EmojiSighting(id, name, animated == "a", messageId, at) }
        .toList()

/**
 * The index of seen emoji: every server emoji the bot sees used in the association's server, fed
 * live from the gateway. A message counts once per emoji, however often it is edited or seen.
 */
@Service
class SeenEmojiIndex(
    private val repository: SeenEmojiRepository,
    private val transactions: TransactionOperations,
    private val sightings: ObjectProvider<EmojiSightings>,
) : SmartLifecycle {
    @Volatile private var running = false

    @Volatile private var writer: ExecutorService? = null

    fun saw(sighting: EmojiSighting) {
        transactions.executeWithoutResult {
            if (repository.recordMessage(sighting.emojiId, sighting.messageId) == 1) {
                repository.countUse(sighting.emojiId, sighting.name, sighting.animated, sighting.at)
            }
        }
    }

    /** Every seen emoji, the most used first. */
    fun seen(): List<DiscordEmoji> =
        repository.findAllByOrderByUseCountDesc().map {
            DiscordEmoji(
                id = it.id,
                name = it.name,
                animated = it.animated,
                available = true,
                roleIds = emptyList(),
                kind = DiscordEmojiKind.SEEN,
                uses = it.useCount,
            )
        }

    /*
     * Written on a thread of its own: the gateway calls back on the thread that reads it, which a
     * slow database would hold past its heartbeat. Past QUEUED_MAX waiting, or once stopped, a
     * sighting is dropped; the backfill counts it on its next pass.
     */
    override fun start() {
        running = true
        val source = sightings.ifAvailable ?: return
        val queue = LinkedBlockingQueue<Runnable>(QUEUED_MAX)
        val thread = ThreadFactory { Thread(it, "discord-emoji").apply { isDaemon = true } }
        val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, queue, thread, DiscardPolicy())
        writer = worker
        source.onEmojiSeen { sighting ->
            worker.execute { runCatching { saw(sighting) }.onFailure { log.warn("A seen emoji could not be counted", it) } }
        }
    }

    // What is waiting is written first, for a while.
    override fun stop() {
        running = false
        writer?.run {
            shutdown()
            if (!awaitTermination(DRAIN_SECONDS, TimeUnit.SECONDS)) shutdownNow()
        }
        writer = null
    }

    override fun isRunning(): Boolean = running

    private companion object {
        const val QUEUED_MAX = 10_000
        const val DRAIN_SECONDS = 5L
        val log = LoggerFactory.getLogger(SeenEmojiIndex::class.java)
    }
}
