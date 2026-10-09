package net.blueshell.api.pinger.web

import net.blueshell.api.pinger.api.PingerShareService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * The live push of each pinger's paint share: a stream is primed with the device's slice, then sent
 * it again whenever it moves, so devices re-split the moment one joins, leaves or changes rate.
 *
 * One scheduled loop recomputes every subscriber's share, and the share service reads the live set
 * once per second for all of them. A comment heartbeat keeps idle proxies from cutting the stream and
 * finds dead ones; an emitter is dropped the moment it completes, times out, errors or fails a send.
 */
@Component
class PingerShareStream(
    private val shares: PingerShareService,
    // 0 is no timeout: a pinger holds its stream for the whole event, and the heartbeat's failed
    // send is what finds and drops a dead one.
    @param:Value($$"${pinger.share.stream-timeout-ms:0}") private val timeoutMs: Long,
) {
    private class Subscriber(
        val identity: String,
        val deviceId: String,
    ) {
        @Volatile
        var last: PingerShareResponse? = null
    }

    private val subscribers = ConcurrentHashMap<SseEmitter, Subscriber>()

    fun open(
        identity: String,
        deviceId: String,
    ): SseEmitter {
        if (subscribers.size >= MAX_EMITTERS) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The share stream is at capacity; retry shortly.")
        }
        val emitter = newEmitter()
        val subscriber = Subscriber(identity, deviceId)
        subscribers[emitter] = subscriber
        emitter.onCompletion { subscribers.remove(emitter) }
        emitter.onTimeout { subscribers.remove(emitter) }
        emitter.onError { subscribers.remove(emitter) }
        push(emitter, subscriber)
        return emitter
    }

    @Scheduled(fixedDelayString = $$"${pinger.share.push-interval-ms:1000}")
    fun tick() {
        subscribers.forEach { (emitter, subscriber) -> push(emitter, subscriber) }
    }

    @Scheduled(fixedDelayString = $$"${pinger.share.heartbeat-ms:15000}")
    fun heartbeat() {
        subscribers.keys.forEach { emitter -> send(emitter) { it.send(SseEmitter.event().comment("heartbeat")) } }
    }

    internal fun newEmitter(): SseEmitter = SseEmitter(timeoutMs)

    private fun push(
        emitter: SseEmitter,
        subscriber: Subscriber,
    ) {
        val current = PingerShareResponse.of(shares.share(subscriber.identity, subscriber.deviceId))
        val last = subscriber.last
        if (last != null && !moved(last, current)) return
        subscriber.last = current
        send(emitter) { it.send(current) }
    }

    private fun moved(
        last: PingerShareResponse,
        current: PingerShareResponse,
    ) = abs(last.from - current.from) > TOLERANCE || abs(last.to - current.to) > TOLERANCE || last.devices != current.devices

    private fun send(
        emitter: SseEmitter,
        write: (SseEmitter) -> Unit,
    ) {
        try {
            write(emitter)
        } catch (ex: IOException) {
            emitter.completeWithError(ex)
            subscribers.remove(emitter)
        }
    }

    private companion object {
        // Every member's devices plus SiteCie's replicas, with room to spare; a flood past it is refused.
        const val MAX_EMITTERS = 2_000

        // Float noise from a re-sum must not wake every client.
        const val TOLERANCE = 1e-6
    }
}
