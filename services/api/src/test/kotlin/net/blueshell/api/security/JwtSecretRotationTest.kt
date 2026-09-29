package net.blueshell.api.security

import net.blueshell.api.platform.config.SettableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.mock.env.MockEnvironment
import java.time.Instant
import java.util.Base64

class JwtSecretRotationTest {
    private val first = Base64.getEncoder().encodeToString(ByteArray(64) { 1 })
    private val second = Base64.getEncoder().encodeToString(ByteArray(64) { 2 })
    private val clock = SettableClock().apply { set(Instant.parse("2026-09-29T12:00:00Z")) }
    private val tokens = JwtTokenUtil(first, "api", "web", clock)
    private val environment = MockEnvironment()
    private val rotation = JwtSecretRotation(tokens, environment)

    private fun vaultHolds(secret: String) {
        environment.setProperty("app.jwt.secret", secret)
        rotation.onChange(EnvironmentChangeEvent(setOf("app.jwt.secret")))
    }

    private fun mint() = tokens.mint("7", "sign-in", "jti", clock.instant().plusSeconds(600))

    @Test
    fun `a token signed before the rotation still reads, and new tokens use the new secret`() {
        val before = mint()

        vaultHolds(second)

        assertThat(tokens.read(before)?.sid).isEqualTo("sign-in")
        assertThat(JwtTokenUtil(second, "api", "web", clock).read(mint())?.sid).isEqualTo("sign-in")
    }

    @Test
    fun `two rotations later the first secret no longer reads`() {
        val before = mint()

        vaultHolds(second)
        vaultHolds(Base64.getEncoder().encodeToString(ByteArray(64) { 3 }))

        assertThat(tokens.read(before)).isNull()
    }

    @Test
    fun `a secret too short for HS512 is refused and the secret in use stays`() {
        vaultHolds(Base64.getEncoder().encodeToString(ByteArray(16)))

        assertThat(JwtTokenUtil(first, "api", "web", clock).read(mint())?.sid).isEqualTo("sign-in")
    }

    @Test
    fun `a secret that is not Base64 is refused and the secret in use stays`() {
        vaultHolds("not base64 !")

        assertThat(JwtTokenUtil(first, "api", "web", clock).read(mint())?.sid).isEqualTo("sign-in")
    }

    @Test
    fun `a change to another key leaves the signer alone`() {
        environment.setProperty("app.jwt.secret", "not base64 !")
        rotation.onChange(EnvironmentChangeEvent(setOf("brevo.apiKey")))

        assertThat(JwtTokenUtil(first, "api", "web", clock).read(mint())?.sid).isEqualTo("sign-in")
    }
}
