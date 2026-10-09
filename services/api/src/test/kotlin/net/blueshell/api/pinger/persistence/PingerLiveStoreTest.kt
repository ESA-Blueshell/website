package net.blueshell.api.pinger.persistence

import net.blueshell.api.testsupport.UnitValkey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class PingerLiveStoreTest {
    private val store = PingerLiveStore(UnitValkey.template)

    @BeforeEach
    fun clear() {
        UnitValkey.flush()
    }

    @Test
    fun `touch writes a device row aggregate reads it back`() {
        val at = Instant.parse("2026-10-07T12:00:00Z")

        store.touch("member:42", "laptop", online = true, pps = 128, at = at)

        assertThat(store.aggregate("member:42")).isEqualTo(PingerLive(online = true, pps = 128, lastSeen = at))
    }

    @Test
    fun `a later touch of the same device overwrites its earlier presence`() {
        store.touch("sitecie", "replica-a", online = true, pps = 200, at = Instant.parse("2026-10-07T12:00:00Z"))
        val later = Instant.parse("2026-10-07T12:00:05Z")

        store.touch("sitecie", "replica-a", online = false, pps = 0, at = later)

        assertThat(store.aggregate("sitecie")).isEqualTo(PingerLive(online = false, pps = 0, lastSeen = later))
    }

    @Test
    fun `aggregate sums the online devices' rates and takes the latest lastSeen`() {
        val earlier = Instant.parse("2026-10-07T12:00:00Z")
        val later = Instant.parse("2026-10-07T12:00:03Z")
        store.touch("member:42", "laptop", online = true, pps = 170, at = earlier)
        store.touch("member:42", "phone", online = true, pps = 130, at = later)

        assertThat(store.aggregate("member:42")).isEqualTo(PingerLive(online = true, pps = 300, lastSeen = later))
    }

    @Test
    fun `aggregate is online when any device is and counts only the online devices' rates`() {
        val at = Instant.parse("2026-10-07T12:00:00Z")
        store.touch("member:42", "laptop", online = true, pps = 170, at = at)
        store.touch("member:42", "phone", online = false, pps = 0, at = at)

        val live = store.aggregate("member:42")!!
        assertThat(live.online).isTrue()
        assertThat(live.pps).isEqualTo(170)
    }

    @Test
    fun `one identity's devices do not leak into another's aggregate`() {
        val at = Instant.parse("2026-10-07T12:00:00Z")
        store.touch("member:4", "d", online = true, pps = 10, at = at)
        store.touch("member:42", "d", online = true, pps = 99, at = at)

        assertThat(store.aggregate("member:4")).isEqualTo(PingerLive(online = true, pps = 10, lastSeen = at))
    }

    @Test
    fun `aggregate returns null when nothing was reported`() {
        assertThat(store.aggregate("member:7")).isNull()
    }

    @Test
    fun `aggregateAll buckets every identity's devices in one read`() {
        val at = Instant.ofEpochMilli(1_000)
        store.touch("member:1", "a", online = true, pps = 100, at = at)
        store.touch("member:1", "b", online = true, pps = 50, at = at.plusMillis(5))
        store.touch("member:1", "c", online = false, pps = 999, at = at)
        store.touch("sitecie", "r1", online = false, pps = 0, at = at)

        val all = store.aggregateAll()

        assertThat(all["member:1"]).isEqualTo(PingerLive(online = true, pps = 150, lastSeen = at.plusMillis(5)))
        assertThat(all.getValue("sitecie").online).isFalse()
    }

    @Test
    fun `devices lists every device row apart with its identity and device id`() {
        val at = Instant.ofEpochMilli(1_000)
        store.touch("member:1", "a", online = true, pps = 100, at = at)
        store.touch("member:1", "b#2", online = false, pps = 0, at = at)
        store.touch("sitecie", "r1", online = true, pps = 7, at = at)

        assertThat(store.devices()).containsExactlyInAnyOrder(
            PingerLiveDevice("member:1", "a", PingerLive(online = true, pps = 100, lastSeen = at)),
            PingerLiveDevice("member:1", "b#2", PingerLive(online = false, pps = 0, lastSeen = at)),
            PingerLiveDevice("sitecie", "r1", PingerLive(online = true, pps = 7, lastSeen = at)),
        )
    }

    @Test
    fun `devices skips a key outside the device shape`() {
        UnitValkey.template.opsForHash<String, String>().putAll("blueshell-api:pinger-live:nohash", mapOf("online" to "true"))
        UnitValkey.template.opsForHash<String, String>().putAll("blueshell-api:pinger-live:member:1#d", mapOf("online" to "true"))

        assertThat(store.devices()).isEmpty()
    }
}
