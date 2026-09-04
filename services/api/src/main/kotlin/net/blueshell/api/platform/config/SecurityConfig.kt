package net.blueshell.api.platform.config

import net.blueshell.api.security.CookieFlags
import net.blueshell.api.shared.web.SignupHeaders
import net.blueshell.api.security.JwtAuthFilter
import net.blueshell.api.security.JwtAuthenticationEntryPoint
import net.blueshell.api.security.PublicAuthRateLimitFilter
import net.blueshell.api.security.permission.CompositePermissionEvaluator
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
    private val requireHttps: Boolean,
    // The CSRF cookie reads its own property rather than the auth cookie's: both say
    // None in production, but for unrelated reasons -- the auth cookie so it reaches
    // the forwardAuth subdomains, this one only because it is read cross-origin.
    @param:Value($$"${security.csrf-cookie.same-site:None}")
    private val csrfCookieSameSite: String
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
        cfg.allowedHeaders = mutableListOf(
            "Authorization",
            "Content-Type",
            "X-Guest-Access-Token",
            SignupHeaders.SIGNUP_TOKEN,
            "X-XSRF-TOKEN",
        )
        cfg.exposedHeaders = mutableListOf("X-Guest-Access-Token")
        cfg.allowCredentials = true
        cfg.maxAge = 3600

        val src = UrlBasedCorsConfigurationSource()
        src.registerCorsConfiguration("/**", cfg)
        return src
    }

    @Bean
    fun csrfTokenRepository(): CookieCsrfTokenRepository {
        val sameSite = CookieFlags.sameSite(csrfCookieSameSite)
        val tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
        tokenRepository.setCookieCustomizer { cookie ->
            cookie.path("/")
            cookie.sameSite(sameSite)
            cookie.secure(CookieFlags.secure(requireHttps, sameSite))
        }
        return tokenRepository
    }

    @Bean
    @Order(0)
    fun actuatorChain(
        http: HttpSecurity,
        csrfTokenRepository: CookieCsrfTokenRepository
    ): SecurityFilterChain {
        http.securityMatcher(EndpointRequest.toAnyEndpoint())
            .csrf { it.csrfTokenRepository(csrfTokenRepository) }
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
                "/signup",
                "/signup/**",
                "/users/guest",
                "/events/*/signups"
            ).permitAll()
            auth.requestMatchers(HttpMethod.PUT, "/events/*/signups").permitAll()
            auth.requestMatchers(HttpMethod.PATCH, "/signup/**").permitAll()
            auth.requestMatchers(
                HttpMethod.GET,
                "/csrf",
                // Read back on the signup token, which is the credential; named exactly
                // rather than as /signup/** so a later read cannot join it by accident.
                "/signup/session",
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
                // Posters, banners and roster icons: the images the public pages draw.
                "/files/public/**",
                // The collection and one game: "/esports/games/*" matches the second only,
                // so the list of games needs saying separately.
                "/esports/games",
                "/esports/games/*",
                "/esports/seasons",
                // A season's band. It answers everybody and answers them differently: a
                // visitor gets the games with a team in them, the board also gets the ones
                // entered with nobody fielded yet.
                "/esports/seasons/*/games",
                "/esports/teams",
                "/committees/**",
                "/contributionPeriods/current",
                "/health",
                // The association's own numbers, which an anonymous caller reads.
                "/statistics/association",
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
