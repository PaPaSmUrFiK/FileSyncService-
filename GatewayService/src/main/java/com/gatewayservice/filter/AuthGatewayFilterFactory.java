package com.gatewayservice.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewayservice.client.AuthServiceClient;
import com.gatewayservice.model.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class AuthGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthGatewayFilterFactory.Config> {

    private final AuthServiceClient authServiceClient;
    private final ObjectMapper objectMapper;

    public AuthGatewayFilterFactory(AuthServiceClient authServiceClient) {
        super(Config.class);
        this.authServiceClient = authServiceClient;
        this.objectMapper = new ObjectMapper();
    }

    @Data
    public static class Config {
        // Configuration properties if needed
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();
            String method = request.getMethod().name();

            // Only handle POST requests to auth endpoints
            if (!method.equals("POST") || !path.startsWith("/api/v1/auth/")) {
                return chain.filter(exchange);
            }

            return DataBufferUtils.join(request.getBody())
                    .flatMap(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        String body = new String(bytes, StandardCharsets.UTF_8);

                        try {
                            if (path.contains("/login")) {
                                LoginRequest loginRequest = objectMapper.readValue(body, LoginRequest.class);
                                return handleLogin(exchange, loginRequest);
                            } else if (path.contains("/register")) {
                                RegisterRequest registerRequest = objectMapper.readValue(body, RegisterRequest.class);
                                return handleRegister(exchange, registerRequest);
                            } else if (path.contains("/refresh")) {
                                RefreshTokenRequest refreshRequest = objectMapper.readValue(body, RefreshTokenRequest.class);
                                return handleRefresh(exchange, refreshRequest);
                            } else if (path.contains("/logout") && !path.contains("/logout-all")) {
                                LogoutRequest logoutRequest = objectMapper.readValue(body, LogoutRequest.class);
                                return handleLogout(exchange, logoutRequest);
                            } else if (path.contains("/logout-all")) {
                                // Для logout-all нужен Authorization header для получения userId
                                String authHeader = request.getHeaders().getFirst("Authorization");
                                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                                    return onError(exchange, "Authorization header required", HttpStatus.UNAUTHORIZED);
                                }
                                String token = authHeader.substring(7);
                                // Валидируем токен для получения userId
                                return authServiceClient.validateToken(token)
                                        .flatMap(validationResponse -> {
                                            if (validationResponse.isValid()) {
                                                return handleLogoutAll(exchange, validationResponse.getUserId());
                                            } else {
                                                return onError(exchange, "Invalid token", HttpStatus.UNAUTHORIZED);
                                            }
                                        });
                            }
                        } catch (Exception e) {
                            log.error("Error parsing request body", e);
                            return onError(exchange, "Invalid request", HttpStatus.BAD_REQUEST);
                        }
                        return chain.filter(exchange);
                    })
                    .onErrorResume(e -> {
                        log.error("Error processing auth request", e);
                        return onError(exchange, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
                    });
        };
    }

    private Mono<Void> handleLogin(ServerWebExchange exchange, LoginRequest request) {
        log.info("Login request for email: {}", request.getEmail());
        return authServiceClient.login(request)
                .flatMap(tokenResponse -> writeResponse(exchange, tokenResponse, HttpStatus.OK))
                .onErrorResume(e -> {
                    log.error("Login error: {}", e.getMessage());
                    return onError(exchange, "Login failed", HttpStatus.UNAUTHORIZED);
                });
    }

    private Mono<Void> handleRegister(ServerWebExchange exchange, RegisterRequest request) {
        log.info("Register request for email: {}", request.getEmail());
        return authServiceClient.register(request)
                .flatMap(tokenResponse -> writeResponse(exchange, tokenResponse, HttpStatus.OK))
                .onErrorResume(e -> {
                    log.error("Register error: {}", e.getMessage());
                    HttpStatus status = e.getMessage() != null && e.getMessage().contains("already registered")
                            ? HttpStatus.CONFLICT
                            : HttpStatus.BAD_REQUEST;
                    return onError(exchange, "Registration failed", status);
                });
    }

    private Mono<Void> handleRefresh(ServerWebExchange exchange, RefreshTokenRequest request) {
        log.info("Refresh token request");
        return authServiceClient.refreshToken(request)
                .flatMap(tokenResponse -> writeResponse(exchange, tokenResponse, HttpStatus.OK))
                .onErrorResume(e -> {
                    log.error("Refresh token error: {}", e.getMessage());
                    return onError(exchange, "Token refresh failed", HttpStatus.UNAUTHORIZED);
                });
    }

    private Mono<Void> handleLogout(ServerWebExchange exchange, LogoutRequest request) {
        log.info("Logout request");
        return authServiceClient.logout(request)
                .then(writeEmptyResponse(exchange, HttpStatus.OK))
                .onErrorResume(e -> {
                    log.error("Logout error: {}", e.getMessage());
                    return onError(exchange, "Logout failed", HttpStatus.INTERNAL_SERVER_ERROR);
                });
    }

    private Mono<Void> handleLogoutAll(ServerWebExchange exchange, String userId) {
        log.info("Logout all request for user: {}", userId);
        return authServiceClient.logoutAll(userId)
                .then(writeEmptyResponse(exchange, HttpStatus.OK))
                .onErrorResume(e -> {
                    log.error("Logout all error: {}", e.getMessage());
                    return onError(exchange, "Logout all failed", HttpStatus.INTERNAL_SERVER_ERROR);
                });
    }

    private Mono<Void> writeResponse(ServerWebExchange exchange, TokenResponse response, HttpStatus status) {
        ServerHttpResponse httpResponse = exchange.getResponse();
        httpResponse.setStatusCode(status);
        httpResponse.getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        try {
            String json = objectMapper.writeValueAsString(response);
            DataBuffer buffer = httpResponse.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return httpResponse.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Error writing response", e);
            return onError(exchange, "Internal error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Mono<Void> writeEmptyResponse(ServerWebExchange exchange, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        return response.setComplete();
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        
        try {
            String json = objectMapper.writeValueAsString(
                    java.util.Map.of("error", message, "status", status.value())
            );
            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Error writing error response", e);
            return response.setComplete();
        }
    }
}

