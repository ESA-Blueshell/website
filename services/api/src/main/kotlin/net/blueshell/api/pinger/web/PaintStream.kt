package net.blueshell.api.pinger.web

import net.blueshell.api.pinger.api.PaintView
import net.blueshell.api.pinger.api.PingerPaintService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * The live push behind the paint job: every open stream is sent the whole paint job on connect and
 * again whenever it changes, so the pingers and the site follow an admin's edits as they land.
 *
 * An edit on this replica pushes at once; a once-a-second re-read catches an edit made on another
 * replica, so no cross-pod channel is needed. A comment every 15 s keeps idle proxies from cutting
 * the stream and finds dead clients, whose emitters are dropped on the failed send.
 */
@Component
class PaintStream(
    private val paint: PingerPaintService,
    @param:Value($$"${pinger.paint.stream-timeout-ms:0}") private val timeoutMs: Long,
) {
    private val emitters = ConcurrentHashMap.newKeySet<SseEmitter>()
    private val lock = Any()

    // Compared with serverTime blanked: the clock alone changing is not a change.
    private var lastSent: PaintView? = null

    /** Opens a stream, primed with the current paint job so a late joiner paints at once. */
    fun open(): SseEmitter {
        // Cap concurrent streams so a flood of unauthenticated opens cannot exhaust emitters.
        if (emitters.size >= MAX_EMITTERS) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The paint stream is at capacity; retry shortly.")
        }
        val emitter = newEmitter()
        emitters.add(emitter)
        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }
        emitter.onError { emitters.remove(emitter) }
        send(emitter, PaintResponse.from(paint.current()))
        return emitter
    }

    /** Pushes the paint job to every stream if it changed since the last push. Call after a write commits. */
    @Scheduled(fixedDelayString = $$"${pinger.paint.push-interval-ms:1000}")
    fun refresh() {
        if (emitters.isEmpty()) return
        synchronized(lock) {
            val current = paint.current()
            val job = current.copy(serverTime = Instant.EPOCH)
            if (job == lastSent) return
            lastSent = job
            val payload = PaintResponse.from(current)
            emitters.forEach { send(it, payload) }
        }
    }

    @Scheduled(fixedDelayString = $$"${pinger.paint.heartbeat-interval-ms:15000}")
    fun heartbeat() {
        emitters.forEach { send(it, SseEmitter.event().comment("")) }
    }

    internal fun newEmitter(): SseEmitter = SseEmitter(timeoutMs)

    private fun send(
        emitter: SseEmitter,
        payload: PaintResponse,
    ) = deliver(emitter) { emitter.send(payload) }

    private fun send(
        emitter: SseEmitter,
        event: SseEmitter.SseEventBuilder,
    ) = deliver(emitter) { emitter.send(event) }

    private fun deliver(
        emitter: SseEmitter,
        write: () -> Unit,
    ) {
        try {
            write()
        } catch (ex: IOException) {
            emitter.completeWithError(ex)
            emitters.remove(emitter)
        } catch (ex: IllegalStateException) {
            // Sending on an emitter that has already completed.
            emitters.remove(emitter)
        }
    }

    private companion object {
        const val MAX_EMITTERS = 2000
    }
}
