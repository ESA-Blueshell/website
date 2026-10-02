package net.blueshell.api.user.domain.sealing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.vault.core.VaultTemplate
import org.springframework.vault.support.VaultResponse
import java.util.Base64

class VaultTransitSealerTest {
    private val vault: VaultTemplate = mock()
    private val sealer = VaultTransitSealer(vault)

    private fun answer(vararg results: Map<String, Any?>) = VaultResponse().apply { data = mapOf("batch_results" to results.toList()) }

    private fun b64(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())

    @Test
    fun `seals and opens in one batch, each value with its own context`() {
        whenever(vault.write(eq("transit/encrypt/api-address"), any())).thenReturn(
            answer(
                mapOf("ciphertext" to "vault:v1:a"),
                mapOf(
                    "ciphertext" to "vault:v1:b",
                ),
            ),
        )
        whenever(vault.write(eq("transit/decrypt/api-address"), any())).thenReturn(answer(mapOf("plaintext" to b64("Enschede"))))

        assertThat(
            sealer.seal("api-address", listOf(Sealed("Enschede", "address:7"), Sealed("Hengelo", "address:8"))),
        ).containsExactly("vault:v1:a", "vault:v1:b")
        assertThat(sealer.open("api-address", listOf(Sealed("vault:v1:a", "address:7")))).containsExactly("Enschede")

        val body = argumentCaptor<Any>()
        verify(vault).write(eq("transit/encrypt/api-address"), body.capture())
        assertThat(body.firstValue).isEqualTo(
            mapOf(
                "batch_input" to
                    listOf(
                        mapOf("plaintext" to b64("Enschede"), "context" to b64("address:7")),
                        mapOf(
                            "plaintext" to b64("Hengelo"),
                            "context" to b64("address:8"),
                        ),
                    ),
            ),
        )
        assertThat(sealer.seal("api-address", emptyList())).isEmpty()
    }

    @Test
    fun `answers unavailable when Vault cannot be reached, and unopenable for a value under another context`() {
        whenever(vault.write(eq("transit/encrypt/api-address"), any())).thenThrow(IllegalStateException("connection refused"))
        assertThatThrownBy { sealer.seal("api-address", listOf(Sealed("x", "address:7"))) }.isInstanceOf(SealingUnavailable::class.java)

        whenever(vault.write(eq("transit/decrypt/api-address"), any())).thenReturn(
            answer(
                mapOf(
                    "error" to "cipher: message authentication failed",
                ),
            ),
        )
        assertThatThrownBy {
            sealer.open(
                "api-address",
                listOf(Sealed("vault:v1:a", "address:8")),
            )
        }.isInstanceOf(SealedValueUnopenable::class.java)

        whenever(vault.write(eq("transit/decrypt/api-address"), any())).thenReturn(null, answer(mapOf("plaintext" to null)))
        assertThatThrownBy {
            sealer.open(
                "api-address",
                listOf(Sealed("vault:v1:a", "address:7")),
            )
        }.isInstanceOf(SealingUnavailable::class.java)
        assertThatThrownBy {
            sealer.open(
                "api-address",
                listOf(Sealed("vault:v1:a", "address:7")),
            )
        }.isInstanceOf(SealingUnavailable::class.java)
    }
}
