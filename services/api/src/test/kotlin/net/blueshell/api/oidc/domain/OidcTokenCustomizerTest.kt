package net.blueshell.api.oidc.domain

import net.blueshell.api.security.SignInDetails
import net.blueshell.api.security.SignIns
import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext

class OidcTokenCustomizerTest {
    private val loader = mock<OidcUserLoader>()
    private val signIns = mock<SignIns>()
    private val customizer = OidcTokenCustomizer(loader, signIns).tokenCustomizer()
    private val client = RegisteredClients().registeredClientRepository("secret").findByClientId("headlamp")!!

    private fun context(
        tokenType: OAuth2TokenType,
        grant: AuthorizationGrantType,
        details: SignInDetails?,
    ): Pair<JwtEncodingContext, JwtClaimsSet.Builder> {
        val claims = JwtClaimsSet.builder()
        val principal = UsernamePasswordAuthenticationToken("alice", null).also { it.details = details }
        val context =
            JwtEncodingContext
                .with(JwsHeader.with(SignatureAlgorithm.RS256), claims)
                .registeredClient(client)
                .principal(principal)
                .tokenType(tokenType)
                .authorizationGrantType(grant)
                .authorizedScopes(setOf(OidcScopes.OPENID, OidcScopes.PROFILE))
                .build()
        return context to claims
    }

    private fun admin() =
        whenever(loader.load("alice")).thenReturn(OidcUserData(7, "alice", "a@example.com", "Alice", "Doe", setOf(Role.ADMIN)))

    @Test
    fun `a token says how its sign-in was proved`() {
        admin()
        val (access, accessClaims) = context(
            OAuth2TokenType.ACCESS_TOKEN,
            AuthorizationGrantType.AUTHORIZATION_CODE,
            SignInDetails("s", setOf("pwd", "otp")),
        )
        val (id, idClaims) = context(
            OAuth2TokenType("id_token"),
            AuthorizationGrantType.AUTHORIZATION_CODE,
            SignInDetails("s", setOf("pwd", "otp")),
        )

        customizer.customize(access)
        customizer.customize(id)

        assertThat(accessClaims.build().getClaimAsStringList("amr")).containsExactly("otp", "pwd")
        assertThat(idClaims.build().getClaimAsStringList("amr")).containsExactly("otp", "pwd")
    }

    @Test
    fun `a token from something other than a sign-in names no methods`() {
        admin()
        val (access, claims) = context(OAuth2TokenType.ACCESS_TOKEN, AuthorizationGrantType.AUTHORIZATION_CODE, null)

        customizer.customize(access)

        assertThat(claims.build().claims).doesNotContainKey("amr")
    }

    @Test
    fun `a refresh token renews only while the sign-in behind it lives`() {
        admin()
        whenever(signIns.isLive("live")).thenReturn(true)

        customizer.customize(
            context(OAuth2TokenType.ACCESS_TOKEN, AuthorizationGrantType.REFRESH_TOKEN, SignInDetails("live", setOf("pwd"))).first,
        )
        assertThrows<OAuth2AuthenticationException> {
            customizer.customize(
                context(OAuth2TokenType.ACCESS_TOKEN, AuthorizationGrantType.REFRESH_TOKEN, SignInDetails("gone", setOf("pwd"))).first,
            )
        }
        assertThrows<OAuth2AuthenticationException> {
            customizer.customize(context(OAuth2TokenType.ACCESS_TOKEN, AuthorizationGrantType.REFRESH_TOKEN, null).first)
        }
    }
}
