package net.blueshell.api.platform.config

import net.blueshell.api.infrastructure.security.JwtAuthFilter
import net.blueshell.api.infrastructure.security.JwtAuthenticationEntryPoint
import net.blueshell.api.infrastructure.security.PublicAuthRateLimitFilter
import net.blueshell.api.infrastructure.security.permission.CompositePermissionEvaluator
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.util.*

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityCorsProperties::class)
class SecurityConfig(
    private val authenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val jwtAuthFilter: JwtAuthFilter,
    private val publicAuthRateLimitFilterProvider: ObjectProvider<PublicAuthRateLimitFilter>,
    private val securityCorsProperties: SecurityCorsProperties,
    @param:Value($$"${security.openapi.public.enabled:false}")
    private val openApiPublicEnabled: Boolean,
    @param:Value($$"${app.security.require-https:true}")
    private val requireHttps: Boolean
) {
    @Bean
    fun authenticationManager(cfg: AuthenticationConfiguration): AuthenticationManager {
        return cfg.authenticationManager
    }

    @Bean
    fun roleHierarchy(): RoleHierarchy {
        val hierarchy = Arrays.stream(Role.entries.toTypedArray())
            .sorted { a: Role, b: Role -> b.authorities.size - a.authorities.size }
            .map { it.name }
            .reduce { a: String, b: String -> "$a > $b" }
            .orElse("")
        return RoleHierarchyImpl.fromHierarchy(hierarchy)
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val cfg = CorsConfiguration()
        cfg.allowedOrigins = securityCorsProperties.allowedOrigins
            .map { it.trim().removeSuffix("/") }
            .filter { it.isNotBlank() }
            .distinct()
            .toMutableList()
        cfg.allowedMethods = mutableListOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        cfg.allowedHeaders = mutableListOf("Authorization", "Content-Type", "X-Guest-Access-Token", "X-XSRF-TOKEN")
        cfg.exposedHeaders = mutableListOf("X-Guest-Access-Token")
        cfg.allowCredentials = true
        cfg.maxAge = 3600

        val src = UrlBasedCorsConfigurationSource()
        src.registerCorsConfiguration("/**", cfg)
        return src
    }

    @Bean
    fun csrfTokenRepository(): CookieCsrfTokenRepository {
        val tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
        tokenRepository.setCookieCustomizer { cookie ->
            cookie.path("/")
            cookie.sameSite("None")
            cookie.secure(true)
        }
        return tokenRepository
    }

    // Dedicated chain for Spring Boot actuator endpoints. Lives at @Order(0)
    // so it runs before the @Order(3) authChain that calls redirectToHttps —
    // kubelet probes speak plain HTTP, and a 302 to https from a permitAll
    // path would still fail the probe. CSRF disabled (probes have no token)
    // and anyRequest().permitAll() so in-cluster scrapers (kubelet,
    // Prometheus, Gatus) can reach health/prometheus without a JWT.
    //
    // CodeQL java/spring-disabled-csrf-protection: CSRF is intentionally disabled for this chain
    // only. Actuator endpoints are read-only health/metrics probes consumed by automated in-cluster
    // tooling (kubelet, Prometheus, Gatus) that cannot supply a CSRF token. State-changing
    // application endpoints are handled by authChain (@Order(3)), which uses
    // CookieCsrfTokenRepository with SameSite=None; Secure cookies — CSRF protection is fully
    // active there. Disabling CSRF here is safe: no session-authenticated state-changing
    // operations are exposed via actuator paths.
    @Bean
    @Order(0)
    @Suppress("codeql[java/spring-disabled-csrf-protection]")
    fun actuatorChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher(EndpointRequest.toAnyEndpoint())
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }

    @Bean
    @Order(3)
    fun authChain(
        http: HttpSecurity,
        csrfTokenRepository: CookieCsrfTokenRepository
    ): SecurityFilterChain {
        if (requireHttps) {
            http.redirectToHttps(Customizer.withDefaults())
            http.headers { headers ->
                headers.httpStrictTransportSecurity { hsts ->
                    hsts.includeSubDomains(true)
                    hsts.maxAgeInSeconds(31536000)
                }
            }
        }

        http.securityMatcher("/**")
            .csrf { it.csrfTokenRepository(csrfTokenRepository).ignoringRequestMatchers("/auth/logout") }
            // IF_REQUIRED (not STATELESS) so the SecurityContext JwtAuthFilter
            // saves is persisted to the Valkey-backed session, keeping the user
            // signed in after the JWT expires.
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        publicAuthRateLimitFilterProvider.ifAvailable { rateLimitFilter ->
            http.addFilterBefore(rateLimitFilter, JwtAuthFilter::class.java)
        }
        http.authorizeHttpRequests { auth ->
            auth.requestMatchers(
                HttpMethod.POST,
                "/auth",
                "/auth/logout",
                "/recovery/**",
                "/users",
                "/users/guest",
                "/events/*/signups"
            ).permitAll()
            auth.requestMatchers(HttpMethod.PUT, "/events/*/signups").permitAll()
            auth.requestMatchers(
                HttpMethod.GET,
                "/csrf",
                "/events/**",
                "/events/signups/byAccessToken",
                "/me/services",
                "/blogs",
                "/blogs/*",
                "/boards",
                "/boards/*",
                "/telemetry/*",
                "/committeeMembers/committees",
                "/contributionPeriods",
                "/download/**",
                "/committees/**",
                "/contributionPeriods/current",
                "/health",
                "/oauth2/forward-auth",
                "/track/email/**",
                "/actuator/health",
                "/actuator/health/**",
                "/actuator/prometheus",
                "/test-support/**",
            ).permitAll()

            if (openApiPublicEnabled) {
                auth.requestMatchers(
                    HttpMethod.GET,
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/swagger-ui",
                    "/swagger-ui/**"
                ).permitAll()
            }

            auth.requestMatchers(HttpMethod.DELETE, "/events/signups/*").permitAll()
            auth.requestMatchers("/error").permitAll()
            auth.anyRequest().authenticated()
        }
            .exceptionHandling { it.authenticationEntryPoint(authenticationEntryPoint) }
        return http.build()
    }

    @Bean
    fun methodSecurityExpressionHandler(
        evaluator: CompositePermissionEvaluator
    ): MethodSecurityExpressionHandler {
        val h = DefaultMethodSecurityExpressionHandler()
        h.setPermissionEvaluator(evaluator)
        return h
    }
}
