package net.blueshell.api.infrastructure.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class InMemoryRequestRateLimiterTest {

    private val limiter = InMemoryRequestRateLimiter(cleanupInterval = 1)

    @Test
    fun `allows requests up to limit and blocks next request`() {
        repeat(3) {
            val decision = limiter.tryAcquire("key-1", maxRequests = 3, window = Duration.ofSeconds(5))
            assertThat(decision.allowed).isTrue()
        }

        val blocked = limiter.tryAcquire("key-1", maxRequests = 3, window = Duration.ofSeconds(5))

        assertThat(blocked.allowed).isFalse()
        assertThat(blocked.retryAfterSeconds).isPositive()
    }

    @Test
    fun `rate limits are isolated per key`() {
        repeat(2) {
            assertThat(limiter.tryAcquire("auth|ip-a", 2, Duration.ofSeconds(5)).allowed).isTrue()
        }
        assertThat(limiter.tryAcquire("auth|ip-a", 2, Duration.ofSeconds(5)).allowed).isFalse()

        val otherKey = limiter.tryAcquire("auth|ip-b", 2, Duration.ofSeconds(5))
        assertThat(otherKey.allowed).isTrue()
    }

    @Test
    fun `caps tracked buckets to configured maximum`() {
        val boundedLimiter = InMemoryRequestRateLimiter(
            maxBuckets = 3,
            bucketIdleTtl = Duration.ofHours(1),
            cleanupInterval = 1
        )

        repeat(20) { index ->
            boundedLimiter.tryAcquire("key-$index", maxRequests = 1, window = Duration.ofMinutes(1))
        }

        assertThat(boundedLimiter.trackedBucketCount()).isLessThanOrEqualTo(3)
    }
}
