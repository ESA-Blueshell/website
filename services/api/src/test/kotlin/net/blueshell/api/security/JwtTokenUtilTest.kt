package net.blueshell.api.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JwtTokenUtilTest {

    private val sharedSecret = "2goYh5PqH6dPkWWXLUJQ4QY6nD2YgR5qk9+6Yu8aITR7cfwxkuNolL9zkgf2qHFxifWdbxG+E+XqMIKkt3ibDw=="

    @Test
    fun `generated token contains expected claims and validates`() {
        val util = JwtTokenUtil(
            expiration = 60_000,
            secret = sharedSecret,
            issuer = "blueshell-api",
            audience = "blueshell-web"
        )

        val token = util.generateToken("alice")
        val validation = util.parseAndValidate(token)

        assertThat(validation.isValid).isTrue()
        assertThat(validation.username).isEqualTo("alice")
        assertThat(validation.jti).isNotBlank()
        assertThat(util.getUsernameFromToken(token)).isEqualTo("alice")
    }

    @Test
    fun `token with invalid issuer is rejected`() {
        val issuerA = JwtTokenUtil(
            expiration = 60_000,
            secret = sharedSecret,
            issuer = "issuer-a",
            audience = "blueshell-web"
        )
        val issuerB = JwtTokenUtil(
            expiration = 60_000,
            secret = sharedSecret,
            issuer = "issuer-b",
            audience = "blueshell-web"
        )

        val token = issuerA.generateToken("alice")
        val validation = issuerB.parseAndValidate(token)

        assertThat(validation.isValid).isFalse()
        assertThat(validation.error?.message).contains("issuer")
    }

    @Test
    fun `token with invalid audience is rejected`() {
        val audienceA = JwtTokenUtil(
            expiration = 60_000,
            secret = sharedSecret,
            issuer = "blueshell-api",
            audience = "aud-a"
        )
        val audienceB = JwtTokenUtil(
            expiration = 60_000,
            secret = sharedSecret,
            issuer = "blueshell-api",
            audience = "aud-b"
        )

        val token = audienceA.generateToken("alice")
        val validation = audienceB.parseAndValidate(token)

        assertThat(validation.isValid).isFalse()
        assertThat(validation.error?.message).contains("audience")
    }
}
