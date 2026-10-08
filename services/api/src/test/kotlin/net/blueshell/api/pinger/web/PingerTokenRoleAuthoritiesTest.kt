package net.blueshell.api.pinger.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class PingerTokenRoleAuthoritiesTest {
    private val authorities = PingerTokenRoleAuthorities()

    private fun tokenWithRoles(roles: List<String>?): Jwt =
        Jwt
            .withTokenValue("token")
            .header("alg", "none")
            .subject("1")
            .issuedAt(Instant.EPOCH)
            .expiresAt(Instant.EPOCH.plusSeconds(60))
            .apply { roles?.let { claim("roles", it) } }
            .build()

    @Test
    fun `a member token carries the member authority and the roles it inherits`() {
        val granted = authorities.convert(tokenWithRoles(listOf("MEMBER"))).map { it.authority }

        assertThat(granted).contains("MEMBER", "GUEST", "ANONYMOUS")
    }

    @Test
    fun `an admin token answers a member check through the role hierarchy`() {
        val granted = authorities.convert(tokenWithRoles(listOf("ADMIN"))).map { it.authority }

        assertThat(granted).contains("ADMIN", "MEMBER")
    }

    @Test
    fun `an unknown role name is dropped rather than trusted`() {
        val granted = authorities.convert(tokenWithRoles(listOf("WIZARD", "MEMBER"))).map { it.authority }

        assertThat(granted).contains("MEMBER").doesNotContain("WIZARD")
    }

    @Test
    fun `a token with no roles claim grants nothing`() {
        assertThat(authorities.convert(tokenWithRoles(null))).isEmpty()
    }
}
