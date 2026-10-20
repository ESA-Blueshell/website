package net.blueshell.api.user.api

import net.blueshell.api.user.persistence.MandateRetentionRepository
import net.blueshell.api.user.persistence.StoppedAccountRow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class MandateRetentionTest {
    private val mandates: MandateRetentionRepository = mock()
    private val clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC)
    private val retention = MandateRetention(mandates, clock)

    @Test
    fun `names the mandates no longer collected from, and wipes one only while it still is the one judged`() {
        val today = LocalDate.of(2026, 10, 3)
        val row =
            object : StoppedAccountRow {
                override val userId = 7L
                override val reference = "BLUESHELL-7-20250901"
                override val recordedAt: Instant = Instant.parse("2025-09-01T10:00:00Z")
                override val lastPaidToOn: LocalDate = LocalDate.of(2025, 10, 1)
            }
        whenever(mandates.findStopped(today)).thenReturn(listOf(row))
        // The write checks the row again: the second time the mandate was recorded anew, so it is left.
        whenever(mandates.wipe(7, "BLUESHELL-7-20250901", today)).thenReturn(1, 0)

        val stopped = retention.stopped(today).single()
        assertThat(stopped).isEqualTo(StoppedAccount(7, "BLUESHELL-7-20250901", LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 1)))
        assertThat(retention.wipe(stopped, today)).isTrue()
        assertThat(retention.wipe(stopped, today)).isFalse()
    }

    @Test
    fun `an erased account's mandate ends today`() {
        retention.accountErased(7)

        verify(mandates).stopCollecting(7, LocalDate.now(clock))
    }
}
