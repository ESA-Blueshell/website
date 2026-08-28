package net.blueshell.api.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JwtRevocationServiceTest {

    @Test
    fun `reads initial revoked set from configuration string`() {
        val service = JwtRevocationService("a,b , c")

        assertThat(service.isRevoked("a")).isTrue()
        assertThat(service.isRevoked("b")).isTrue()
        assertThat(service.isRevoked("c")).isTrue()
        assertThat(service.isRevoked("d")).isFalse()
    }

    @Test
    fun `supports runtime revocation`() {
        val service = JwtRevocationService("")

        assertThat(service.isRevoked("new-jti")).isFalse()
        service.revoke("new-jti")
        assertThat(service.isRevoked("new-jti")).isTrue()
    }
}
