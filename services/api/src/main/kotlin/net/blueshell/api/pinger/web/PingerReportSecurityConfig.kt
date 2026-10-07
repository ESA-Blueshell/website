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
    @Bean
    @Order(2)
    fun pingerReportFilterChain(
        http: HttpSecurity,
        jwkSource: JWKSource<SecurityContext>,
        @Value($$"${pinger.report.service-token:}") serviceToken: String,
    ): SecurityFilterChain {
        http
            .securityMatcher("/pinger/report/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().hasAnyAuthority(Role.MEMBER.reprString, "SITECIE") }
            .oauth2ResourceServer { it.jwt(jwtCustomizer(jwkSource)) }
            .addFilterBefore(
                PingerServiceTokenAuthenticationFilter(serviceToken),
                BearerTokenAuthenticationFilter::class.java,
            )
        return http.build()
    }

    internal fun jwtCustomizer(
        jwkSource: JWKSource<SecurityContext>,
    ): Customizer<OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer> =
        Customizer { jwt ->
            jwt.decoder(NimbusJwtDecoder.withJwkSource(jwkSource).build())
            jwt.jwtAuthenticationConverter(memberTokenConverter())
        }

    internal fun memberTokenConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter(PingerTokenRoleAuthorities())
        return converter
    }
}
