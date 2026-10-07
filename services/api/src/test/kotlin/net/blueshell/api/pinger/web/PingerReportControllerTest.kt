package net.blueshell.api.pinger.web

import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.pinger.api.PingerIdentity
import net.blueshell.api.pinger.api.PingerReportService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import java.time.Instant

class PingerReportControllerTest {
    private val reports = mockk<PingerReportService>(relaxUnitFun = true)
    private val controller = PingerReportController(reports)

    private fun memberAuth(subject: String) =
        JwtAuthenticationToken(
            Jwt
                .withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(60))
                .claim("roles", listOf("MEMBER"))
                .build(),
            listOf(SimpleGrantedAuthority("MEMBER")),
        )

    @Test
    fun `a member bearer resolves to the member and their roles`() {
        val response = controller.whoami(memberAuth("42"))

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

    @Test
    fun `a report accrues under the resolved identity`() {
        controller.report(memberAuth("42"), PingerReportRequest(online = true, pps = 128, sent = 500, errors = 3))

        verify { reports.report(PingerIdentity.Member(42), online = true, pps = 128, sent = 500) }
    }
}
