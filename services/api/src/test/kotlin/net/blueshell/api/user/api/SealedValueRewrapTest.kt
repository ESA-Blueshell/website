package net.blueshell.api.user.api

import net.blueshell.api.user.domain.sealing.Sealed
import net.blueshell.api.user.domain.sealing.Sealer
import net.blueshell.api.user.domain.sealing.SealingUnavailable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus

class SealedValueRewrapTest {
    private val sealer: Sealer = mock()
    private val addresses: SealedField =
        mock {
            on { name } doReturn "address"
            on { key } doReturn "api-address"
        }

    private val transactions: PlatformTransactionManager = mock { on { getTransaction(any()) } doReturn SimpleTransactionStatus() }

    private fun rewrap(vararg fields: SealedField = arrayOf(addresses)) = SealedValueRewrap(sealer, fields.toList(), transactions)

    private fun value(
        id: Long,
        version: Int,
    ) = SealedValue(id, "vault:v$version:$id", "address:$id")

    @Test
    fun `moves the values below the newest version, leaves one already on it, and records one that fails`() {
        whenever(addresses.sealedValues()).thenReturn(listOf(value(1, 1), value(2, 2), value(3, 1), value(4, 1)))
        whenever(sealer.rewrap(any(), any())).thenReturn(listOf("vault:v2:one", "vault:v2:again", null, "vault:v2:four"))
        whenever(addresses.swap(1, "vault:v1:1", "vault:v2:one")).thenReturn(true)
        // Saved in between, which sealed it on the newest version already.
        whenever(addresses.swap(4, "vault:v1:4", "vault:v2:four")).thenReturn(false)

        val report = rewrap().rewrapEvery()

        assertThat(report).isEqualTo(RewrapReport(moved = 1, failed = listOf("address 3")))
        verify(sealer).rewrap("api-address", listOf(1L, 2L, 3L, 4L).map { Sealed(value(it, if (it == 2L) 2 else 1).sealed, "address:$it") })
        verify(addresses, never()).swap(any(), any(), eq("vault:v2:again"))
    }

    @Test
    fun `a batch the vault refuses whole fails its values and the next field still runs`() {
        val bank: SealedField =
            mock {
                on { name } doReturn "bank details"
                on { key } doReturn "api-bank-details"
                on { sealedValues() } doReturn listOf(SealedValue(9, "vault:v1:9", "bank:9"))
                on { swap(9, "vault:v1:9", "vault:v2:9") } doReturn true
            }
        whenever(addresses.sealedValues()).thenReturn(listOf(value(1, 1), value(2, 1)))
        whenever(sealer.rewrap(eq("api-address"), any())).doThrow(SealingUnavailable())
        whenever(sealer.rewrap(eq("api-bank-details"), any())).thenReturn(listOf("vault:v2:9"))

        assertThat(rewrap(addresses, bank).rewrapEvery()).isEqualTo(RewrapReport(moved = 1, failed = listOf("address 1", "address 2")))
    }

    @Test
    fun `with nothing sealed it asks the vault nothing`() {
        whenever(addresses.sealedValues()).thenReturn(emptyList())

        assertThat(rewrap().rewrapEvery()).isEqualTo(RewrapReport(0, emptyList()))
        verify(sealer, never()).rewrap(any(), any())
    }
}
