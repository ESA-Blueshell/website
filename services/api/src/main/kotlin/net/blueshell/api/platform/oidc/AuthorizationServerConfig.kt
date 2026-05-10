package net.blueshell.api.platform.oidc

import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.blueshell.api.infrastructure.security.JwtAuthFilter
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val ADMIN_ONLY_CLIENTS = setOf("headlamp", "vault")

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
            // Re-apply on this matcher so BSH_AUTH cookies are read on /oauth2/* endpoints.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            // Spring SAS 7 turns "anonymous principal at /oauth2/authorize"
            // into a 302 to redirect_uri with ?error=invalid_request — the
            // AuthenticationEntryPoint never fires. Pre-empt that with our
            // own /login redirect so the SAS 1.x UX (off-SPA re-entry)
            // still works.
            .addFilterAfter(anonymousAuthorizeLoginRedirect(), JwtAuthFilter::class.java)
            .addFilterAfter(downstreamClientAuthorizationFilter(), JwtAuthFilter::class.java)
            .exceptionHandling {
                it.authenticationEntryPoint(loginRedirectEntryPoint())
            }
            .csrf { it.ignoringRequestMatchers(authServerConfigurer.endpointsMatcher) }

        return http.build()
    }

    private fun anonymousAuthorizeLoginRedirect(): OncePerRequestFilter =
        object : OncePerRequestFilter() {
            override fun shouldNotFilter(request: HttpServletRequest): Boolean =
                request.requestURI != "/oauth2/authorize"

            override fun doFilterInternal(
                request: HttpServletRequest,
                response: HttpServletResponse,
                filterChain: FilterChain,
            ) {
                val auth = SecurityContextHolder.getContext().authentication
                if (auth != null && auth !is AnonymousAuthenticationToken && auth.isAuthenticated) {
                    filterChain.doFilter(request, response)
                    return
                }
                val publicPath = "/api${request.requestURI}"
                val query = request.queryString
                val originalUrl = if (!query.isNullOrEmpty()) "$publicPath?$query" else publicPath
                val encoded = URLEncoder.encode(originalUrl, StandardCharsets.UTF_8)
                response.sendRedirect("/login?redirect=$encoded")
            }
        }

    private fun downstreamClientAuthorizationFilter(): OncePerRequestFilter =
        object : OncePerRequestFilter() {
            override fun shouldNotFilter(request: HttpServletRequest): Boolean =
                request.requestURI != "/oauth2/authorize"

            override fun doFilterInternal(
                request: HttpServletRequest,
                response: HttpServletResponse,
                filterChain: FilterChain,
            ) {
                val clientId = request.getParameter("client_id")
                if (clientId == null || clientId !in ADMIN_ONLY_CLIENTS) {
                    filterChain.doFilter(request, response)
                    return
                }
                val auth = SecurityContextHolder.getContext().authentication
                if (auth == null || auth is AnonymousAuthenticationToken || !auth.isAuthenticated) {
                    // Unauthenticated — let the entry point redirect to /login.
                    filterChain.doFilter(request, response)
                    return
                }
                val isAdmin = auth.authorities.any { it.authority == Role.ADMIN.reprString }
                if (!isAdmin) {
                    response.sendError(
                        HttpServletResponse.SC_FORBIDDEN,
                        "Admin access required for $clientId",
                    )
                    return
                }
                filterChain.doFilter(request, response)
            }
        }

    // Traefik strips `/api` before forwarding, so re-add it so the redirect
    // lands at the public URL and the SPA's off-SPA navigation re-enters this chain.
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

    // In-memory rather than JDBC: UserPrincipal isn't in the Jackson allowlist
    // SecurityJackson2Modules ships, so JdbcOAuth2AuthorizationService can't
    // round-trip the principal. Replicas=1 means in-memory is fine; revisit
    // if/when we scale out.
    @Bean
    fun authorizationService(): OAuth2AuthorizationService {
        return InMemoryOAuth2AuthorizationService()
    }

    @Bean
    fun authorizationConsentService(): OAuth2AuthorizationConsentService {
        return InMemoryOAuth2AuthorizationConsentService()
    }
}
