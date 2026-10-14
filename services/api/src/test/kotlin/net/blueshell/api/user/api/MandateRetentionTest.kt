package net.blueshell.api.user.api

import net.blueshell.api.user.persistence.MandateRetentionRepository
import net.blueshell.api.user.persistence.StoppedMandateRow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class MandateRetentionTest {
    private val mandates: MandateRetentionRepository = mock()
    private val retention = MandateRetention(mandates)

    @Test
    fun `names the mandates no longer collected from, and wipes one only while it still is the one judged`() {
        val today = LocalDate.of(2026, 10, 3)
        val row =
            object : StoppedMandateRow {
                override val userId = 7L
                override val reference = "BLUESHELL-7-20250901"
            }
        whenever(mandates.findStopped(today)).thenReturn(listOf(row))
        // The write checks the row again: the second time the mandate was recorded anew, so it is left.
        whenever(mandates.wipe(7, "BLUESHELL-7-20250901", today)).thenReturn(1, 0)

        val stopped = retention.stopped(today).single()
        assertThat(stopped).isEqualTo(StoppedMandate(7, "BLUESHELL-7-20250901"))
        assertThat(retention.wipe(stopped, today)).isTrue()
        assertThat(retention.wipe(stopped, today)).isFalse()
    }

    @Test
    fun `an erased account comes off incasso`() {
        retention.accountErased(7)

        verify(mandates).stopCollecting(7)
    }
}
