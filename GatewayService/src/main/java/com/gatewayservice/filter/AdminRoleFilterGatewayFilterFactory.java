package com.gatewayservice.filter;

import lombok.Data;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminRoleFilterGatewayFilterFactory
        extends AbstractGatewayFilterFactory<AdminRoleFilterGatewayFilterFactory.Config> {

    public AdminRoleFilterGatewayFilterFactory() {
        super(Config.class);
    }

    @Data
    public static class Config {
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            List<String> roles = exchange.getAttribute("roles");
            if (roles == null || !roles.contains("ADMIN")) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }
}
