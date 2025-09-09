package net.blueshell.api.config;

import net.blueshell.api.auth.JwtAuthFilter;
import net.blueshell.api.auth.JwtAuthenticationEntryPoint;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.permission.CompositePermissionEvaluator;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import static org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl.fromHierarchy;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationEntryPoint authenticationEntryPoint, JwtAuthFilter jwtAuthFilter) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.jwtAuthFilter = jwtAuthFilter;
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter f) {
        return new FilterRegistrationBean<>(f);
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        String hierarchy = Arrays.stream(Role.values())
                .sorted((a, b) -> b.getAuthorities().size() - a.getAuthorities().size())
                .map(Role::getName)
                .reduce((a, b) -> a + " > " + b)
                .orElse("").toString();
        return fromHierarchy(hierarchy);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("https://localhost"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }


    @Bean
    public SecurityFilterChain authChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/auth").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/identity").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/events/**",
                                "/v3/api-docs**/**",
                                "/swagger-ui**/**",
                                "/download/**",
                                "/committees/**",
                                "/contributionPeriods",
                                "/health").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/events/signups/*/guest",
                                "/users",
                                "/users/guest").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint));
        return http.build();
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            CompositePermissionEvaluator evaluator) {
        DefaultMethodSecurityExpressionHandler h = new DefaultMethodSecurityExpressionHandler();
        h.setPermissionEvaluator(evaluator);
        return h;
    }
}