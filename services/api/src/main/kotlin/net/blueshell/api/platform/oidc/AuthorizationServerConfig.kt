package net.blueshell.api.platform.oidc

import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import net.blueshell.api.infrastructure.security.JwtAuthFilter
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Configuration
class AuthorizationServerConfig {

    @Bean
    @Order(1)
    fun authorizationServerFilterChain(
        http: HttpSecurity,
        jwkSource: JWKSource<SecurityContext>,
        tokenCustomizer: OAuth2TokenCustomizer<JwtEncodingContext>,
        jwtAuthFilter: JwtAuthFilter,
    ): SecurityFilterChain {
        val authServerConfigurer = OAuth2AuthorizationServerConfigurer()

        authServerConfigurer
            .oidc(Customizer.withDefaults())

        http
            .securityMatcher(authServerConfigurer.endpointsMatcher)
            .with(authServerConfigurer) {}
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            // The default SecurityConfig's filter chain doesn't apply on
            // this matcher — without re-adding JwtAuthFilter here, a
            // logged-in user's BSH_AUTH cookie isn't read on the
            // /oauth2/* endpoints and Spring Authorization Server treats
            // them as anonymous. The filter is idempotent: if the cookie
            // is missing or invalid, it's a no-op and the entry point
            // below kicks in.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling {
                it.authenticationEntryPoint(loginRedirectEntryPoint())
            }
            .csrf { it.ignoringRequestMatchers(authServerConfigurer.endpointsMatcher) }

        return http.build()
    }

    /**
     * Sends anonymous callers to the SPA's /login page with the original
     * api URL preserved as a `redirect` query param. After the user
     * signs in, the SPA's Login.vue does a real browser navigation to
     * that redirect URL (Login.vue's `isOffSpa` check matches `/api/`),
     * which re-hits this filter chain — now authenticated — and the
     * OIDC flow resumes.
     *
     * The api receives `/oauth2/authorize` (Traefik strips `/api`
     * before forwarding); we prepend `/api` back so the redirect lands
     * at the public URL.
     */
    private fun loginRedirectEntryPoint(): AuthenticationEntryPoint =
        AuthenticationEntryPoint { request, response, _ ->
            val publicPath = "/api${request.requestURI}"
            val query = request.queryString
            val originalUrl = if (!query.isNullOrEmpty()) "$publicPath?$query" else publicPath
            val encoded = URLEncoder.encode(originalUrl, StandardCharsets.UTF_8)
            response.sendRedirect("/login?redirect=$encoded")
        }

    @Bean
    @Order(2)
    fun loginFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/login", "/login/**")
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .formLogin(Customizer.withDefaults())
        return http.build()
    }

    @Bean
    fun authorizationServerSettings(
        @Value("\${auth.issuer:https://v2.esa-blueshell.nl/api}") issuer: String,
    ): AuthorizationServerSettings {
        return AuthorizationServerSettings.builder()
            .issuer(issuer)
            .build()
    }

    @Bean
    fun authorizationService(
        jdbcTemplate: JdbcTemplate,
        registeredClientRepository: RegisteredClientRepository,
    ): OAuth2AuthorizationService {
        return JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository)
    }

    @Bean
    fun authorizationConsentService(
        jdbcTemplate: JdbcTemplate,
        registeredClientRepository: RegisteredClientRepository,
    ): OAuth2AuthorizationConsentService {
        return JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository)
    }
}
