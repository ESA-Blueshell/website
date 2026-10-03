package net.blueshell.api.user.domain.sealing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class LocalSealerTest {
    private val sealer = LocalSealer()

    @Test
    fun `opens a value only under the context it was sealed with`() {
        val sealed = sealer.seal("api-address", listOf(Sealed("Drienerlolaan 5", "address:7"))).single()

        assertThat(sealed).startsWith("local:v1:").doesNotContain("Drienerlolaan")
        assertThat(sealer.open("api-address", listOf(Sealed(sealed, "address:7")))).containsExactly("Drienerlolaan 5")
        assertThatThrownBy { sealer.open("api-address", listOf(Sealed(sealed, "address:8"))) }
            .isInstanceOf(SealedValueUnopenable::class.java)
        assertThatThrownBy { sealer.open("api-bank-details", listOf(Sealed(sealed, "address:7"))) }
            .isInstanceOf(SealedValueUnopenable::class.java)
        assertThat(sealingContext("address", 7)).isEqualTo("address:7")
    }
}
