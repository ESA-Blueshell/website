package net.blueshell.apigateway.identity;

import net.blueshell.common.enums.Role;
import net.blueshell.common.identity.Identity;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiIdentityFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;

    public ApiIdentityFilter(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("lb://API/api/auth/identity")
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null) {
            return chain.filter(exchange);
        }

        return webClient.get()
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .onStatus(HttpStatus.FORBIDDEN::equals,
                        resp -> Mono.error(new PermissionDeniedException("Token expired")))
                .bodyToMono(Identity.class)
                .flatMap(user -> {
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .header("X-User-Id",   String.valueOf(user.getId()))
                            .header("X-User-Name", user.getUsername())
                            .header("X-User-Roles",
                                    String.join(",",
                                            user.getRoles().stream()
                                                    .map(Role::toString)
                                                    .toList()))
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                .onErrorResume(PermissionDeniedException.class, e -> {
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                })
                .onErrorResume(e -> chain.filter(exchange)); // fail‑open for any other error
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static class PermissionDeniedException extends RuntimeException {
        public PermissionDeniedException(String msg) {
            super(msg);
        }
    }
}
