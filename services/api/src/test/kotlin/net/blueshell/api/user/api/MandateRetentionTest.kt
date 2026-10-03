package net.blueshell.api.user.api

import net.blueshell.api.user.persistence.MandateRetentionRepository
import net.blueshell.api.user.persistence.PendingMandateRepository
import net.blueshell.api.user.persistence.StoppedMandateRow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class MandateRetentionTest {
    private val mandates: MandateRetentionRepository = mock()
    private val pending: PendingMandateRepository = mock()
    private val retention = MandateRetention(mandates, pending)

    @Test
    fun `names the mandates no longer collected from, an erased account's among them, and wipes one`() {
        val today = LocalDate.of(2026, 10, 3)
        val row =
            object : StoppedMandateRow {
                override val id = 12L
                override val userId = 7L
                override val reference = "BLUESHELL-12-20250901"
            }
        whenever(mandates.findStopped(today, "%@deleted.invalid")).thenReturn(listOf(row))
        whenever(mandates.wipe(12)).thenReturn(1, 0)

        assertThat(retention.stopped(today)).containsExactly(StoppedMandate(12, 7, "BLUESHELL-12-20250901"))
        assertThat(retention.wipe(12)).isTrue()
        assertThat(retention.wipe(12)).isFalse()
    }

    @Test
    fun `a pending mandate goes with its account`() {
        retention.forgetPending(7)

        verify(pending).deleteByUserId(7)
    }
}
