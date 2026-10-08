package net.blueshell.api.pinger.web

import net.blueshell.api.pinger.api.LeaderboardService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * The live push behind the leaderboard: every open stream is sent the current standings about once
 * a second, and only when they changed, so totals tick and rank swaps show without a reload.
 *
 * An emitter is dropped the moment its request completes, times out or errors, and a failed send
 * ends that stream, so a closed tab leaves nothing behind to push to.
 */
@Component
class LeaderboardStream(
    private val leaderboard: LeaderboardService,
    @param:Value($$"${pinger.leaderboard.stream-timeout-ms:300000}") private val timeoutMs: Long,
) {
    private val emitters = ConcurrentHashMap.newKeySet<SseEmitter>()

    @Volatile
    private var lastSent: LeaderboardResponse? = null

    /** Opens a stream, primed with the current standings so a late joiner sees the board at once. */
    fun open(): SseEmitter {
        // Cap concurrent streams so a flood of unauthenticated opens cannot exhaust emitters.
        if (emitters.size >= MAX_EMITTERS) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The leaderboard stream is at capacity; retry shortly.")
        }
        val emitter = newEmitter()
        emitters.add(emitter)
        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }
        emitter.onError { emitters.remove(emitter) }
        send(emitter, LeaderboardResponse.from(leaderboard.snapshot()))
        return emitter
    }

    @Scheduled(fixedDelayString = $$"${pinger.leaderboard.push-interval-ms:1000}")
    fun tick() {
        if (emitters.isEmpty()) return
        val current = LeaderboardResponse.from(leaderboard.snapshot())
        if (current == lastSent) return
        lastSent = current
        emitters.forEach { send(it, current) }
    }

    internal fun newEmitter(): SseEmitter = SseEmitter(timeoutMs)

    private fun send(
        emitter: SseEmitter,
        payload: LeaderboardResponse,
    ) {
        try {
            emitter.send(payload)
        } catch (ex: IOException) {
            emitter.completeWithError(ex)
            emitters.remove(emitter)
        }
    }

    private companion object {
        const val MAX_EMITTERS = 500
    }
}
