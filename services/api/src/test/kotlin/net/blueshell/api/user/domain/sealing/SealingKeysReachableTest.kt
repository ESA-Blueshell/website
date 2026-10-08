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
    fun `starts where the address key seals`() {
        whenever(sealer.seal(any(), any())).thenReturn(listOf("vault:v1:x"))

        SealingKeysReachable(sealer, "api-address")

        verify(sealer).seal(eq("api-address"), any())
    }

    @Test
    fun `refuses to start where the key cannot be used, naming it`() {
        whenever(sealer.seal(eq("api-address"), any())).doThrow(SealingUnavailable())

        assertThatThrownBy { SealingKeysReachable(sealer, "api-address") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("api-address")
    }
}
