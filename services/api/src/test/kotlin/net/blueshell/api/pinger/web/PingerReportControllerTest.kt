package net.blueshell.api.pinger.web

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.pinger.api.PingerIdentity
import net.blueshell.api.pinger.api.PingerReportService
import net.blueshell.api.pinger.api.PingerShare
import net.blueshell.api.pinger.api.PingerShareService
import net.blueshell.api.shared.user.MemberIdentities
import net.blueshell.api.shared.user.MemberIdentity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant

class PingerReportControllerTest {
    private val reports = mockk<PingerReportService>(relaxUnitFun = true)
    private val identities = mockk<MemberIdentities>()
    private val shares = mockk<PingerShareService>()
    private val shareStream = mockk<PingerShareStream>()
    private val controller = PingerReportController(reports, identities, shares, shareStream)

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
    fun `a member bearer resolves to the member, their username and roles`() {
        every { identities.of(listOf(42)) } returns mapOf(42L to MemberIdentity(username = "joris", discordId = null))

        val response = controller.whoami(memberAuth("42"))

        assertThat(response.subject).isEqualTo("42")
        assertThat(response.username).isEqualTo("joris")
        assertThat(response.member).isTrue()
        assertThat(response.roles).containsExactly("MEMBER")
    }

    @Test
    fun `a member with no resolvable identity has no username`() {
        every { identities.of(listOf(42)) } returns emptyMap()

        val response = controller.whoami(memberAuth("42"))

        assertThat(response.subject).isEqualTo("42")
        assertThat(response.username).isNull()
    }

    @Test
    fun `the service token resolves to SiteCie with no member roles`() {
        val authentication =
            PreAuthenticatedAuthenticationToken("sitecie", null, listOf(SimpleGrantedAuthority("SITECIE")))

        val response = controller.whoami(authentication)

        assertThat(response.subject).isEqualTo("sitecie")
        assertThat(response.username).isNull()
        assertThat(response.member).isFalse()
        assertThat(response.roles).isEmpty()
    }

    @Test
    fun `a report accrues under the resolved identity`() {
        controller.report(memberAuth("42"), PingerReportRequest(deviceId = "device-7", online = true, pps = 128, sent = 500, errors = 3))

        verify { reports.report(PingerIdentity.Member(42), deviceId = "device-7", online = true, pps = 128, sent = 500) }
    }

    @Test
    fun `a share is computed for the resolved identity's device`() {
        every { shares.share("member:42", "device-7") } returns PingerShare(from = 0.25, to = 0.5, devices = 4)

        assertThat(controller.share(memberAuth("42"), "device-7")).isEqualTo(PingerShareResponse(from = 0.25, to = 0.5, devices = 4))
    }

    @Test
    fun `SiteCie's share is computed under the sitecie identity`() {
        every { shares.share("sitecie", "replica-a") } returns PingerShare(from = 0.0, to = 1.0, devices = 1)
        val authentication = PreAuthenticatedAuthenticationToken("sitecie", null, listOf(SimpleGrantedAuthority("SITECIE")))

        assertThat(controller.share(authentication, "replica-a").devices).isEqualTo(1)
    }

    @Test
    fun `the share stream opens for the resolved identity's device`() {
        val emitter = SseEmitter()
        every { shareStream.open("member:42", "device-7") } returns emitter

        assertThat(controller.shareStream(memberAuth("42"), "device-7")).isSameAs(emitter)
    }

    @Test
    fun `a blank or oversized device id is refused`() {
        assertThatThrownBy { controller.share(memberAuth("42"), " ") }.isInstanceOf(ResponseStatusException::class.java)
        assertThatThrownBy { controller.shareStream(memberAuth("42"), "x".repeat(65)) }
            .isInstanceOf(ResponseStatusException::class.java)
    }
}
