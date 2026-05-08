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
            // The default SecurityConfig's filter chain doesn't apply on
            // this matcher — without re-adding JwtAuthFilter here, a
            // logged-in user's BSH_AUTH cookie isn't read on the
            // /oauth2/* endpoints and Spring Authorization Server treats
            // them as anonymous. The filter is idempotent: if the cookie
            // is missing or invalid, it's a no-op and the entry point
            // below kicks in.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            // Gate non-admin users out of admin-only clients (vault,
            // headlamp) at /oauth2/authorize. Doing this here rather
            // than from the OAuth2TokenCustomizer means Spring SAS sees
            // a clean 403 *before* an authorization code is issued —
            // throwing from the customizer at /oauth2/token bubbles up
            // as `invalid_grant`, which Vault's UI surfaces as
            // "callback did not supply all of the required parameters".
            .addFilterAfter(downstreamClientAuthorizationFilter(), JwtAuthFilter::class.java)
            .exceptionHandling {
                it.authenticationEntryPoint(loginRedirectEntryPoint())
            }
            .csrf { it.ignoringRequestMatchers(authServerConfigurer.endpointsMatcher) }

        return http.build()
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

    // In-memory rather than JDBC: JdbcOAuth2AuthorizationService roundtrips
    // OAuth2Authorization (incl. the principal Authentication) through Jackson
    // to/from `oauth2_authorization.attributes` LONGTEXT. Our `UserPrincipal`
    // (a Kotlin data class implementing UserDetails) isn't registered in
    // SecurityJackson2Modules' allowlist, so the row written at /oauth2/authorize
    // can't be deserialised back at /oauth2/token — Spring SAS treats the auth
    // code as not-found and emits `invalid_grant` (which Vault's UI surfaces
    // as "callback did not supply all required parameters", and Headlamp as
    // "oauth2: invalid_grant"). The api Deployment is replicas=1 and auth
    // codes are short-lived (~5 min), so an in-process store has no
    // operational downside today; if we ever scale out, the right fix is to
    // configure JdbcOAuth2AuthorizationService with a custom ObjectMapper that
    // knows how to serialise UserPrincipal.
    @Bean
    fun authorizationService(): OAuth2AuthorizationService {
        return InMemoryOAuth2AuthorizationService()
    }

    @Bean
    fun authorizationConsentService(): OAuth2AuthorizationConsentService {
        return InMemoryOAuth2AuthorizationConsentService()
    }
}
