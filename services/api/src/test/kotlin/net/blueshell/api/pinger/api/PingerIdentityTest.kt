package net.blueshell.api.pinger.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import java.time.Instant

class PingerIdentityTest {
    @Test
    fun `a member bearer resolves to the member its subject names`() {
        val jwt =
            Jwt
                .withTokenValue("token")
                .header("alg", "none")
                .subject("42")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(60))
                .build()
        val authentication = JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("MEMBER")))

        val identity = PingerIdentity.of(authentication)

        assertThat(identity).isEqualTo(PingerIdentity.Member(42))
        assertThat(identity.key).isEqualTo("member:42")
        assertThat(identity.memberId).isEqualTo(42)
    }

    @Test
    fun `the service token resolves to SiteCie with no member`() {
        val authentication =
            PreAuthenticatedAuthenticationToken("sitecie", null, listOf(SimpleGrantedAuthority("SITECIE")))

        val identity = PingerIdentity.of(authentication)

        assertThat(identity).isEqualTo(PingerIdentity.Sitecie)
        assertThat(identity.key).isEqualTo("sitecie")
        assertThat(identity.memberId).isNull()
    }
}
