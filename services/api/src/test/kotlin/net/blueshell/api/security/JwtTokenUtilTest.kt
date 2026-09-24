package net.blueshell.api.security

import net.blueshell.api.platform.config.SettableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class JwtTokenUtilTest {
    private val sharedSecret = "2goYh5PqH6dPkWWXLUJQ4QY6nD2YgR5qk9+6Yu8aITR7cfwxkuNolL9zkgf2qHFxifWdbxG+E+XqMIKkt3ibDw=="
    private val clock = SettableClock().apply { set(Instant.parse("2026-09-24T12:00:00Z")) }

    private fun util(
        issuer: String = "blueshell-api",
        audience: String = "blueshell-web",
    ) = JwtTokenUtil(sharedSecret, issuer, audience, clock)

    @Test
    fun `a minted token names its sign-in and token id`() {
        val token = util().mint("7", sid = "sign-in-1", jti = "jti-1", expiresAt = clock.instant().plusSeconds(60))

        assertThat(util().read(token)).isEqualTo(JwtTokenUtil.Claims(subject = "7", sid = "sign-in-1", jti = "jti-1"))
    }

    @Test
    fun `a token past its expiry reads as nothing`() {
        val token = util().mint("7", sid = "s", jti = "j", expiresAt = clock.instant().plusSeconds(60))

        clock.advance(Duration.ofSeconds(61))

        assertThat(util().read(token)).isNull()
    }

    @Test
    fun `a token from another issuer or for another audience reads as nothing`() {
        val expiresAt = clock.instant().plusSeconds(60)

        assertThat(util().read(util(issuer = "elsewhere").mint("7", "s", "j", expiresAt))).isNull()
        assertThat(util().read(util(audience = "elsewhere").mint("7", "s", "j", expiresAt))).isNull()
    }

    @Test
    fun `an empty or mangled token reads as nothing`() {
        assertThat(util().read("")).isNull()
        assertThat(util().read("not.a.token")).isNull()
    }

    @Test
    fun `a token without a sign-in id reads as nothing`() {
        assertThat(util().read("not-a-token")).isNull()
        assertThat(util().read("")).isNull()
    }
}
