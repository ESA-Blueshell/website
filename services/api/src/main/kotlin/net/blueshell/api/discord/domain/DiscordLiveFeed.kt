package net.blueshell.api.discord.domain

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The server as the band draws it, handed to every subscriber whenever it changes.
 *
 * The gateway says something changed many times a second, mostly things the band does not show,
 * so a change is only looked at after [SETTLE], and subscribers hear only a server that differs
 * from the last one. A tick every [TICK] catches what the gateway does not announce: the counts
 * read over REST where the application lacks the intents to follow them.
 */
@Service
class DiscordLiveFeed(
    private val live: DiscordLiveService,
    private val voice: ObjectProvider<VoiceServerSource>,
) : SmartLifecycle {
    private val subscribers = CopyOnWriteArraySet<(DiscordLive?) -> Unit>()
    private val pending = AtomicBoolean(false)

    @Volatile private var last: DiscordLive? = null

    @Volatile private var executor: ScheduledExecutorService? = null

    /** The server as last seen, or read now where nothing was seen yet. */
    fun current(): DiscordLive? = last ?: live.live()

    /** Hands [subscriber] every changed server, null when the bot drops out; the returned call stops it. */
    fun subscribe(subscriber: (DiscordLive?) -> Unit): () -> Unit {
        subscribers += subscriber
        return { subscribers -= subscriber }
    }

    internal fun changed() {
        val running = executor ?: return
        if (pending.compareAndSet(false, true)) {
            running.schedule({
                pending.set(false)
                refresh()
            }, SETTLE.toMillis(), TimeUnit.MILLISECONDS)
        }
    }

    internal fun refresh() {
        val now = runCatching { live.live() }.onFailure { log.warn("Discord server could not be read", it) }.getOrNull()
        if (now == last) return
        last = now
        subscribers.forEach { subscriber ->
            runCatching { subscriber(now) }.onFailure { log.debug("A Discord subscriber refused an update", it) }
        }
    }

    override fun start() {
        val source = voice.ifAvailable ?: return
        executor =
            Executors.newSingleThreadScheduledExecutor { Thread(it, "discord-live").apply { isDaemon = true } }.also {
                it.scheduleWithFixedDelay(::refresh, TICK.toMillis(), TICK.toMillis(), TimeUnit.MILLISECONDS)
            }
        source.onChange(::changed)
    }

    override fun stop() {
        executor?.shutdownNow()
        executor = null
    }

    override fun isRunning(): Boolean = executor != null

    internal companion object {
        val SETTLE: Duration = Duration.ofMillis(500)
        val TICK: Duration = Duration.ofMinutes(1)
        private val log = LoggerFactory.getLogger(DiscordLiveFeed::class.java)
    }
}
