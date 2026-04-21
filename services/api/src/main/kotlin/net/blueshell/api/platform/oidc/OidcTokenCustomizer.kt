package net.blueshell.api.platform.oidc

import net.blueshell.api.shared.enums.Role
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer

private val ADMIN_ONLY_CLIENTS = setOf("headlamp", "vault")

@Configuration
class OidcTokenCustomizer(private val userLoader: OidcUserLoader) {

    @Bean
    fun tokenCustomizer(): OAuth2TokenCustomizer<JwtEncodingContext> {
        return OAuth2TokenCustomizer { context ->
            val principal = context.getPrincipal<Any>()?.name ?: return@OAuth2TokenCustomizer
            val user = userLoader.load(principal) ?: return@OAuth2TokenCustomizer

            val clientId = context.registeredClient.clientId
            if (clientId in ADMIN_ONLY_CLIENTS && !user.roles.any { it.matchesRole(Role.ADMIN) }) {
                throw OAuth2AuthorizationCodeRequestAuthenticationException(
                    OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, "Admin access required for $clientId", null),
                    null,
                )
            }

            context.claims.apply {
                claim("preferred_username", user.username)
                claim("email", user.email)
                claim("name", "${user.firstName} ${user.lastName}".trim())
                claim("roles", user.roles.map { it.name })
                claim("groups", user.roles.toGroups())
            }
        }
    }

    private fun Set<Role>.toGroups(): List<String> = buildList {
        if (any { it.matchesRole(Role.ADMIN) }) add("k8s-admin")
        if (any { it.matchesRole(Role.MEMBER) }) add("member")
    }
}
