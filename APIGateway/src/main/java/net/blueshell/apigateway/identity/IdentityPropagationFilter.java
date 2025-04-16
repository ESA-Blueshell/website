//package net.blueshell.apigateway.identity;
//
//import jakarta.ws.rs.core.HttpHeaders;
//import net.blueshell.common.communicator.ApiCommunicator;
//import net.blueshell.common.identity.Identity;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.cloud.gateway.filter.GatewayFilterChain;
//import org.springframework.cloud.gateway.filter.GlobalFilter;
//import org.springframework.core.Ordered;
//import org.springframework.http.HttpMethod;
//import org.springframework.stereotype.Component;
//import org.springframework.web.server.ServerWebExchange;
//import reactor.core.publisher.Mono;
//
//// In your gateway module
//@Component
//public class IdentityPropagationFilter implements GlobalFilter, Ordered {
//
//    private final ApiCommunicator apiCommunicator;
//
//    @Autowired
//    public IdentityPropagationFilter(ApiCommunicator apiCommunicator) {
//        this.apiCommunicator = apiCommunicator;
//    }
//
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        // 1) Extract Authorization header
//        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
//        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//            // If there's no token, we treat the user as anonymous
//            return chain.filter(exchange);
//        }
//
//        Identity identity = apiCommunicator.sendSync("identity", HttpMethod.GET, Identity.class);
//
//        // 2) Call identity service to validate token or get user info
//        return webClient.get()
//                .uri("/auth/validate?token={token}", authHeader.substring(7))
//                .retrieve()
//                .bodyToMono(UserInfo.class)
//                .flatMap(userInfo -> {
//                    // 3) Attach user info as headers to forward to downstream
//                    ServerHttpRequest mutatedRequest = exchange.getRequest()
//                            .mutate()
//                            // we can add a custom header (or multiple)
//                            .header("X-User-Id", String.valueOf(userInfo.getUserId()))
//                            .header("X-User-Name", userInfo.getUsername())
//                            .header("X-User-Roles", String.join(",", userInfo.getRoles()))
//                            .build();
//
//                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
//                });
//    }
//
//    @Override
//    public int getOrder() {
//        // Priority for your filter (lower means higher priority)
//        return 0;
//    }
//}
