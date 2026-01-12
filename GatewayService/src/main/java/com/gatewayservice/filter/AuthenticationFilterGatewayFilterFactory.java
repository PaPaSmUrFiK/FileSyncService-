package com.gatewayservice.filter;

import com.gatewayservice.client.AuthServiceClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class AuthenticationFilterGatewayFilterFactory
        extends AbstractGatewayFilterFactory<AuthenticationFilterGatewayFilterFactory.Config> {

    private final AuthServiceClient authServiceClient;

    public AuthenticationFilterGatewayFilterFactory(AuthServiceClient authServiceClient) {
        super(Config.class);
        this.authServiceClient = authServiceClient;
    }

    @Data
    public static class Config {
        // configuration properties if any
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "Missing authorization header", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION).get(0);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Invalid authorization header", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            return authServiceClient.validateToken(token)
                    .flatMap(validationResponse -> {
                        if (validationResponse.isValid()) {
                            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                                    .header("X-User-Id", validationResponse.getUserId())
                                    .header("X-User-Email", validationResponse.getEmail())
                                    .header("X-User-Roles", String.join(",", validationResponse.getRoles()))
                                    .build();

                            exchange.getAttributes().put("userId", validationResponse.getUserId());
                            exchange.getAttributes().put("roles", validationResponse.getRoles());

                            return chain.filter(exchange.mutate().request(modifiedRequest).build());
                        } else {
                            return onError(exchange, "Unauthorized: " + validationResponse.getErrorMessage(),
                                    HttpStatus.UNAUTHORIZED);
                        }
                    });
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        exchange.getResponse().setStatusCode(httpStatus);
        log.warn("Authentication error: {}", err);
        return exchange.getResponse().setComplete();
    }
}
