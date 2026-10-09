package net.blueshell.api.pinger.persistence

import org.springframework.data.redis.core.ScanOptions
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

/** One device's row in the live store, kept apart from its identity's other devices. */
data class PingerLiveDevice(
    val identity: String,
    val deviceId: String,
    val live: PingerLive,
)

/**
 * The live presence of each reporting pinger device, in Valkey so every replica sees the same and a
 * device that stops reporting ages out on its own.
 *
 * One row per (identity, device): a member on several devices, or SiteCie across several replicas,
 * keeps a row each, and the leaderboard aggregates them at read time — online if any device is, and
 * the pps the sum of the online ones. This row is a mirror, not a ledger: it is rebuilt from the
 * next report and a wipe loses only presence, never the durable tally. Each write pushes the expiry
 * out, so a row outlives only a brief gap between reports.
 */
@Component
class PingerLiveStore(
    private val redis: StringRedisTemplate,
) {
    fun touch(
        identity: String,
        deviceId: String,
        online: Boolean,
        pps: Int,
        at: Instant,
    ) {
        val liveKey = liveKey(identity, deviceId)
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

    /**
     * The identity's presence across its devices, or null when none are live. Online is true if any
     * device is, the pps is the sum of the online devices', and lastSeen is the most recent of them.
     */
    fun aggregate(identity: String): PingerLive? {
        var online = false
        var pps = 0
        var lastSeen = 0L
        var seen = false
        val scan =
            ScanOptions
                .scanOptions()
                .match("${prefix(identity)}*")
                .count(SCAN_COUNT)
                .build()
        redis.scan(scan).use { cursor ->
            while (cursor.hasNext()) {
                val device = read(cursor.next()) ?: continue
                seen = true
                if (device.online) {
                    online = true
                    pps += device.pps
                }
                lastSeen = maxOf(lastSeen, device.lastSeen.toEpochMilli())
            }
        }
        return if (seen) PingerLive(online = online, pps = pps, lastSeen = Instant.ofEpochMilli(lastSeen)) else null
    }

    /**
     * Every identity's presence in one keyspace walk, so the public leaderboard reads live state
     * with a single SCAN rather than one per contributor. Buckets each device key by its identity
     * and aggregates the same way as [aggregate].
     */
    fun aggregateAll(): Map<String, PingerLive> {
        val acc = HashMap<String, Agg>()
        val scan =
            ScanOptions
                .scanOptions()
                .match("$KEY_PREFIX*")
                .count(SCAN_COUNT)
                .build()
        redis.scan(scan).use { cursor ->
            while (cursor.hasNext()) {
                val key = cursor.next()
                val identity = identityOf(key)
                val device = identity?.let { read(key) }
                if (identity != null && device != null) {
                    val a = acc.getOrPut(identity) { Agg() }
                    if (device.online) {
                        a.online = true
                        a.pps += device.pps
                    }
                    a.lastSeen = maxOf(a.lastSeen, device.lastSeen.toEpochMilli())
                }
            }
        }
        return acc.mapValues { PingerLive(it.value.online, it.value.pps, Instant.ofEpochMilli(it.value.lastSeen)) }
    }

    /** Every device row in one keyspace walk, unaggregated, for splitting the paint between devices. */
    fun devices(): List<PingerLiveDevice> {
        val devices = ArrayList<PingerLiveDevice>()
        val scan =
            ScanOptions
                .scanOptions()
                .match("$KEY_PREFIX*")
                .count(SCAN_COUNT)
                .build()
        redis.scan(scan).use { cursor ->
            while (cursor.hasNext()) {
                val key = cursor.next()
                // Split at the first '#': an identity never holds one, a device id may.
                val rest = key.removePrefix(KEY_PREFIX)
                val hash = rest.indexOf('#')
                val device = if (hash > 0) read(key) else null
                if (device != null) {
                    devices += PingerLiveDevice(rest.substring(0, hash), rest.substring(hash + 1), device)
                }
            }
        }
        return devices
    }

    private class Agg(
        var online: Boolean = false,
        var pps: Int = 0,
        var lastSeen: Long = 0L,
    )

    // The identity is the part between the fixed prefix and the '#' that precedes the device id.
    private fun identityOf(key: String): String? {
        val rest = key.removePrefix(KEY_PREFIX)
        if (rest.length == key.length) return null
        val hash = rest.lastIndexOf('#')
        return if (hash <= 0) null else rest.substring(0, hash)
    }

    private fun read(key: String): PingerLive? {
        val fields = redis.opsForHash<String, String>().entries(key)
        val online = fields["online"]?.toBoolean()
        val pps = fields["pps"]?.toIntOrNull()
        val lastSeen = fields["lastSeen"]?.toLongOrNull()?.let(Instant::ofEpochMilli)
        if (online == null || pps == null || lastSeen == null) return null
        return PingerLive(online, pps, lastSeen)
    }

    // The device id is a suffix after '#', which an identity ("member:123", "sitecie") never holds,
    // so the prefix match selects exactly one identity's devices.
    private fun prefix(identity: String) = "$KEY_PREFIX$identity#"

    private fun liveKey(
        identity: String,
        deviceId: String,
    ) = prefix(identity) + deviceId

    companion object {
        private const val KEY_PREFIX = "blueshell-api:pinger-live:"
        private val TTL: Duration = Duration.ofMinutes(1)
        private const val SCAN_COUNT = 256L
    }
}
