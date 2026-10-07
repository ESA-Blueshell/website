package net.blueshell.api.pinger.persistence

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/** A pinger's current presence, mirrored from its last report. */
data class PingerLive(
    val online: Boolean,
    val pps: Int,
    val lastSeen: Instant,
)

/**
 * The live presence of each reporting pinger, in Valkey so every replica sees the same and a client
 * that stops reporting ages out on its own.
 *
 * This row is a mirror, not a ledger: it is rebuilt from the next report and a wipe loses only
 * presence, never the durable tally. Each write pushes the expiry out, so a row outlives only a
 * brief gap between reports.
 */
@Component
class PingerLiveStore(
    private val redis: StringRedisTemplate,
) {
    fun touch(
        key: String,
        online: Boolean,
        pps: Int,
        at: Instant,
    ) {
        val liveKey = liveKey(key)
        redis.opsForHash<String, String>().putAll(
            liveKey,
            mapOf(
                "online" to online.toString(),
                "pps" to pps.toString(),
                "lastSeen" to at.toEpochMilli().toString(),
            ),
        )
        redis.expire(liveKey, TTL)
    }

    /** The live row for [key], or null once it has aged out or never was. */
    fun find(key: String): PingerLive? {
        val fields = redis.opsForHash<String, String>().entries(liveKey(key))
        val online = fields["online"]?.toBoolean()
        val pps = fields["pps"]?.toIntOrNull()
        val lastSeen = fields["lastSeen"]?.toLongOrNull()?.let(Instant::ofEpochMilli)
        if (online == null || pps == null || lastSeen == null) return null
        return PingerLive(online, pps, lastSeen)
    }

    private fun liveKey(key: String) = "blueshell-api:pinger-live:$key"

    companion object {
        private val TTL: Duration = Duration.ofMinutes(1)
    }
}
