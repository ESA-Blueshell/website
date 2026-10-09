package net.blueshell.api.pinger.web

import net.blueshell.api.pinger.api.PaintView
import net.blueshell.api.pinger.api.PingerPaintService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The live push behind the paint job: every open stream is sent the whole paint job on connect and
 * again whenever it changes, so the pingers and the site follow an admin's edits as they land.
 *
 * An edit on this replica pushes at once; a once-a-second re-read catches an edit made on another
 * replica, so no cross-pod channel is needed. The stream is anonymous, so the slots are capped in
 * total and per client address, each reserved before its emitter exists and released exactly once
 * however the stream ends; a comment every 15 s keeps proxies from cutting an idle stream and
 * releases a dead one on the failed send.
 */
@Component
class PaintStream(
    private val paint: PingerPaintService,
    private val json: JsonMapper,
    @param:Value($$"${pinger.paint.stream-timeout-ms:0}") private val timeoutMs: Long,
    @param:Value($$"${pinger.paint.max-streams:2000}") private val maxStreams: Int,
    @param:Value($$"${pinger.paint.max-streams-per-client:64}") private val maxPerClient: Int,
) {
    private val subscribers = ConcurrentHashMap.newKeySet<Subscriber>()
    private val open = AtomicInteger()
    private val perClient = ConcurrentHashMap<String, Int>()
    private val lock = Any()

    // Compared with serverTime blanked: the clock alone changing is not a change.
    private var lastSent: PaintView? = null

    /** Opens a stream for [client], primed with the current paint job so a late joiner paints at once. */
    fun open(client: String): SseEmitter {
        reserve(client)
        val subscriber = Subscriber(newEmitter(), client)
        subscribers.add(subscriber)
        subscriber.emitter.onCompletion { subscriber.release() }
        subscriber.emitter.onTimeout { subscriber.release() }
        subscriber.emitter.onError { subscriber.release() }
        // A stream that fails before it is handed back is never completed by the framework.
        try {
            subscriber.push(serialise(paint.current()))
        } catch (ex: RuntimeException) {
            subscriber.release()
            throw ex
        }
        return subscriber.emitter
    }

    /** Pushes the paint job to every stream if it changed since the last push. Call after a write commits. */
    @Scheduled(fixedDelayString = $$"${pinger.paint.push-interval-ms:1000}")
    fun refresh() {
        if (subscribers.isEmpty()) return
        synchronized(lock) {
            val current = paint.current()
            val job = current.copy(serverTime = Instant.EPOCH)
            if (job == lastSent) return
            lastSent = job
            val payload = serialise(current)
            subscribers.forEach { it.push(payload) }
        }
    }

    @Scheduled(fixedDelayString = $$"${pinger.paint.heartbeat-interval-ms:15000}")
    fun heartbeat() {
        subscribers.forEach { it.heartbeat() }
    }

    internal fun newEmitter(): SseEmitter = SseEmitter(timeoutMs)

    // Once per change, not per subscriber: every stream is sent the same string.
    private fun serialise(view: PaintView): String = json.writeValueAsString(PaintResponse.from(view))

    private fun reserve(client: String) {
        while (true) {
            val taken = open.get()
            if (taken >= maxStreams) {
                throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The paint stream is at capacity; retry shortly.")
            }
            if (open.compareAndSet(taken, taken + 1)) break
        }
        var refused = false
        perClient.compute(client) { _, held ->
            val count = held ?: 0
            if (count >= maxPerClient) {
                refused = true
                held
            } else {
                count + 1
            }
        }
        if (refused) {
            open.decrementAndGet()
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many paint streams from one address.")
        }
    }

    private fun unreserve(client: String) {
        open.decrementAndGet()
        perClient.computeIfPresent(client) { _, held -> if (held <= 1) null else held - 1 }
    }

    private inner class Subscriber(
        val emitter: SseEmitter,
        private val client: String,
    ) {
        private val released = AtomicBoolean(false)

        fun push(payload: String) = deliver { emitter.send(SseEmitter.event().data(payload, MediaType.TEXT_PLAIN)) }

        fun heartbeat() = deliver { emitter.send(SseEmitter.event().comment("")) }

        fun release() {
            if (!released.compareAndSet(false, true)) return
            subscribers.remove(this)
            unreserve(client)
        }

        private fun deliver(write: () -> Unit) {
            try {
                write()
            } catch (ex: IOException) {
                emitter.completeWithError(ex)
                release()
            } catch (_: IllegalStateException) {
                // Sending on an emitter that has already completed.
                release()
            }
        }
    }
}
