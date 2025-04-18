package net.blueshell.apigateway.identity;

import jakarta.ws.rs.core.HttpHeaders;
import net.blueshell.common.enums.Role;
import net.blueshell.common.identity.SharedUserDetails;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

@Component
public class UserDetailsFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;

    public UserDetailsFilter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("http://API/api/user-details")
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

        String token = authHeader.substring(7);

        return webClient.get()
                .uri(uri -> uri.queryParam("token", token).build())
                .retrieve()
                // if 403 ⇒ error path
                .onStatus(HttpStatus.FORBIDDEN::equals,
                        resp -> Mono.error(new PermissionDeniedException("User details forbidden")))
                .bodyToMono(SharedUserDetails.class)
                // if we get a user, mutate the headers
                .flatMap(userDetails -> {
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .header("X-User-Id", String.valueOf(userDetails.getId()))
                            .header("X-User-Name", userDetails.getUsername())
                            .header("X-User-Roles",
                                    String.join(",",
                                            userDetails.getRoles()
                                                    .stream()
                                                    .map(Role::toString)
                                                    .toList()))
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                .onErrorResume(PermissionDeniedException.class, e -> {
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                })
                .onErrorResume(e -> chain.filter(exchange));
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
