package net.blueshell.api.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

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
    private val bucketIdleTtl: Duration = Duration.ofMinutes(30),
    @param:Value("\${security.auth-rate-limit.cleanup-interval:128}")
    private val cleanupInterval: Int = 128,
) {

    data class Decision(
        val allowed: Boolean,
        val retryAfterSeconds: Long = 0
    )

    private data class Bucket(
        val timestamps: ArrayDeque<Long> = ArrayDeque(),
        @Volatile var lastSeenAt: Long = 0
    )

    private val buckets = ConcurrentHashMap<String, Bucket>()
    private val capacityLock = Any()
    private val attemptCounter = AtomicLong(0)

    init {
        require(maxBuckets > 0) { "maxBuckets must be positive" }
        require(bucketIdleTtl.toMillis() > 0) { "bucketIdleTtl must be positive" }
        require(cleanupInterval > 0) { "cleanupInterval must be positive" }
    }

    fun tryAcquire(key: String, maxRequests: Int, window: Duration): Decision {
        require(maxRequests > 0) { "maxRequests must be positive" }
        val windowMillis = window.toMillis()
        require(windowMillis > 0) { "window must be positive" }

        val now = System.currentTimeMillis()
        maybeCleanup(now)
        val bucket = getOrCreateBucket(key, now)

        synchronized(bucket) {
            bucket.lastSeenAt = now
            pruneExpiredTimestamps(bucket, now, windowMillis)

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

    internal fun trackedBucketCount(): Int = buckets.size

    private fun getOrCreateBucket(key: String, now: Long): Bucket {
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

    private fun enforceCapacity(now: Long, reserve: Int) {
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

    private fun pruneExpiredTimestamps(bucket: Bucket, now: Long, windowMillis: Long) {
        while (bucket.timestamps.isNotEmpty() && now - bucket.timestamps.first() >= windowMillis) {
            bucket.timestamps.removeFirst()
        }
    }
}
