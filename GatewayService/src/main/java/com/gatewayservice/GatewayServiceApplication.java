package com.gatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Главный класс Gateway Service.
 * 
 * ConsulCatalogWatch отключен через настройки в application.yml
 * (catalog-services-watch.enabled: false), чтобы предотвратить
 * блокирующие вызовы в RouteRefreshListener при обновлении маршрутов.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }

}
