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
    fun `touch writes a live row find reads back`() {
        val at = Instant.parse("2026-10-07T12:00:00Z")

        store.touch("member:42", online = true, pps = 128, at = at)

        val live = store.find("member:42")
        assertThat(live).isEqualTo(PingerLive(online = true, pps = 128, lastSeen = at))
    }

    @Test
    fun `a later touch overwrites the earlier presence`() {
        store.touch("sitecie", online = true, pps = 200, at = Instant.parse("2026-10-07T12:00:00Z"))
        val later = Instant.parse("2026-10-07T12:00:05Z")

        store.touch("sitecie", online = false, pps = 0, at = later)

        assertThat(store.find("sitecie")).isEqualTo(PingerLive(online = false, pps = 0, lastSeen = later))
    }

    @Test
    fun `find returns null when nothing was reported`() {
        assertThat(store.find("member:7")).isNull()
    }
}
