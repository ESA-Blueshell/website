package net.blueshell.api.pinger.web

import net.blueshell.api.pinger.persistence.PingerContributionRepository
import net.blueshell.api.pinger.persistence.PingerLiveStore
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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

    @Autowired
    private lateinit var contributions: PingerContributionRepository

    @Autowired
    private lateinit var liveStore: PingerLiveStore

    @Autowired
    private lateinit var redis: StringRedisTemplate

    @Value($$"${pinger.report.service-token}")
    private lateinit var serviceToken: String

    private fun reportBody(
        online: Boolean,
        pps: Int,
        sent: Long,
        errors: Int = 0,
    ) = """{"online":$online,"pps":$pps,"sent":$sent,"errors":$errors}"""

    private fun totalSentOf(identity: String): Long = contributions.findAll().single { it.identity == identity }.totalSent

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

    @Test
    fun `a member report accrues the durable total and refreshes the live row`() {
        val member = createUserWithRole(Role.MEMBER)

        mvc
            .perform(
                post("/pinger/report")
                    .header("Authorization", "Bearer ${memberBearer(member)}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reportBody(online = true, pps = 128, sent = 500)),
            ).andExpect(status().isNoContent)

        assertThat(totalSentOf("member:${member.id}")).isEqualTo(500)
        assertThat(liveStore.find("member:${member.id}")?.pps).isEqualTo(128)
        assertThat(liveStore.find("member:${member.id}")?.online).isTrue()
    }

    @Test
    fun `the durable total survives a Valkey wipe and does not double-count`() {
        val member = createUserWithRole(Role.MEMBER)

        fun report(sent: Long) =
            mvc
                .perform(
                    post("/pinger/report")
                        .header("Authorization", "Bearer ${memberBearer(member)}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody(online = true, pps = 64, sent = sent)),
                ).andExpect(status().isNoContent)

        report(500)
        redis.execute { connection -> connection.serverCommands().flushDb() }
        report(800)

        // The last counter lives in the durable row, so the continuing session adds only 300.
        assertThat(totalSentOf("member:${member.id}")).isEqualTo(800)
        assertThat(liveStore.find("member:${member.id}")).isNotNull()
    }

    @Test
    fun `a restarted session reporting a lower counter accrues without double-counting`() {
        val member = createUserWithRole(Role.MEMBER)

        fun report(sent: Long) =
            mvc
                .perform(
                    post("/pinger/report")
                        .header("Authorization", "Bearer ${memberBearer(member)}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody(online = true, pps = 64, sent = sent)),
                ).andExpect(status().isNoContent)

        report(1_000)
        report(50)

        assertThat(totalSentOf("member:${member.id}")).isEqualTo(1_050)
    }

    @Test
    fun `a SiteCie report accrues under SiteCie and not any member`() {
        mvc
            .perform(
                post("/pinger/report")
                    .header("X-Pinger-Service-Token", serviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reportBody(online = true, pps = 200, sent = 420)),
            ).andExpect(status().isNoContent)

        val row = contributions.findAll().single { it.identity == "sitecie" }
        assertThat(row.totalSent).isEqualTo(420)
        assertThat(row.memberId).isNull()
    }
}
