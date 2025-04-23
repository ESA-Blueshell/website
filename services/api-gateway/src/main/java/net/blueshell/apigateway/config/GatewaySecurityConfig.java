package net.blueshell.apigateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Value("${frontend.url}")
    private String frontendUrl;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(corsSpec -> corsSpec.configurationSource(request -> {
                    var config = new org.springframework.web.cors.CorsConfiguration();
                    config.setAllowedOriginPatterns(List.of(frontendUrl));
                    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Auth-Token", "X-User-Id", "X-User-Roles", "X-User-Name"));
                    config.setExposedHeaders(List.of("X-Auth-Token"));
                    config.setAllowCredentials(true);
                    return config;
                }))
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().permitAll()
                );

        return http.build();
    }
}
