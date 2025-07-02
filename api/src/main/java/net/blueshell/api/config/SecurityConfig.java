package net.blueshell.api.config;

import net.blueshell.api.auth.JwtAuthFilter;
import net.blueshell.api.auth.JwtAuthenticationEntryPoint;
import net.blueshell.api.base.CompositePermissionEvaluator;
import net.blueshell.api.common.enums.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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

import java.util.Arrays;

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
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        String hierarchyString = (String) Arrays.stream(Role.values())
                .sorted((a, b) -> b.getAuthorities().size() - a.getAuthorities().size())
                .map(Role::getName)
                .reduce((a, b) -> a + " > " + b)
                .orElse("");
        return fromHierarchy(hierarchyString);
    }

    // 1) Chain for /auth/**
    @Bean
    @Order(1)
    public SecurityFilterChain authSecurityChain(HttpSecurity http) throws Exception {
        http
                // only apply this chain to /auth/**
                .securityMatcher("/auth/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // run your JWT filter here
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/auth").permitAll()        // login
                        .requestMatchers(HttpMethod.GET, "/auth/identity").permitAll() // token→identity
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint));
        return http.build();
    }

    // 2) Chain for everything else
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET,
                                "/events/**", "/download/**", "/committees**", "/contributionPeriods", "/health"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/events/signups/*/guest", "/users"
                        ).permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }


    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(CompositePermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
}