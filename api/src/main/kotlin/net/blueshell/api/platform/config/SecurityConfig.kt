package net.blueshell.api.platform.config

import net.blueshell.api.auth.security.JwtAuthFilter
import net.blueshell.api.auth.security.JwtAuthenticationEntryPoint
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.platform.config.permission.CompositePermissionEvaluator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.util.*

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val authenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val jwtAuthFilter: JwtAuthFilter
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
            .orElse("").toString()
        return RoleHierarchyImpl.fromHierarchy(hierarchy)
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val cfg = CorsConfiguration()
        cfg.allowedOriginPatterns = mutableListOf(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://localhost",
            "https://esa-blueshell.nl"
        )
        cfg.allowedMethods = mutableListOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        cfg.allowedHeaders = mutableListOf("Authorization", "Content-Type")
        cfg.allowCredentials = true

        val src = UrlBasedCorsConfigurationSource()
        src.registerCorsConfiguration("/**", cfg)
        return src
    }


    @Bean
    fun authChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher("/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests {
                it.requestMatchers(
                    HttpMethod.POST,
                    "/auth",
                    "/recovery/**",
                    "/events/*/signups",
                    "/users",
                    "/users/guest"
                ).permitAll()
                    .requestMatchers(HttpMethod.PUT, "/events/*/signups").permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/events/**",
                        "/v3/api-docs**/**",
                        "/swagger-ui**/**",
                        "/download/**",
                        "/committees/**",
                        "/contributionPeriods/current",
                        "/health"
                    ).permitAll()
                    .requestMatchers(HttpMethod.DELETE, "/events/signups/*").permitAll()
                    .anyRequest().authenticated()
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