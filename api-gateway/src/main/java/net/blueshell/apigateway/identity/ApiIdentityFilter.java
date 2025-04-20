package net.blueshell.apigateway.identity;

import jakarta.ws.rs.core.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.common.enums.Role;
import net.blueshell.common.identity.Identity;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ApiIdentityFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;

    public ApiIdentityFilter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("lb://API/auth/identity")
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        // no bearer ⇒ just forward
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        return webClient.get()
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .bodyToMono(Identity.class)
                // if we get a user, mutate the headers
                .flatMap(identity -> {
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .header("X-User-Id", String.valueOf(identity.getId()))
                            .header("X-User-Name", identity.getUsername())
                            .header("X-User-Roles",
                                    String.join(",",
                                            identity.getRoles()
                                                    .stream()
                                                    .map(Role::toString)
                                                    .toList()))
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                });
    }

    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * Simple marker exception to indicate Permission Denied from user‐details service.
     */
    private static class PermissionDeniedException extends RuntimeException {
        public PermissionDeniedException(String msg) {
            super(msg);
        }
    }
}
