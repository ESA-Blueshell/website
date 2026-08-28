package net.blueshell.api.oidc.domain

import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.blueshell.api.security.JwtAuthFilter
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
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.web.filter.OncePerRequestFilter
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
            // Must run before Spring SAS's OAuth2AuthorizationCodeRequestValidatingFilter
            // (positioned before AbstractPreAuthenticatedProcessingFilter), otherwise the
            // validating filter snapshots SecurityContext while it's still anonymous and
            // the endpoint filter later issues `?error=invalid_request&error_description=
            // OAuth 2.0 Parameter: principal` to the client's redirect_uri.
            .addFilterAfter(jwtAuthFilter, SecurityContextHolderFilter::class.java)
            .addFilterAfter(downstreamClientAuthorizationFilter(), JwtAuthFilter::class.java)
            .exceptionHandling {
                it.authenticationEntryPoint(loginRedirectEntryPoint())
            }
            .csrf { it.ignoringRequestMatchers(authServerConfigurer.endpointsMatcher) }

        return http.build()
    }

    // Every client registered with this server is an admin tool (see RegisteredClients), so
    // authorization requests are admin-only across the board. The gate deliberately does not
    // branch on the request's own `client_id`: letting that parameter decide whether the check
    // runs would hand an attacker the switch that turns the check off (CWE-807).
    private fun downstreamClientAuthorizationFilter(): OncePerRequestFilter =
        object : OncePerRequestFilter() {
            override fun shouldNotFilter(request: HttpServletRequest): Boolean =
                request.requestURI != "/oauth2/authorize"

            override fun doFilterInternal(
                request: HttpServletRequest,
                response: HttpServletResponse,
                filterChain: FilterChain,
            ) {
                val auth = SecurityContextHolder.getContext().authentication
                if (auth == null || auth is AnonymousAuthenticationToken || !auth.isAuthenticated) {
                    // Unauthenticated — let the entry point redirect to /login.
                    filterChain.doFilter(request, response)
                    return
                }
                val isAdmin = auth.authorities.any { it.authority == Role.ADMIN.reprString }
                if (!isAdmin) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin access required")
                    return
                }
                filterChain.doFilter(request, response)
            }
        }

    private fun loginRedirectEntryPoint(): AuthenticationEntryPoint =
        AuthenticationEntryPoint { request, response, _ ->
            val target = loginRedirectTarget(request.requestURI, request.queryString)
            response.sendRedirect("/login?redirect=${URLEncoder.encode(target, StandardCharsets.UTF_8)}")
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
        @Value("\${auth.issuer:https://esa-blueshell.nl/api}") issuer: String,
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

/** Where a member lands after logging in when the page they wanted is not safe to return to. */
internal const val DEFAULT_POST_LOGIN_PATH = "/"

/**
 * The value that goes in `/login?redirect=…` for a request that arrived unauthenticated.
 *
 * Traefik strips `/api` before forwarding, so it is re-added: the redirect has to name the
 * public URL for the frontend's off-SPA navigation to re-enter this chain.
 *
 * The frontend navigates to whatever this returns, so it must be a path on this site. A value
 * that is protocol-relative (`//host`, `/\host`) or not rooted at `/` would leave the origin,
 * and is replaced by [DEFAULT_POST_LOGIN_PATH] rather than passed through (CWE-601).
 */
internal fun loginRedirectTarget(requestUri: String, queryString: String?): String {
    if (!isSameOriginPath(requestUri)) return DEFAULT_POST_LOGIN_PATH
    val publicPath = "/api$requestUri"
    return if (!queryString.isNullOrEmpty()) "$publicPath?$queryString" else publicPath
}

private fun isSameOriginPath(value: String): Boolean =
    value.startsWith("/") && !value.startsWith("//") && !value.startsWith("/\\")
