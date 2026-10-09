package net.blueshell.api.pinger.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.pinger.persistence.PingerLive
import net.blueshell.api.pinger.persistence.PingerLiveStore
import net.blueshell.api.pinger.persistence.PingerRecord
import net.blueshell.api.pinger.persistence.PingerRecordRepository
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class PingerRecordKeeperTest {
    private val now = Instant.parse("2026-10-09T21:14:00Z")
    private val records = mockk<PingerRecordRepository>()
    private val live = mockk<PingerLiveStore>()
    private val keeper = PingerRecordKeeper(records, live, Clock.fixed(now, ZoneOffset.UTC))

    private fun stored(pps: Long) {
        every { records.findById(1L) } returns Optional.of(PingerRecord(pps = pps))
    }

    @Test
    fun `a combined rate above the record raises it`() {
        stored(1_000)
        every { records.raise(2_000, now) } returns 1

        keeper.observe(2_000)

        verify { records.raise(2_000, now) }
    }

    @Test
    fun `a combined rate at or below the record writes nothing`() {
        stored(1_000)

        keeper.observe(1_000)
        keeper.observe(400)

        verify(exactly = 0) { records.raise(any(), any()) }
    }

    @Test
    fun `the record is read once and remembered, so a quiet second costs no read`() {
        stored(1_000)
        every { records.raise(any(), any()) } returns 1

        keeper.observe(500)
        keeper.observe(1_500)
        keeper.observe(1_200)

        verify(exactly = 1) { records.findById(1L) }
        verify(exactly = 1) { records.raise(any(), any()) }
    }

    @Test
    fun `a record another replica set higher is the one remembered`() {
        every { records.findById(1L) } returnsMany listOf(Optional.of(PingerRecord(pps = 1_000)), Optional.of(PingerRecord(pps = 5_000)))
        every { records.raise(2_000, now) } returns 0
        every { records.existsById(1L) } returns true

        keeper.observe(2_000)
        keeper.observe(2_000)

        verify(exactly = 1) { records.raise(any(), any()) }
    }

    @Test
    fun `a missing record row is set afresh`() {
        every { records.findById(1L) } returns Optional.empty()
        every { records.raise(2_000, now) } returns 0
        every { records.existsById(1L) } returns false
        every { records.save(any()) } answers { firstArg() }

        keeper.observe(2_000)

        verify { records.save(match { it.pps == 2_000L && it.setAt == now }) }
    }

    @Test
    fun `a tick sums every online sender's rate, SiteCie included, and skips the offline`() {
        stored(0)
        every { records.raise(any(), any()) } returns 1
        every { live.aggregateAll() } returns
            mapOf(
                "sitecie" to PingerLive(online = true, pps = 9_000, lastSeen = now),
                "member:1" to PingerLive(online = true, pps = 300, lastSeen = now),
                "member:2" to PingerLive(online = false, pps = 0, lastSeen = now),
            )

        keeper.tick()

        verify { records.raise(9_300, now) }
    }
}
