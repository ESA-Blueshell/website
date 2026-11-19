package net.blueshell.api.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

private const val BUCKET_IDLE_MINUTES = 30L
private const val MILLIS_PER_SECOND = 1000.0

/**
 * Lightweight in-memory sliding-window limiter for low-volume abuse protection.
 *
 * Note: process-local only. For horizontal scaling, replace with a shared store.
 */
@Component
class InMemoryRequestRateLimiter(
    @param:Value("\${security.auth-rate-limit.max-buckets:10000}")
    private val maxBuckets: Int = 10_000,
    @param:Value("\${security.auth-rate-limit.bucket-idle-ttl:PT30M}")
    private val bucketIdleTtl: Duration = Duration.ofMinutes(BUCKET_IDLE_MINUTES),
    @param:Value("\${security.auth-rate-limit.cleanup-interval:128}")
    private val cleanupInterval: Int = 128,
    // No bean has this type, so Spring leaves it at wall time; a caller with its own clock passes it.
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class Decision(
        val allowed: Boolean,
        val retryAfterSeconds: Long = 0,
    )

    private data class Bucket(
        val timestamps: ArrayDeque<Long> = ArrayDeque(),
        @Volatile var lastSeenAt: Long = 0,
    )

    private val buckets = ConcurrentHashMap<String, Bucket>()
    private val capacityLock = Any()
    private val attemptCounter = AtomicLong(0)

    init {
        require(maxBuckets > 0) { "maxBuckets must be positive" }
        require(bucketIdleTtl.toMillis() > 0) { "bucketIdleTtl must be positive" }
        require(cleanupInterval > 0) { "cleanupInterval must be positive" }
    }

    fun tryAcquire(
        key: String,
        maxRequests: Int,
        window: Duration,
    ): Decision {
        val retryAfterMillis = acquire(key, maxRequests, window)
        if (retryAfterMillis == 0L) return Decision(allowed = true)
        return Decision(allowed = false, retryAfterSeconds = ceil(retryAfterMillis / MILLIS_PER_SECOND).toLong())
    }

    /**
     * Takes a slot under [key], pausing until one frees up; false where that wait would outlast
     * [patience], with nothing taken.
     */
    fun awaitTurn(
        key: String,
        maxRequests: Int,
        window: Duration,
        patience: Duration,
        pause: (Duration) -> Unit,
    ): Boolean {
        val giveUp = now() + patience.toMillis()
        while (true) {
            val wait = acquire(key, maxRequests, window)
            if (wait == 0L) return true
            if (now() + wait >= giveUp) return false
            pause(Duration.ofMillis(wait))
        }
    }

    // A slot taken is 0; otherwise the milliseconds until the oldest one in the window frees up.
    private fun acquire(
        key: String,
        maxRequests: Int,
        window: Duration,
    ): Long {
        require(maxRequests > 0) { "maxRequests must be positive" }
        val windowMillis = window.toMillis()
        require(windowMillis > 0) { "window must be positive" }

        val now = now()
        maybeCleanup(now)
        val bucket = getOrCreateBucket(key, now)

        synchronized(bucket) {
            bucket.lastSeenAt = now
            pruneExpiredTimestamps(bucket, now, windowMillis)

            if (bucket.timestamps.size >= maxRequests) {
                return (windowMillis - (now - bucket.timestamps.first())).coerceAtLeast(1)
            }

            bucket.timestamps.addLast(now)
            return 0
        }
    }

    internal fun trackedBucketCount(): Int = buckets.size

    private fun getOrCreateBucket(
        key: String,
        now: Long,
    ): Bucket {
        buckets[key]?.let { return it }

        synchronized(capacityLock) {
            buckets[key]?.let { return it }
            enforceCapacity(now, reserve = 1)
            return buckets.computeIfAbsent(key) { Bucket(lastSeenAt = now) }
        }
    }

    private fun maybeCleanup(now: Long) {
        if (attemptCounter.incrementAndGet() % cleanupInterval.toLong() != 0L) {
            return
        }
        synchronized(capacityLock) {
            enforceCapacity(now, reserve = 0)
        }
    }

    private fun enforceCapacity(
        now: Long,
        reserve: Int,
    ) {
        evictStaleBuckets(now)

        val overflow = buckets.size + reserve - maxBuckets
        if (overflow <= 0) {
            return
        }

        buckets.entries
            .asSequence()
            .map { it.key to it.value }
            .sortedBy { (_, bucket) -> bucket.lastSeenAt }
            .take(overflow)
            .forEach { (key, bucket) -> buckets.remove(key, bucket) }
    }

    private fun evictStaleBuckets(now: Long) {
        val idleMillis = bucketIdleTtl.toMillis()
        buckets.forEach { (key, bucket) ->
            if (now - bucket.lastSeenAt < idleMillis) {
                return@forEach
            }
            synchronized(bucket) {
                if (now - bucket.lastSeenAt < idleMillis) {
                    return@synchronized
                }
                pruneExpiredTimestamps(bucket, now, idleMillis)
                if (bucket.timestamps.isEmpty()) {
                    buckets.remove(key, bucket)
                }
            }
        }
    }

    private fun pruneExpiredTimestamps(
        bucket: Bucket,
        now: Long,
        windowMillis: Long,
    ) {
        while (bucket.timestamps.isNotEmpty() && now - bucket.timestamps.first() >= windowMillis) {
            bucket.timestamps.removeFirst()
        }
    }
}
