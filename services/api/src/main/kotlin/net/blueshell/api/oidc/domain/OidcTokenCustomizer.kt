package net.blueshell.api.oidc.domain

import net.blueshell.api.shared.enums.Role
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer

@Configuration
class OidcTokenCustomizer(private val userLoader: OidcUserLoader) {

    @Bean
    fun tokenCustomizer(): OAuth2TokenCustomizer<JwtEncodingContext> {
        return OAuth2TokenCustomizer { context ->
            val principal = context.getPrincipal<Authentication>().name ?: return@OAuth2TokenCustomizer
            val user = userLoader.load(principal) ?: return@OAuth2TokenCustomizer

            val roles = user.roles.map { it.name }
            val subject = user.userId.toString()
            val clientId = context.registeredClient.clientId

            when {
                context.tokenType == OAuth2TokenType.ACCESS_TOKEN -> {
                    context.claims.subject(subject)
                    context.claims.claim("aud", listOf(clientId))
                    context.claims.claim("roles", roles)
                    context.claims.claim("username", user.username)
                    context.claims.claim("preferred_username", user.username)
                    context.claims.claim("email", user.email)
                }

                context.tokenType.value == OidcParameterNames.ID_TOKEN -> {
                    context.claims.subject(subject)
                    context.claims.claim("roles", roles)

                    if (OidcScopes.PROFILE in context.authorizedScopes) {
                        context.claims.claim("preferred_username", user.username)
                        context.claims.claim("name", "${user.firstName} ${user.lastName}".trim())
                    }

                    if (OidcScopes.EMAIL in context.authorizedScopes) {
                        context.claims.claim("email", user.email)
                    }

                    if ("groups" in context.authorizedScopes) {
                        context.claims.claim("groups", user.roles.toGroups())
                    }
                }
            }
        }
    }

    private fun Set<Role>.toGroups(): List<String> = buildList {
        if (this@toGroups.any { it.matchesRole(Role.ADMIN) }) add("k8s-admin")
        if (this@toGroups.any { it.matchesRole(Role.MEMBER) }) add("member")
    }
}
