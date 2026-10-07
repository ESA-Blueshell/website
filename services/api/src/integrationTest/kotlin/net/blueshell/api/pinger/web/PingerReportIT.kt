package net.blueshell.api.pinger.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The `pinger/report` chain is the one chain that accepts a bearer, so these requests carry a
 * token or the service header rather than the auth cookie the rest of the site runs on.
 */
@SpringBootTest
class PingerReportIT : UserTestSupport() {
    @Autowired
    private lateinit var jwtEncoder: JwtEncoder

    @Value($$"${pinger.report.service-token}")
    private lateinit var serviceToken: String

    private fun memberBearer(member: User): String {
        val now = clock.instant()
        val claims =
            JwtClaimsSet
                .builder()
                .issuer("https://esa-blueshell.nl/api")
                .subject(requireNotNull(member.id).toString())
                .audience(listOf("pinger-app"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", listOf(Role.MEMBER.name))
                .build()
        val header = JwsHeader.with(SignatureAlgorithm.RS256).build()
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    @Test
    fun `a valid member bearer resolves to the member`() {
        val member = createUserWithRole(Role.MEMBER)

        mvc
            .perform(get("/pinger/report/whoami").header("Authorization", "Bearer ${memberBearer(member)}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.member").value(true))
            .andExpect(jsonPath("$.subject").value(member.id.toString()))
            .andExpect(jsonPath("$.roles[0]").value("MEMBER"))
    }

    @Test
    fun `the service token resolves to SiteCie`() {
        mvc
            .perform(get("/pinger/report/whoami").header("X-Pinger-Service-Token", serviceToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.member").value(false))
            .andExpect(jsonPath("$.subject").value("sitecie"))
    }

    @Test
    fun `the auth cookie alone does not reach the report chain`() {
        val member = createUserWithRole(Role.MEMBER)

        mvc
            .perform(get("/pinger/report/whoami").with(signedIn(member)))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a request with no credentials is refused`() {
        mvc
            .perform(get("/pinger/report/whoami"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a wrong service token is refused`() {
        mvc
            .perform(get("/pinger/report/whoami").header("X-Pinger-Service-Token", "not-the-token"))
            .andExpect(status().isUnauthorized)
    }
}
