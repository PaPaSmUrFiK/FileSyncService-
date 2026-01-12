package com.gatewayservice.filter;

import com.gatewayservice.client.AuthServiceClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.util.List;

/**
 * Фильтр для аутентификации WebSocket соединений.
 * Проверяет JWT токен из query параметра или заголовка Authorization перед установкой WebSocket соединения.
 */
@Component
@Slf4j
public class WebSocketAuthenticationFilterGatewayFilterFactory
        extends AbstractGatewayFilterFactory<WebSocketAuthenticationFilterGatewayFilterFactory.Config> {

    private final AuthServiceClient authServiceClient;

    @Override
    public String name() {
        return "WebSocketAuth";
    }

    public WebSocketAuthenticationFilterGatewayFilterFactory(AuthServiceClient authServiceClient) {
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
            // Для WebSocket соединений токен обычно передается в query параметре
            URI uri = exchange.getRequest().getURI();
            String token = extractTokenFromQuery(uri);
            
            // Если токена нет в query, проверяем заголовок Authorization
            if (token == null) {
                List<String> authHeaders = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
                if (authHeaders != null && !authHeaders.isEmpty()) {
                    String authHeader = authHeaders.get(0);
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        token = authHeader.substring(7);
                    }
                }
            }

            if (token == null) {
                log.warn("Missing authentication token for WebSocket connection: {}", uri.getPath());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            return authServiceClient.validateToken(token)
                    .flatMap(validationResponse -> {
                        if (validationResponse.isValid()) {
                            // Добавляем userId в атрибуты для передачи в downstream сервис
                            exchange.getAttributes().put("userId", validationResponse.getUserId());
                            exchange.getAttributes().put("email", validationResponse.getEmail());
                            exchange.getAttributes().put("roles", validationResponse.getRoles());

                            // Добавляем userId в заголовки для downstream сервиса
                            ServerWebExchange modifiedExchange = exchange.mutate()
                                    .request(exchange.getRequest().mutate()
                                            .header("X-User-Id", validationResponse.getUserId())
                                            .header("X-User-Email", validationResponse.getEmail())
                                            .header("X-User-Roles", String.join(",", validationResponse.getRoles()))
                                            .build())
                                    .build();

                            return chain.filter(modifiedExchange);
                        } else {
                            log.warn("Invalid token for WebSocket connection: {}", validationResponse.getErrorMessage());
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("Error validating token for WebSocket: {}", e.getMessage());
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    });
        };
    }

    private String extractTokenFromQuery(URI uri) {
        if (uri.getQuery() == null) {
            return null;
        }
        String[] params = uri.getQuery().split("&");
        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2 && ("token".equals(keyValue[0]) || "accessToken".equals(keyValue[0]))) {
                return keyValue[1];
            }
        }
        return null;
    }
}

