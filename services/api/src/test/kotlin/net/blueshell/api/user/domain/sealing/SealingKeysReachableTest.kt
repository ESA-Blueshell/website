package net.blueshell.api.user.domain.sealing

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SealingKeysReachableTest {
    private val sealer: Sealer = mock()

    @Test
    fun `starts where both keys seal`() {
        whenever(sealer.seal(any(), any())).thenReturn(listOf("vault:v1:x"))

        SealingKeysReachable(sealer, "api-address", "api-bank-details")

        verify(sealer).seal(eq("api-address"), any())
        verify(sealer).seal(eq("api-bank-details"), any())
    }

    @Test
    fun `refuses to start where a key cannot be used, naming it`() {
        whenever(sealer.seal(eq("api-address"), any())).thenReturn(listOf("vault:v1:x"))
        whenever(sealer.seal(eq("api-bank-details"), any())).doThrow(SealingUnavailable())

        assertThatThrownBy { SealingKeysReachable(sealer, "api-address", "api-bank-details") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("api-bank-details")
    }
}
