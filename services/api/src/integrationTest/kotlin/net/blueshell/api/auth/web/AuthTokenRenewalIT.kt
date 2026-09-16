package net.blueshell.api.auth.web

import net.blueshell.api.factory.auth.web.request.AuthRequestFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * A sign-in that is used does not run out. The threshold is dropped to nothing here so one request
 * stands for the day of use that would otherwise have to pass; what it proves is the mechanism,
 * not the interval. [AuthenticationControllerIT] holds the other half — at the configured
 * threshold a token minted moments ago is left alone.
 */
@SpringBootTest(properties = ["app.jwt.renew-after=0s"])
class AuthTokenRenewalIT : UserTestSupport() {

    @Autowired
    private lateinit var authRequestFactory: AuthRequestFactory

    @Test
    fun `a request made with an ageing cookie is answered with a fresh one`() {
        val user = createUserWithRole(Role.MEMBER)

        val signIn = mvc.perform(
            post("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(authRequestFactory.authenticatePayload(user.username, "Password123!"))
        )
            .andExpect(status().isOk)
            .andReturn()

        val issued = signIn.response.cookies.first { it.name == "BSH_AUTH" }

        val read = mvc.perform(get("/users/${user.id}").cookie(issued))
            .andExpect(status().isOk)
            .andReturn()

        val renewed = read.response.cookies.firstOrNull { it.name == "BSH_AUTH" }
        assertThat(renewed).describedAs("a renewed auth cookie on an ordinary read").isNotNull
        assertThat(renewed!!.value).isNotBlank().isNotEqualTo(issued.value)
        assertThat(renewed.maxAge)
            .describedAs("the full lifetime again, not what was left of the old one")
            .isEqualTo(java.time.Duration.ofDays(30).seconds.toInt())
        assertThat(renewed.isHttpOnly).isTrue()

        mvc.perform(get("/users/${user.id}").cookie(renewed))
            .andExpect(status().isOk)
    }

    @Test
    fun `signing out is not written over by a renewal`() {
        val user = createUserWithRole(Role.MEMBER)

        val signIn = mvc.perform(
            post("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(authRequestFactory.authenticatePayload(user.username, "Password123!"))
        )
            .andExpect(status().isOk)
            .andReturn()

        val issued = signIn.response.cookies.first { it.name == "BSH_AUTH" }

        val signOut = mvc.perform(post("/auth/logout").cookie(issued))
            .andExpect(status().isNoContent)
            .andReturn()

        val cleared = signOut.response.cookies.filter { it.name == "BSH_AUTH" }
        assertThat(cleared).hasSize(1)
        assertThat(cleared.single().maxAge).isZero()

        mvc.perform(get("/users/${user.id}").cookie(issued))
            .andExpect(status().isUnauthorized)
    }
}
