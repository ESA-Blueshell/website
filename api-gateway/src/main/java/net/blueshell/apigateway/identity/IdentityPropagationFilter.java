package net.blueshell.apigateway.identity;

import jakarta.ws.rs.core.HttpHeaders;
import net.blueshell.common.enums.Role;
import net.blueshell.common.identity.Identity;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class IdentityPropagationFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;

    public IdentityPropagationFilter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("http://API/api/identity") // Eureka service ID
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder.queryParam("token", token).build())
                .retrieve()
                .bodyToMono(Identity.class)
                .onErrorResume(e -> Mono.empty())
                .flatMap(identity -> {
                    if (identity == null) {
                        return chain.filter(exchange);
                    }

                    ServerHttpRequest mutatedRequest = exchange
                            .getRequest()
                            .mutate()
                            .header("X-User-Id", String.valueOf(identity.getUserId()))
                            .header("X-User-Name", identity.getUsername())
                            .header("X-User-Roles", String.join(",", identity.getRoles().stream().map(Role::toString).toList()))
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
