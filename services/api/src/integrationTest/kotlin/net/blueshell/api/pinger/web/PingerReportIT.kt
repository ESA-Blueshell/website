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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant

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
        deviceId: String = "device-1",
    ) = """{"deviceId":"$deviceId","online":$online,"pps":$pps,"sent":$sent,"errors":$errors}"""

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
        assertThat(liveStore.aggregate("member:${member.id}")?.pps).isEqualTo(128)
        assertThat(liveStore.aggregate("member:${member.id}")?.online).isTrue()
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
        assertThat(liveStore.aggregate("member:${member.id}")).isNotNull()
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

    // The share service caches the live set for a second, and the cache outlives a test: step the
    // clock past every earlier test's read so this one sees its own rows.
    private fun freshShares() {
        shareClock = maxOf(shareClock, clock.instant()).plus(Duration.ofSeconds(2))
        clock.set(shareClock)
    }

    @Test
    fun `a member's device is told its slice of the paint, weighed against the online devices`() {
        val member = createUserWithRole(Role.MEMBER)
        liveStore.touch("sitecie", "replica-a", online = true, pps = 3_000, at = clock.instant())
        freshShares()

        mvc
            .perform(get("/pinger/report/share").param("deviceId", "laptop").header("Authorization", "Bearer ${memberBearer(member)}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.from").value(0.0))
            .andExpect(jsonPath("$.to").value(0.25))
            .andExpect(jsonPath("$.devices").value(2))
    }

    @Test
    fun `SiteCie's service token is told its slice`() {
        freshShares()

        mvc
            .perform(get("/pinger/report/share").param("deviceId", "replica-a").header("X-Pinger-Service-Token", serviceToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.from").value(0.0))
            .andExpect(jsonPath("$.to").value(1.0))
            .andExpect(jsonPath("$.devices").value(1))
    }

    @Test
    fun `a share request with no credentials is refused`() {
        mvc
            .perform(get("/pinger/report/share").param("deviceId", "laptop"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a share request without a device id is refused`() {
        mvc
            .perform(get("/pinger/report/share").header("X-Pinger-Service-Token", serviceToken))
            .andExpect(status().isBadRequest)
        mvc
            .perform(get("/pinger/report/share").param("deviceId", " ").header("X-Pinger-Service-Token", serviceToken))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `the share stream opens with the device's slice as its first event`() {
        val member = createUserWithRole(Role.MEMBER)
        freshShares()

        mvc
            .perform(
                get("/pinger/report/share/stream")
                    .param("deviceId", "laptop")
                    .header("Authorization", "Bearer ${memberBearer(member)}")
                    .accept(MediaType.TEXT_EVENT_STREAM),
            ).andExpect(request().asyncStarted())
            .andExpect(content().string("data:{\"from\":0.0,\"to\":1.0,\"devices\":1}\n\n"))
    }

    @Test
    fun `the share stream refuses a request with no credentials`() {
        mvc
            .perform(get("/pinger/report/share/stream").param("deviceId", "laptop").accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isUnauthorized)
    }

    private companion object {
        var shareClock: Instant = Instant.EPOCH
    }
}
