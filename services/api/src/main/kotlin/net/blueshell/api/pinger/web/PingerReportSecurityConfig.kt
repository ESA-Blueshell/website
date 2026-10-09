package net.blueshell.api.pinger.web

import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain

/**
 * The one chain that accepts a bearer credential, scoped to the `/pinger/report` paths and
 * nothing else.
 *
 * It narrowly amends the cookie-only rule (api ADR-030, ADR-035): a member reaches it with the
 * access token their `pinger-app` sign-in minted, and the SiteCie painter with its service token.
 * The main site chain stays cookie-only, so the bearer this chain honours buys nothing anywhere
 * else. The chain holds no session and carries no CSRF token, because neither caller is a browser.
 */
@Configuration
class PingerReportSecurityConfig {
    // Order -1 keeps this chain distinct from the login chain (also order 2) and ahead of the
    // cookie catch-all; its /pinger/report matcher is specific, so running first shadows nothing.
    @Bean
    @Order(-1)
    fun pingerReportFilterChain(
        http: HttpSecurity,
        jwkSource: JWKSource<SecurityContext>,
        @Value($$"${pinger.report.service-token:}") serviceToken: String,
        @Value($$"${auth.issuer:https://esa-blueshell.nl/api}") issuer: String,
    ): SecurityFilterChain {
        http
            .securityMatcher("/pinger/report/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().hasAnyAuthority(Role.MEMBER.reprString, "SITECIE") }
            .oauth2ResourceServer { it.jwt(jwtCustomizer(jwkSource, issuer)) }
            .addFilterBefore(
                PingerServiceTokenAuthenticationFilter(serviceToken),
                BearerTokenAuthenticationFilter::class.java,
            )
        return http.build()
    }

    internal fun jwtCustomizer(
        jwkSource: JWKSource<SecurityContext>,
        issuer: String,
    ): Customizer<OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer> =
        Customizer { jwt ->
            jwt.decoder(validatedDecoder(jwkSource, issuer))
            jwt.jwtAuthenticationConverter(memberTokenConverter())
        }

    // Signature validation alone would accept any token this auth server signed, for any client.
    // Pin the issuer and require this client's audience, so only a pinger-app bearer is honoured.
    private fun validatedDecoder(
        jwkSource: JWKSource<SecurityContext>,
        issuer: String,
    ): NimbusJwtDecoder {
        val decoder = NimbusJwtDecoder.withJwkSource(jwkSource).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(JwtValidators.createDefault(), JwtIssuerValidator(issuer), audienceValidator()),
        )
        return decoder
    }

    internal fun audienceValidator(): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt ->
            if (CLIENT_ID in jwt.audience.orEmpty()) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "The token is not for this client.", null))
            }
        }

    internal fun memberTokenConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter(PingerTokenRoleAuthorities())
        return converter
    }

    private companion object {
        const val CLIENT_ID = "pinger-app"
    }
}
