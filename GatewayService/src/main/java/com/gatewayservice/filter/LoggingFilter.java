package com.gatewayservice.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant start = Instant.now();
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        String requestId = exchange.getAttribute("requestId");
        String userId = exchange.getAttribute("userId");
        String clientIp = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        log.info("[REQUEST] {} {} {} user_id={} ip={} request_id={}",
                start, method, path, userId != null ? userId : "anonymous", clientIp, requestId);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            Instant end = Instant.now();
            long duration = end.toEpochMilli() - start.toEpochMilli();
            log.info("[RESPONSE] {} {} {} status={} duration={}ms request_id={}",
                    end, method, path, exchange.getResponse().getStatusCode(), duration, requestId);
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
