package net.blueshell.api.security

import net.blueshell.api.factory.auth.web.request.AuthRequestFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

/**
 * A sign-out has to be seen by the replica that takes the next request, which is why the record
 * lives in Valkey rather than in the pod that performed it.
 */
@SpringBootTest
class JwtRevocationIT : UserTestSupport() {
    @Autowired
    private lateinit var authRequestFactory: AuthRequestFactory

    @Autowired
    private lateinit var redis: StringRedisTemplate

    @Autowired
    private lateinit var jwtTokenUtil: JwtTokenUtil

    private fun signIn(username: String): String {
        val result =
            mvc
                .perform(
                    post("/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authRequestFactory.authenticatePayload(username, "Password123!")),
                ).andExpect(status().isOk)
                .andReturn()
        return mapper.readTree(result.response.contentAsByteArray).path("token").asText()
    }

    /**
     * Said before anything else is asked of it: the store degrades on an unreachable Valkey, and
     * without this every assertion below would read "not revoked" and pass on a list nothing was
     * ever written to. This one throws instead.
     */
    @BeforeEach
    fun valkeyAnswers() {
        val probe = "${ValkeyRevokedJtiStore.KEY_PREFIX}probe"
        redis.opsForValue().set(probe, "1", Duration.ofSeconds(30))
        assertThat(redis.hasKey(probe)).describedAs("a valkey to write the denylist to").isTrue()
        redis.delete(probe)
    }

    @Test
    fun `a sign-out is written to valkey, where another replica reads it`() {
        val user = createUserWithRole(Role.MEMBER)
        val token = signIn(user.username)
        val jti = jwtTokenUtil.parseAndValidate(token).jti!!

        mvc
            .perform(post("/auth/logout").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        // A service of its own, holding nothing: the replica that did not perform the sign-out.
        val anotherReplica = JwtRevocationService("", Duration.ofDays(30), ValkeyRevokedJtiStore(redis))
        assertThat(anotherReplica.isRevoked(jti)).isTrue()
    }

    @Test
    fun `the record is given up when the token it names would have expired anyway`() {
        val user = createUserWithRole(Role.MEMBER)
        val token = signIn(user.username)
        val jti = jwtTokenUtil.parseAndValidate(token).jti!!

        mvc
            .perform(post("/auth/logout").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        val ttl = redis.getExpire("${ValkeyRevokedJtiStore.KEY_PREFIX}$jti")
        assertThat(ttl)
            .describedAs("what was left of the token, not forever")
            .isGreaterThan(0)
            .isLessThanOrEqualTo(Duration.ofDays(30).seconds)
    }

    @Test
    fun `a signed-out token is refused on the next request`() {
        val user = createUserWithRole(Role.MEMBER)
        val token = signIn(user.username)

        mvc
            .perform(get("/users/${user.id}").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)

        mvc
            .perform(post("/auth/logout").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        mvc
            .perform(get("/users/${user.id}").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)
    }
}
