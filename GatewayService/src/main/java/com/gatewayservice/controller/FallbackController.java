package com.gatewayservice.controller;

import com.gatewayservice.model.ErrorResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/auth")
    public Mono<ErrorResponse> authFallback() {
        return Mono.just(ErrorResponse.builder()
                .error("service_unavailable")
                .message("Authentication service is temporarily unavailable. Please try again later.")
                .service("auth-service")
                .build());
    }

    @GetMapping("/file")
    public Mono<ErrorResponse> fileFallback() {
        return Mono.just(ErrorResponse.builder()
                .error("service_unavailable")
                .message("File service is temporarily unavailable. Please try again later.")
                .service("file-service")
                .build());
    }

    @GetMapping("/sync")
    public Mono<ErrorResponse> syncFallback() {
        return Mono.just(ErrorResponse.builder()
                .error("service_unavailable")
                .message("Sync service is temporarily unavailable. Please try again later.")
                .service("sync-service")
                .build());
    }

    @GetMapping("/storage")
    public Mono<ErrorResponse> storageFallback() {
        return Mono.just(ErrorResponse.builder()
                .error("service_unavailable")
                .message("Storage service is temporarily unavailable. Please try again later.")
                .service("storage-service")
                .build());
    }

    @GetMapping("/notification")
    public Mono<ErrorResponse> notificationFallback() {
        return Mono.just(ErrorResponse.builder()
                .error("service_unavailable")
                .message("Notification service is temporarily unavailable. Please try again later.")
                .service("notification-service")
                .build());
    }
}
