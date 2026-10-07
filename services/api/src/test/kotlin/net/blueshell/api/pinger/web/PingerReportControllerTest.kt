package net.blueshell.api.pinger.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import java.time.Instant

class PingerReportControllerTest {
    private val controller = PingerReportController()

    @Test
    fun `a member bearer resolves to the member and their roles`() {
        val jwt =
            Jwt
                .withTokenValue("token")
                .header("alg", "none")
                .subject("42")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(60))
                .claim("roles", listOf("MEMBER"))
                .build()
        val authentication = JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("MEMBER")))

        val response = controller.whoami(authentication)

        assertThat(response.subject).isEqualTo("42")
        assertThat(response.member).isTrue()
        assertThat(response.roles).containsExactly("MEMBER")
    }

    @Test
    fun `the service token resolves to SiteCie with no member roles`() {
        val authentication =
            PreAuthenticatedAuthenticationToken("sitecie", null, listOf(SimpleGrantedAuthority("SITECIE")))

        val response = controller.whoami(authentication)

        assertThat(response.subject).isEqualTo("sitecie")
        assertThat(response.member).isFalse()
        assertThat(response.roles).isEmpty()
    }
}
