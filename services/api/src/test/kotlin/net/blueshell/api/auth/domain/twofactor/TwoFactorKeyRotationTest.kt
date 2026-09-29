package net.blueshell.api.auth.domain.twofactor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.mock.env.MockEnvironment
import java.util.Base64

class TwoFactorKeyRotationTest {
    private val keyA = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
    private val keyB = Base64.getEncoder().encodeToString(ByteArray(32) { 2 })
    private val cipher = SecretCipher("a", keyA, "")
    private val environment = MockEnvironment()
    private val rotation = TwoFactorKeyRotation(cipher, environment)

    private fun vaultHolds(
        id: String,
        key: String,
        retired: String,
    ) {
        environment.setProperty("app.two-factor.key-id", id)
        environment.setProperty("app.two-factor.key", key)
        environment.setProperty("app.two-factor.retired-keys", retired)
        rotation.onChange(EnvironmentChangeEvent(setOf("app.two-factor.key", "app.two-factor.key-id")))
    }

    @Test
    fun `a rotated key seals from then on, and what the retired key sealed still opens`() {
        val before = cipher.seal("hello".toByteArray())

        vaultHolds("b", keyB, "a:$keyA")

        assertThat(cipher.seal("x".toByteArray()).keyId).isEqualTo("b")
        assertThat(cipher.open(before)).isEqualTo("hello".toByteArray())
    }

    @Test
    fun `a key of the wrong length is refused and the keys in use stay`() {
        vaultHolds("b", Base64.getEncoder().encodeToString(ByteArray(16)), "a:$keyA")

        assertThat(cipher.seal("x".toByteArray()).keyId).isEqualTo("a")
    }

    @Test
    fun `a change to another key leaves the cipher alone`() {
        environment.setProperty("app.two-factor.key", "not even base64")
        rotation.onChange(EnvironmentChangeEvent(setOf("brevo.apiKey")))

        assertThat(cipher.seal("x".toByteArray()).keyId).isEqualTo("a")
    }
}
