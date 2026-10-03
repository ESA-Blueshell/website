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

    @Test
    fun `a rotated key seals under its newest version, and rewrapping moves an older value onto it`() {
        val old = sealer.seal("api-address", listOf(Sealed("Drienerlolaan 5", "address:7"))).single()

        assertThat(sealer.rotate("api-address")).isEqualTo(2)
        assertThat(sealer.rotate("api-address")).isEqualTo(3)

        assertThat(sealer.seal("api-address", listOf(Sealed("Hallenweg 5", "address:8"))).single()).startsWith("local:v3:")
        assertThat(sealer.open("api-address", listOf(Sealed(old, "address:7")))).containsExactly("Drienerlolaan 5")

        val moved = sealer.rewrap("api-address", listOf(Sealed(old, "address:7"), Sealed(old, "address:8")))
        assertThat(moved[1]).isNull()
        assertThat(keyVersionOf(old)).isEqualTo(1)
        assertThat(keyVersionOf(moved[0]!!)).isEqualTo(3)
        assertThat(sealer.open("api-address", listOf(Sealed(moved[0]!!, "address:7")))).containsExactly("Drienerlolaan 5")
    }

    @Test
    fun `a sealed value that names no key version is refused`() {
        assertThatThrownBy { keyVersionOf("plain") }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
