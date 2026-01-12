package com.gatewayservice.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Фильтр для проксирования запросов в MinIO с сохранением оригинального Host заголовка.
 * Это необходимо, так как Pre-signed URL MinIO включает Host в подпись.
 */
@Component("MinioProxy")
@Slf4j
public class MinioProxyGatewayFilterFactory extends AbstractGatewayFilterFactory<MinioProxyGatewayFilterFactory.Config> {

    @Override
    public String name() {
        return "MinioProxy";
    }

    public MinioProxyGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            try {
                ServerHttpRequest request = exchange.getRequest();
                String originalPath = request.getURI().getPath();
                String queryString = request.getURI().getQuery();
                
                log.info("MinIO Proxy Filter: Original path={}, query={}, method={}", 
                        originalPath, queryString, request.getMethod());
                
                // Путь уже должен быть очищен StripPrefix (например, /file-sync-storage/...)
                // Если по какой-то причине он не начинается с /, исправляем
                String modifiedPath = originalPath;
                if (!modifiedPath.startsWith("/")) {
                    modifiedPath = "/" + modifiedPath;
                }
                
                // MinIO требует, чтобы Host в запросе совпадал с Host в подписи (minio:9000)
                ServerHttpRequest modifiedRequest = request.mutate()
                        .header("Host", "minio:9000")
                        .path(modifiedPath)
                        .build();

                log.info("MinIO Proxy Filter: Forwarding to MinIO - Host={}, Path={}, Query={}", 
                        modifiedRequest.getHeaders().getFirst("Host"),
                        modifiedRequest.getURI().getPath(),
                        modifiedRequest.getURI().getQuery());

                return chain.filter(exchange.mutate().request(modifiedRequest).build())
                        .doOnError(error -> {
                            log.error("MinIO Proxy Filter: Error during forwarding: {}", error.getMessage());
                        });
            } catch (Exception e) {
                log.error("MinIO Proxy Filter: Unexpected error: {}", e.getMessage(), e);
                return Mono.error(e);
            }
        };
    }

    public static class Config {
        // Конфигурация может быть расширена при необходимости
    }
}
