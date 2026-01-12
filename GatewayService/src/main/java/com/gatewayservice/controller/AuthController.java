package com.gatewayservice.controller;

import com.gatewayservice.client.AuthServiceClient;
import com.gatewayservice.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000", "https://filesync.com", "https://app.filesync.com"})
public class AuthController {

    private final AuthServiceClient authServiceClient;

    @PostMapping("/login")
    public Mono<ResponseEntity<Object>> login(@RequestBody LoginRequest request) {
        log.info("Login request for email: {}", request.getEmail());
        return authServiceClient.login(request)
                .map(response -> ResponseEntity.ok((Object) response))
                .onErrorResume(e -> {
                    log.error("Login error: {}", e.getMessage());
                    String message = e.getMessage();
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.UNAUTHORIZED)
                            .body(new ErrorResponse(message != null ? message : "Invalid credentials")));
                });
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<Object>> register(@RequestBody RegisterRequest request) {
        log.info("Register request for email: {}", request.getEmail());
        return authServiceClient.register(request)
                .map(response -> ResponseEntity.ok((Object) response))
                .onErrorResume(e -> {
                    log.error("Register error: {}", e.getMessage());
                    HttpStatus status = e.getMessage().contains("already registered") 
                            ? HttpStatus.CONFLICT 
                            : HttpStatus.BAD_REQUEST;
                    return Mono.just(ResponseEntity
                            .status(status)
                            .body(new ErrorResponse(e.getMessage())));
                });
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<TokenResponse>> refresh(@RequestBody RefreshTokenRequest request) {
        log.info("Refresh token request");
        return authServiceClient.refreshToken(request)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Refresh token error: {}", e.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.UNAUTHORIZED)
                            .build());
                });
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(@RequestBody LogoutRequest request) {
        log.info("Logout request");
        return authServiceClient.logout(request)
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .onErrorResume(e -> {
                    log.error("Logout error: {}", e.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build());
                });
    }

    @PostMapping("/logout-all")
    public Mono<ResponseEntity<Void>> logoutAll(@RequestHeader("X-User-Id") String userId) {
        log.info("Logout all request for user: {}", userId);
        return authServiceClient.logoutAll(userId)
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .onErrorResume(e -> {
                    log.error("Logout all error: {}", e.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build());
                });
    }
}

