package net.blueshell.api.infrastructure.security

import org.springframework.stereotype.Component
import java.time.Duration
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/**
 * Lightweight in-memory sliding-window limiter for low-volume abuse protection.
 *
 * Note: process-local only. For horizontal scaling, replace with a shared store.
 */
@Component
class InMemoryRequestRateLimiter {

    data class Decision(
        val allowed: Boolean,
        val retryAfterSeconds: Long = 0
    )

    private data class Bucket(
        val timestamps: ArrayDeque<Long> = ArrayDeque()
    )

    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun tryAcquire(key: String, maxRequests: Int, window: Duration): Decision {
        require(maxRequests > 0) { "maxRequests must be positive" }
        val windowMillis = window.toMillis()
        require(windowMillis > 0) { "window must be positive" }

        val now = System.currentTimeMillis()
        val bucket = buckets.computeIfAbsent(key) { Bucket() }

        synchronized(bucket) {
            while (bucket.timestamps.isNotEmpty() && now - bucket.timestamps.first() >= windowMillis) {
                bucket.timestamps.removeFirst()
            }

            if (bucket.timestamps.size >= maxRequests) {
                val oldest = bucket.timestamps.first()
                val retryAfterMillis = (windowMillis - (now - oldest)).coerceAtLeast(1)
                val retryAfterSeconds = ceil(retryAfterMillis / 1000.0).toLong()
                return Decision(allowed = false, retryAfterSeconds = retryAfterSeconds)
            }

            bucket.timestamps.addLast(now)
            return Decision(allowed = true)
        }
    }
}
