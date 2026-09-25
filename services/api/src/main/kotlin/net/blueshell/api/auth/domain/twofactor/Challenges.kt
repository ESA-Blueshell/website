package net.blueshell.api.auth.domain.twofactor

import net.blueshell.api.security.Browser
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** A password that was right, waiting for its code. Grants nothing but answering itself (api ADR-031). */
data class Challenge(
    val id: String,
    val userId: Long,
    val browser: Browser,
    val openedAt: Instant,
    val wrongCodes: Int,
)

/**
 * Challenges and the per-account count of wrong codes, in Valkey so every replica sees the same.
 * Each rule keeps its own instant and reads the injected clock; Valkey's expiry only tidies up.
 */
@Component
class Challenges(
    private val redis: StringRedisTemplate,
    private val clock: Clock,
) {
    fun open(
        userId: Long,
        browser: Browser,
    ): Challenge {
        val challenge = Challenge(UUID.randomUUID().toString(), userId, browser, clock.instant(), 0)
        redis.opsForHash<String, String>().putAll(
            key(challenge.id),
            mapOf(
                "userId" to userId.toString(),
                "family" to browser.family,
                "platform" to browser.platform,
                "openedAt" to challenge.openedAt.toEpochMilli().toString(),
                "wrongCodes" to "0",
            ),
        )
        redis.expire(key(challenge.id), LIFETIME.multipliedBy(2))
        return challenge
    }

    /** The live challenge [id] names, or null once it is used up, expired or never was. */
    fun find(id: String?): Challenge? {
        if (id.isNullOrBlank()) return null
        val fields = redis.opsForHash<String, String>().entries(key(id))
        val challenge =
            Challenge(
                id = id,
                userId = fields["userId"]?.toLongOrNull() ?: return null,
                browser = Browser(fields["family"].orEmpty(), fields["platform"].orEmpty()),
                openedAt = fields["openedAt"]?.toLongOrNull()?.let(Instant::ofEpochMilli) ?: return null,
                wrongCodes = fields["wrongCodes"]?.toIntOrNull() ?: return null,
            )
        return challenge.takeIf { clock.instant().isBefore(it.openedAt.plus(LIFETIME)) && it.wrongCodes < TRIES }
    }

    /** Counts a wrong code against the challenge, and answers how many tries it has left. */
    fun fail(challenge: Challenge): Int {
        val wrong = redis.opsForHash<String, String>().increment(key(challenge.id), "wrongCodes", 1).toInt()
        if (wrong >= TRIES) close(challenge.id)
        return (TRIES - wrong).coerceAtLeast(0)
    }

    fun close(id: String) {
        redis.delete(key(id))
    }

    /** Whether the account has had its ten wrong codes inside the window, so no code is checked. */
    fun isThrottled(userId: Long): Boolean = failuresInWindow(userId) >= ACCOUNT_LIMIT

    /** Counts a wrong code against the account; true when this one reached the limit. */
    fun countFailure(userId: Long): Boolean {
        val key = failuresKey(userId)
        val now = clock.instant()
        val since = redis.opsForHash<String, String>().get(key, "since")?.toLongOrNull()?.let(Instant::ofEpochMilli)
        if (since == null || !now.isBefore(since.plus(ACCOUNT_WINDOW))) {
            redis.delete(key)
            redis.opsForHash<String, String>().put(key, "since", now.toEpochMilli().toString())
            redis.expire(key, ACCOUNT_WINDOW.multipliedBy(2))
        }
        return redis.opsForHash<String, String>().increment(key, "count", 1) == ACCOUNT_LIMIT.toLong()
    }

    private fun failuresInWindow(userId: Long): Int {
        val fields = redis.opsForHash<String, String>().entries(failuresKey(userId))
        val since = fields["since"]?.toLongOrNull()?.let(Instant::ofEpochMilli) ?: return 0
        if (!clock.instant().isBefore(since.plus(ACCOUNT_WINDOW))) return 0
        return fields["count"]?.toIntOrNull() ?: 0
    }

    companion object {
        val LIFETIME: Duration = Duration.ofMinutes(5)
        const val TRIES = 5
        const val ACCOUNT_LIMIT = 10
        val ACCOUNT_WINDOW: Duration = Duration.ofMinutes(15)

        private fun key(id: String) = "blueshell-api:two-factor-challenge:$id"

        private fun failuresKey(userId: Long) = "blueshell-api:two-factor-failures:$userId"
    }
}
