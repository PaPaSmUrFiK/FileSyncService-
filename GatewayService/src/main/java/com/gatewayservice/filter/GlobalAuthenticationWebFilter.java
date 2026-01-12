package com.gatewayservice.filter;

import com.gatewayservice.client.AuthServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
// ... (rest of imports)

/**
 * Глобальный фильтр аутентификации для WebFlux.
 * Применяется ко всем запросам, включая те, что обрабатываются локальными контроллерами.
 */
@Component
@Order(-10) // Должен выполняться после CorsWebFilter (у которого Order.HIGHEST_PRECEDENCE)
@Slf4j
@RequiredArgsConstructor
public class GlobalAuthenticationWebFilter implements WebFilter {

    private final AuthServiceClient authServiceClient;

    // Список публичных эндпоинтов, не требующих проверки токена
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/validate",
            "/api/v1/storage/minio/", // Pre-signed URL от MinIO уже содержат подпись
            "/fallback"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 1. Пропускаем OPTIONS запросы для CORS (preflight)
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();

        // Пропускаем публичные пути
        if (isPublicPath(path)) {
            log.debug("Skipping authentication for public path: {}", path);
            return chain.filter(exchange);
        }

        // Пропускаем все запросы к MinIO прокси (они уже подписаны)
        if (path.startsWith("/api/v1/storage/minio")) {
            log.debug("Skipping authentication for MinIO proxy path: {}", path);
            return chain.filter(exchange);
        }

        // Пропускаем WebSocket запросы (у них свой фильтр в Gateway)
        if (path.startsWith("/ws/")) {
            return chain.filter(exchange);
        }

        // Проверяем наличие заголовка Authorization
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Если пути начинаются с /api/v1/, но не в списке публичных - требуем авторизацию
            if (path.startsWith("/api/v1/")) {
                log.warn("Missing or invalid Authorization header for path: {}", path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);
        log.debug("Validating token for path: {}, token prefix: {}...", path, token.length() > 10 ? token.substring(0, 10) : "short");

        return authServiceClient.validateToken(token)
                .flatMap(validationResponse -> {
                    if (validationResponse.isValid()) {
                        // Добавляем заголовки пользователя в запрос
                        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                                .header("X-User-Id", validationResponse.getUserId())
                                .header("X-User-Email", validationResponse.getEmail())
                                .header("X-User-Roles", String.join(",", validationResponse.getRoles()))
                                .build();

                        // Сохраняем в атрибуты для использования в контроллерах и других фильтрах
                        exchange.getAttributes().put("userId", validationResponse.getUserId());
                        exchange.getAttributes().put("userEmail", validationResponse.getEmail());
                        exchange.getAttributes().put("roles", validationResponse.getRoles());

                        return chain.filter(exchange.mutate().request(modifiedRequest).build());
                    } else {
                        log.warn("Invalid token for path {}: {}", path, validationResponse.getErrorMessage());
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error during global authentication for path {}: {}", path, e.getMessage());
                    exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                    return exchange.getResponse().setComplete();
                });
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}

