package net.blueshell.api.platform.oidc

import com.nimbusds.jose.jwk.JWKMatcher
import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.RSAKey
import net.blueshell.common.vault.VaultPublicKey
import net.blueshell.common.vault.VaultTransitClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64

class VaultTransitJwkSourceTest {

    private val keyName = "api-jwt"
    private val selector = JWKSelector(JWKMatcher.Builder().build())

    @Test
    fun `initial refresh succeeds and get returns the cached key`() {
        val (pem, modulus) = generateRsaPem()
        val client: VaultTransitClient = mock()
        whenever(client.readPublicKeys(eq(keyName)))
            .thenReturn(listOf(VaultPublicKey(1, pem)))

        val source = VaultTransitJwkSource(client, keyName).also { it.init() }

        val keys = source.get(selector, null)
        assertThat(keys).hasSize(1)
        assertThat((keys.single() as RSAKey).modulus.decodeToBigInteger()).isEqualTo(modulus)
    }

    @Test
    fun `initial refresh failure serves empty set then recovers on schedule`() {
        val (pem, modulus) = generateRsaPem()
        val client: VaultTransitClient = mock()
        // NoClassDefFoundError (Error subclass) — the failure mode when
        // bcpkix is absent is exactly this, so the catch must cover Error too.
        whenever(client.readPublicKeys(eq(keyName)))
            .thenThrow(NoClassDefFoundError("simulating missing bcpkix"))
            .thenReturn(listOf(VaultPublicKey(1, pem)))

        val source = VaultTransitJwkSource(client, keyName).also { it.init() }
        assertThat(source.get(selector, null)).isEmpty()

        source.scheduledRefresh()

        val keys = source.get(selector, null)
        assertThat(keys).hasSize(1)
        assertThat((keys.single() as RSAKey).modulus.decodeToBigInteger()).isEqualTo(modulus)
    }

    @Test
    fun `scheduled refresh failure preserves previously cached set`() {
        val (pem, modulus) = generateRsaPem()
        val client: VaultTransitClient = mock()
        whenever(client.readPublicKeys(eq(keyName)))
            .thenReturn(listOf(VaultPublicKey(1, pem)))
            .thenThrow(RuntimeException("vault hiccup"))

        val source = VaultTransitJwkSource(client, keyName).also { it.init() }
        assertThat(source.get(selector, null)).hasSize(1)

        source.scheduledRefresh()

        val keys = source.get(selector, null)
        assertThat(keys).hasSize(1)
        assertThat((keys.single() as RSAKey).modulus.decodeToBigInteger()).isEqualTo(modulus)
    }

    @Test
    fun `refresh with empty result preserves previously cached set`() {
        val (pem, modulus) = generateRsaPem()
        val client: VaultTransitClient = mock()
        whenever(client.readPublicKeys(eq(keyName)))
            .thenReturn(listOf(VaultPublicKey(1, pem)))
            .thenReturn(emptyList())

        val source = VaultTransitJwkSource(client, keyName).also { it.init() }
        source.scheduledRefresh()

        val keys = source.get(selector, null)
        assertThat(keys).hasSize(1)
        assertThat((keys.single() as RSAKey).modulus.decodeToBigInteger()).isEqualTo(modulus)
    }

    private fun generateRsaPem(): Pair<String, java.math.BigInteger> {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pub = pair.public as RSAPublicKey
        val b64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pub.encoded)
        val pem = "-----BEGIN PUBLIC KEY-----\n$b64\n-----END PUBLIC KEY-----\n"
        return pem to pub.modulus
    }
}
