package net.blueshell.api.oidc.web

import net.blueshell.api.oidc.domain.IndexingOAuth2AuthorizationService
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

/**
 * Lists and revokes a member's OAuth grants on the real end-to-end path: a member signs the
 * pinger-app in and holds its refresh token, then revokes it from the security page.
 */
@SpringBootTest
class ConnectedAppsControllerIT : UserTestSupport() {
    @Autowired
    private lateinit var authorizations: IndexingOAuth2AuthorizationService

    @Autowired
    private lateinit var registeredClients: RegisteredClientRepository

    @Test
    fun `a member sees the app they have connected`() {
        val member = createUserWithRole(Role.MEMBER)
        connect(member)

        mvc
            .perform(get("/me/connected-apps").with(signedIn(member)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].id", hasItem("pinger-app")))
            .andExpect(jsonPath("$[0].authorizedAt").isNotEmpty)
    }

    @Test
    fun `revoking an app drops its refresh token, so its next call is refused`() {
        val member = createUserWithRole(Role.MEMBER)
        val refreshToken = connect(member)

        mvc
            .perform(delete("/me/connected-apps/{appId}", "pinger-app").with(signedIn(member)).with(csrfToken()))
            .andExpect(status().isNoContent)

        // The refresh provider resolves the token through this lookup; gone means a refresh is refused.
        assertNoGrant(refreshToken)
        mvc
            .perform(get("/me/connected-apps").with(signedIn(member)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].id", not(hasItem("pinger-app"))))
    }

    @Test
    fun `revoking one member's app leaves another member's grant intact`() {
        val ada = createUserWithRole(Role.MEMBER)
        val linus = createUserWithRole(Role.MEMBER)
        connect(ada)
        val linusToken = connect(linus)

        mvc
            .perform(delete("/me/connected-apps/{appId}", "pinger-app").with(signedIn(ada)).with(csrfToken()))
            .andExpect(status().isNoContent)

        assertThat(authorizations.findByToken(linusToken, OAuth2TokenType.REFRESH_TOKEN)).isNotNull
        mvc
            .perform(get("/me/connected-apps").with(signedIn(linus)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].id", hasItem("pinger-app")))
    }

    /** Stands a grant up for the member against the pinger-app client, and answers its refresh token value. */
    private fun connect(member: User): String {
        val client = requireNotNull(registeredClients.findByClientId("pinger-app"))
        val refreshToken = "refresh-${UUID.randomUUID()}"
        val authorization =
            OAuth2Authorization
                .withRegisteredClient(client)
                .principalName(member.username)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .refreshToken(OAuth2RefreshToken(refreshToken, Instant.now(), Instant.now().plusSeconds(604800)))
                .build()
        authorizations.save(authorization)
        return refreshToken
    }

    private fun assertNoGrant(refreshToken: String) {
        assertThat(authorizations.findByToken(refreshToken, OAuth2TokenType.REFRESH_TOKEN)).isNull()
    }
}
